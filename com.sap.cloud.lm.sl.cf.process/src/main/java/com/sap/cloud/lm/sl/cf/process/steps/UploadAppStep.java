package com.sap.cloud.lm.sl.cf.process.steps;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.io.FileUtils;
import org.cloudfoundry.client.lib.CloudControllerClient;
import org.cloudfoundry.client.lib.CloudOperationException;
import org.cloudfoundry.client.lib.domain.CloudApplication;
import org.cloudfoundry.client.lib.domain.Status;
import org.cloudfoundry.client.lib.domain.UploadToken;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;

import com.sap.cloud.lm.sl.cf.client.lib.domain.CloudApplicationExtended;
import com.sap.cloud.lm.sl.cf.client.lib.domain.UploadStatusCallbackExtended;
import com.sap.cloud.lm.sl.cf.core.helpers.ApplicationAttributes;
import com.sap.cloud.lm.sl.cf.core.helpers.ApplicationEnvironmentUpdater;
import com.sap.cloud.lm.sl.cf.core.helpers.MtaArchiveElements;
import com.sap.cloud.lm.sl.cf.core.model.SupportedParameters;
import com.sap.cloud.lm.sl.cf.persistence.services.FileContentProcessor;
import com.sap.cloud.lm.sl.cf.persistence.services.FileStorageException;
import com.sap.cloud.lm.sl.cf.process.Constants;
import com.sap.cloud.lm.sl.cf.process.Messages;
import com.sap.cloud.lm.sl.cf.process.util.ApplicationArchiveContext;
import com.sap.cloud.lm.sl.cf.process.util.ApplicationArchiveReader;
import com.sap.cloud.lm.sl.cf.process.util.ApplicationDigestDetector;
import com.sap.cloud.lm.sl.cf.process.util.ApplicationStager;
import com.sap.cloud.lm.sl.cf.process.util.ApplicationZipBuilder;
import com.sap.cloud.lm.sl.common.SLException;

@Named("uploadAppStep")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class UploadAppStep extends TimeoutAsyncFlowableStep {

    static final int DEFAULT_APP_UPLOAD_TIMEOUT = (int) TimeUnit.HOURS.toSeconds(1);

    @Inject
    protected ApplicationArchiveReader applicationArchiveReader;
    @Inject
    protected ApplicationZipBuilder applicationZipBuilder;

    @Override
    public StepPhase executeAsyncStep(ExecutionWrapper execution) throws FileStorageException {
        CloudApplicationExtended app = StepsUtil.getApp(execution.getContext());
        String appName = app.getName();

        getStepLogger().info(Messages.UPLOADING_APP, appName);
        CloudControllerClient client = execution.getControllerClient();

        String appArchiveId = StepsUtil.getRequiredString(execution.getContext(), Constants.PARAM_APP_ARCHIVE_ID);
        MtaArchiveElements mtaArchiveElements = StepsUtil.getMtaArchiveElements(execution.getContext());
        String fileName = mtaArchiveElements.getModuleFileName(app.getModuleName());

        if (fileName == null) {
            getStepLogger().debug(Messages.NO_CONTENT_TO_UPLOAD);
            return StepPhase.DONE;
        }

        String newApplicationDigest = getNewApplicationDigest(execution, appArchiveId, fileName);
        CloudApplication cloudApp = client.getApplication(appName);
        boolean contentChanged = detectApplicationFileDigestChanges(execution, cloudApp, client, newApplicationDigest);
        if (!contentChanged && isAppStagedCorrectly(client, cloudApp)) {
            getStepLogger().info(Messages.CONTENT_OF_APPLICATION_0_IS_NOT_CHANGED, appName);
            return StepPhase.DONE;
        }

        getStepLogger().debug(Messages.UPLOADING_FILE_0_FOR_APP_1, fileName, appName);
        UploadToken uploadToken = asyncUploadFiles(execution, client, app, appArchiveId, fileName);

        getStepLogger().debug(Messages.STARTED_ASYNC_UPLOAD_OF_APP_0, appName);
        StepsUtil.setUploadToken(uploadToken, execution.getContext());
        return StepPhase.POLL;
    }

    private boolean isAppStagedCorrectly(CloudControllerClient client, CloudApplication cloudApp) {
        ApplicationStager appStager = new ApplicationStager(client);
        return appStager.isApplicationStagedCorrectly(getStepLogger(), cloudApp);
    }

    @Override
    protected String getStepErrorMessage(DelegateExecution context) {
        return MessageFormat.format(Messages.ERROR_UPLOADING_APP_0, StepsUtil.getApp(context)
                                                                             .getName());
    }

    private String getNewApplicationDigest(ExecutionWrapper execution, String appArchiveId, String fileName) throws FileStorageException {
        DelegateExecution context = execution.getContext();
        StringBuilder digestStringBuilder = new StringBuilder();
        fileService.processFileContent(StepsUtil.getSpaceId(context), appArchiveId,
                                       createDigestCalculatorFileContentProcessor(digestStringBuilder, fileName));
        return digestStringBuilder.toString();
    }

    private FileContentProcessor createDigestCalculatorFileContentProcessor(StringBuilder digestStringBuilder, String fileName) {
        return appArchiveStream -> {
            long maxSize = configuration.getMaxResourceFileSize();
            ApplicationArchiveContext applicationArchiveContext = createApplicationArchiveContext(appArchiveStream, fileName, maxSize);
            digestStringBuilder.append(applicationArchiveReader.calculateApplicationDigest(applicationArchiveContext));
        };
    }

    protected ApplicationArchiveContext createApplicationArchiveContext(InputStream appArchiveStream, String fileName, long maxSize) {
        return new ApplicationArchiveContext(appArchiveStream, fileName, maxSize);
    }

    private UploadToken asyncUploadFiles(ExecutionWrapper execution, CloudControllerClient client, CloudApplication app,
                                         String appArchiveId, String fileName)
        throws FileStorageException {
        AtomicReference<UploadToken> uploadTokenReference = new AtomicReference<>();
        DelegateExecution context = execution.getContext();

        fileService.processFileContent(StepsUtil.getSpaceId(context), appArchiveId, appArchiveStream -> {
            Path filePath = null;
            long maxSize = configuration.getMaxResourceFileSize();
            try {
                ApplicationArchiveContext applicationArchiveContext = createApplicationArchiveContext(appArchiveStream, fileName, maxSize);
                filePath = extractFromMtar(applicationArchiveContext);
                UploadToken uploadToken = upload(execution, client, app, filePath);
                uploadTokenReference.set(uploadToken);
            } catch (IOException e) {
                cleanUp(filePath);
                throw new SLException(e, Messages.ERROR_RETRIEVING_MTA_MODULE_CONTENT, fileName);
            } catch (CloudOperationException e) {
                cleanUp(filePath);
                throw e;
            }
        });
        return uploadTokenReference.get();
    }

    protected Path extractFromMtar(ApplicationArchiveContext applicationArchiveContext) {
        return applicationZipBuilder.extractApplicationInNewArchive(applicationArchiveContext, getStepLogger());
    }

    private UploadToken upload(ExecutionWrapper execution, CloudControllerClient client, CloudApplication app, Path filePath)
        throws IOException {
        return client.asyncUploadApplication(app.getName(), filePath.toFile(),
                                             getMonitorUploadStatusCallback(app, filePath.toFile(), execution.getContext()));
    }

    private boolean detectApplicationFileDigestChanges(ExecutionWrapper execution, CloudApplication appWithUpdatedEnvironment,
                                                       CloudControllerClient client, String newApplicationDigest) {
        ApplicationDigestDetector digestDetector = new ApplicationDigestDetector(appWithUpdatedEnvironment);
        String currentApplicationDigest = digestDetector.getExistingApplicationDigest();
        boolean contentChanged = digestDetector.hasApplicationContentDigestChanged(newApplicationDigest, currentApplicationDigest);
        if (contentChanged) {
            attemptToUpdateApplicationDigest(client, appWithUpdatedEnvironment, newApplicationDigest);
        }
        setAppContentChanged(execution, contentChanged);
        return contentChanged;
    }

    private void attemptToUpdateApplicationDigest(CloudControllerClient client, CloudApplication app, String newApplicationDigest) {
        new ApplicationEnvironmentUpdater(app,
                                          client).updateApplicationEnvironment(com.sap.cloud.lm.sl.cf.core.Constants.ENV_DEPLOY_ATTRIBUTES,
                                                                               com.sap.cloud.lm.sl.cf.core.Constants.ATTR_APP_CONTENT_DIGEST,
                                                                               newApplicationDigest);
    }

    private void setAppContentChanged(ExecutionWrapper execution, boolean appContentChanged) {
        execution.getContext()
                 .setVariable(Constants.VAR_APP_CONTENT_CHANGED, Boolean.toString(appContentChanged));
    }

    MonitorUploadStatusCallback getMonitorUploadStatusCallback(CloudApplication app, File file, DelegateExecution context) {
        return new MonitorUploadStatusCallback(app, file, context);
    }

    private void cleanUp(Path filePath) {
        if (filePath == null) {
            return;
        }
        File file = filePath.toFile();
        if (!file.exists()) {
            return;
        }

        try {
            getStepLogger().debug(Messages.DELETING_TEMP_FILE, filePath);
            FileUtils.forceDelete(file);
        } catch (IOException e) {
            getStepLogger().warn(Messages.ERROR_DELETING_APP_TEMP_FILE, filePath.toAbsolutePath());
        }
    }

    @Override
    protected List<AsyncExecution> getAsyncStepExecutions(ExecutionWrapper execution) {
        return Collections.singletonList(new PollUploadAppStatusExecution());
    }

    @Override
    public Integer getTimeout(DelegateExecution context) {
        CloudApplication app = StepsUtil.getApp(context);
        int uploadTimeout = extractUploadTimeoutFromAppAttributes(app, DEFAULT_APP_UPLOAD_TIMEOUT);
        getStepLogger().debug(Messages.UPLOAD_APP_TIMEOUT, uploadTimeout);
        return uploadTimeout;
    }

    private int extractUploadTimeoutFromAppAttributes(CloudApplication app, int defaultAppUploadTimeout) {
        ApplicationAttributes appAttributes = ApplicationAttributes.fromApplication(app);
        Number uploadTimeout = appAttributes.get(SupportedParameters.UPLOAD_TIMEOUT, Number.class, defaultAppUploadTimeout);
        return uploadTimeout.intValue();
    }

    class MonitorUploadStatusCallback implements UploadStatusCallbackExtended {

        private final CloudApplication app;
        private final File file;
        private final DelegateExecution context;

        public MonitorUploadStatusCallback(CloudApplication app, File file, DelegateExecution context) {
            this.app = app;
            this.file = file;
            this.context = context;
        }

        @Override
        public void onCheckResources() {
            getStepLogger().debug("Resources checked");
        }

        @Override
        public void onMatchedFileNames(Set<String> matchedFileNames) {
            getStepLogger().debug(Messages.MATCHED_FILES_COUNT_0, matchedFileNames.size());
        }

        @Override
        public void onProcessMatchedResources(int length) {
            getStepLogger().debug(Messages.MATCHED_RESOURCES_PROCESSED_TOTAL_SIZE_0, length);
        }

        @Override
        public boolean onProgress(String status) {
            getStepLogger().debug(Messages.UPLOAD_STATUS_0, status);
            if (status.equals(Status.READY.toString())) {
                cleanUp(file.toPath());
                getProcessLogsPersister().persistLogs(StepsUtil.getCorrelationId(context), StepsUtil.getTaskId(context));
            }
            return false;
        }

        @Override
        public void onError(Exception e) {
            getStepLogger().error(e, Messages.ERROR_UPLOADING_APP_0, app.getName());
            cleanUp(file.toPath());
        }

        @Override
        public void onError(String description) {
            getStepLogger().error(Messages.ERROR_UPLOADING_APP_0_STATUS_1_DESCRIPTION_2, app.getName(), Status.FAILED, description);
            cleanUp(file.toPath());
        }

    }

}