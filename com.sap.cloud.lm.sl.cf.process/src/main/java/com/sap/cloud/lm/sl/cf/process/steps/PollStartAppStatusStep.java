package com.sap.cloud.lm.sl.cf.process.steps;

import static java.text.MessageFormat.format;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.activiti.engine.delegate.DelegateExecution;
import org.cloudfoundry.client.lib.CloudFoundryException;
import org.cloudfoundry.client.lib.CloudFoundryOperations;
import org.cloudfoundry.client.lib.domain.CloudApplication;
import org.cloudfoundry.client.lib.domain.InstanceInfo;
import org.cloudfoundry.client.lib.domain.InstanceState;
import org.cloudfoundry.client.lib.domain.InstancesInfo;

import com.sap.activiti.common.ExecutionStatus;
import com.sap.activiti.common.util.ContextUtil;
import com.sap.cloud.lm.sl.cf.core.cf.clients.RecentLogsRetriever;
import com.sap.cloud.lm.sl.cf.core.util.Configuration;
import com.sap.cloud.lm.sl.cf.process.Constants;
import com.sap.cloud.lm.sl.cf.process.message.Messages;
import com.sap.cloud.lm.sl.common.SLException;
import com.sap.cloud.lm.sl.common.util.CommonUtil;

public class PollStartAppStatusStep extends AsyncStepOperation {

    enum StartupStatus {
        STARTING, STARTED, CRASHED, FLAPPING
    }

    protected RecentLogsRetriever recentLogsRetriever;
    protected Configuration configuration;

    public PollStartAppStatusStep(RecentLogsRetriever recentLogsRetriever, Configuration configuration) {
        this.recentLogsRetriever = recentLogsRetriever;
        this.configuration = configuration;
    }

    @Override
    public ExecutionStatus executeOperation(ExecutionWrapper execution) throws SLException {
        execution.getStepLogger().logActivitiTask();

        CloudApplication app = getAppToPoll(execution.getContext());
        CloudFoundryOperations client = execution.getCloudFoundryClient();

        try {
            execution.getStepLogger().debug(Messages.CHECKING_APP_STATUS, app.getName());

            StartupStatus status = getStartupStatus(execution, client, app.getName());
            return checkStartupStatus(execution, client, app, status);
        } catch (CloudFoundryException cfe) {
            SLException e = StepsUtil.createException(cfe);
            onError(execution, format(Messages.ERROR_STARTING_APP_1, app.getName()), e);
            StepsUtil.setStepPhase(execution, StepPhase.POLL);
            throw e;
        } catch (SLException e) {
            onError(execution, format(Messages.ERROR_STARTING_APP_1, app.getName()), e);
            StepsUtil.setStepPhase(execution, StepPhase.POLL);
            throw e;
        }
    }

    protected void onError(ExecutionWrapper execution, String message, Exception e) {
        execution.getStepLogger().error(e, message);
    }

    protected void onError(ExecutionWrapper execution, String message) {
        execution.getStepLogger().error(message);
    }

    protected CloudApplication getAppToPoll(DelegateExecution context) {
        return StepsUtil.getApp(context);
    }

    private StartupStatus getStartupStatus(ExecutionWrapper execution, CloudFoundryOperations client, String appName) {
        CloudApplication app = client.getApplication(appName);
        List<InstanceInfo> instances = getApplicationInstances(client, app);

        // The default value here is provided for undeploy processes:
        boolean failOnCrashed = ContextUtil.getVariable(execution.getContext(), Constants.PARAM_FAIL_ON_CRASHED, true);

        if (instances != null) {
            int expectedInstances = app.getInstances();
            int runningInstances = getInstanceCount(instances, InstanceState.RUNNING);
            int flappingInstances = getInstanceCount(instances, InstanceState.FLAPPING);
            int crashedInstances = getInstanceCount(instances, InstanceState.CRASHED);
            int startingInstances = getInstanceCount(instances, InstanceState.STARTING);

            showInstancesStatus(execution, instances, runningInstances, expectedInstances);

            if (runningInstances == expectedInstances) {
                return StartupStatus.STARTED;
            }
            if (startingInstances > 0) {
                return StartupStatus.STARTING;
            }
            if (flappingInstances > 0) {
                return StartupStatus.FLAPPING;
            }
            if (crashedInstances > 0 && failOnCrashed) {
                return StartupStatus.CRASHED;
            }
        }

        return StartupStatus.STARTING;
    }

    private ExecutionStatus checkStartupStatus(ExecutionWrapper execution, CloudFoundryOperations client, CloudApplication app,
        StartupStatus status) throws SLException {

        StepsUtil.saveAppLogs(execution.getContext(), client, recentLogsRetriever, app, LOGGER.getLoggerImpl(),
            execution.getProcessLoggerProviderFactory());
        if (status.equals(StartupStatus.CRASHED) || status.equals(StartupStatus.FLAPPING)) {
            // Application failed to start
            String message = format(Messages.ERROR_STARTING_APP_2, app.getName(), getMessageForStatus(status));
            onError(execution, message);
            setType(execution, StepPhase.RETRY);
            return ExecutionStatus.FAILED;
        } else if (status.equals(StartupStatus.STARTED)) {
            // Application started successfully
            List<String> uris = app.getUris();
            if (uris.isEmpty()) {
                execution.getStepLogger().info(Messages.APP_STARTED, app.getName());
            } else {
                String urls = CommonUtil.toCommaDelimitedString(uris, getProtocolPrefix());
                execution.getStepLogger().info(Messages.APP_STARTED_URLS, app.getName(), urls);
            }
            StepsUtil.setStepPhase(execution, StepPhase.POLL);
            return ExecutionStatus.SUCCESS;
        } else {
            // Application not started yet, wait and try again unless it's a timeout
            if (StepsUtil.hasTimedOut(execution.getContext(), () -> System.currentTimeMillis())) {
                String message = format(Messages.APP_START_TIMED_OUT, app.getName());
                onError(execution, message);
                setType(execution, StepPhase.RETRY);
                return ExecutionStatus.FAILED;
            }
            StepsUtil.setStepPhase(execution, StepPhase.POLL);
            return ExecutionStatus.RUNNING;
        }
    }

    private void setType(ExecutionWrapper execution, StepPhase type) {
        StepsUtil.setStepPhase(execution, type);
    }

    protected String getMessageForStatus(StartupStatus status) {
        if (status.equals(StartupStatus.FLAPPING)) {
            return "Some instances are flapping";
        } else if (status.equals(StartupStatus.CRASHED)) {
            return "Some instances have crashed";
        } else {
            return null;
        }
    }

    private void showInstancesStatus(ExecutionWrapper execution, List<InstanceInfo> instances, int runningInstances,
        int expectedInstances) {

        // Determine state counts
        Map<String, Integer> stateCounts = new HashMap<>();
        if (instances.isEmpty()) {
            stateCounts.put(InstanceState.STARTING.toString(), 0);
        } else {
            for (InstanceInfo instance : instances) {
                final String state = instance.getState().toString();
                final Integer stateCount = stateCounts.get(state);
                stateCounts.put(state, (stateCount == null) ? 1 : (stateCount + 1));
            }
        }

        // Compose state strings
        List<String> stateStrings = new ArrayList<>();
        for (Map.Entry<String, Integer> sc : stateCounts.entrySet()) {
            stateStrings.add(format("{0} {1}", sc.getValue(), sc.getKey().toLowerCase()));
        }

        // Print message
        String message = format(Messages.X_OF_Y_INSTANCES_RUNNING, runningInstances, expectedInstances,
            CommonUtil.toCommaDelimitedString(stateStrings, ""));
        execution.getStepLogger().info(message);
    }

    private static List<InstanceInfo> getApplicationInstances(CloudFoundryOperations client, CloudApplication app) {
        InstancesInfo instancesInfo = client.getApplicationInstances(app);
        return (instancesInfo != null) ? instancesInfo.getInstances() : null;
    }

    private static int getInstanceCount(List<InstanceInfo> instances, InstanceState state) {
        int count = 0;
        for (InstanceInfo instance : instances) {
            if (instance.getState().equals(state)) {
                count++;
            }
        }
        return count;
    }

    private String getProtocolPrefix() {
        return configuration.getTargetURL().getProtocol() + "://";
    }

}
