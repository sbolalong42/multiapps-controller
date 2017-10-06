package com.sap.cloud.lm.sl.cf.process.steps;

import javax.inject.Inject;

import org.activiti.engine.delegate.DelegateExecution;
import org.cloudfoundry.client.lib.CloudFoundryOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sap.activiti.common.ExecutionStatus;
import com.sap.cloud.lm.sl.cf.client.ClientExtensions;
import com.sap.cloud.lm.sl.cf.core.cf.CloudFoundryClientProvider;
import com.sap.cloud.lm.sl.cf.process.exception.MonitoringException;
import com.sap.cloud.lm.sl.cf.process.message.Messages;
import com.sap.cloud.lm.sl.cf.process.util.StepLogger;
import com.sap.cloud.lm.sl.common.SLException;
import com.sap.cloud.lm.sl.slp.services.TaskExtensionService;
import com.sap.cloud.lm.sl.slp.steps.AbstractSLProcessStepWithBridge;
import com.sap.cloud.lm.sl.slp.steps.SLProcessStepHelper;

public abstract class AbstractXS2ProcessStepWithBridge extends AbstractSLProcessStepWithBridge {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    protected CloudFoundryClientProvider clientProvider;
    @Inject
    protected TaskExtensionService taskExtensionService;
    @Inject
    private StepLogger.Factory stepLoggerFactory;

    private StepLogger stepLogger;

    @Override
    protected ExecutionStatus pollStatus(DelegateExecution context) throws Exception {
        try {
            this.stepLogger = createStepLogger(context);
            return pollStatusInternal(context);
        } catch (MonitoringException e) {
            getStepLogger().errorWithoutProgressMessage(e.getMessage());
            throw e;
        }
    }

    protected abstract ExecutionStatus pollStatusInternal(DelegateExecution context) throws Exception;

    protected StepLogger getStepLogger() {
        if (stepLogger == null) {
            throw new IllegalStateException(Messages.STEP_LOGGER_NOT_INITIALIZED);
        }
        return stepLogger;
    }

    private StepLogger createStepLogger(DelegateExecution context) {
        return stepLoggerFactory.create(context, progressMessageService, processLoggerProviderFactory, logger);
    }

    protected CloudFoundryOperations getCloudFoundryClient(DelegateExecution context) throws SLException {
        return StepsUtil.getCloudFoundryClient(context, clientProvider, getStepLogger());
    }

    protected ClientExtensions getClientExtensions(DelegateExecution context) throws SLException {
        return StepsUtil.getClientExtensions(context, clientProvider, getStepLogger());
    }

    @Override
    protected SLProcessStepHelper getStepHelper() {
        if (stepHelper == null) {
            stepHelper = new XS2ProcessStepHelper(getProgressMessageService(), getProcessLoggerProvider(), this);
        }
        return stepHelper;
    }

}
