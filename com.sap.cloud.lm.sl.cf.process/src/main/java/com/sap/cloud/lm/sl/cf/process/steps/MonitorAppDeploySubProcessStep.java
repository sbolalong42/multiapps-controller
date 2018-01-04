package com.sap.cloud.lm.sl.cf.process.steps;

import java.util.Arrays;
import java.util.List;

import org.activiti.engine.delegate.DelegateExecution;

import com.sap.cloud.lm.sl.cf.core.activiti.ActivitiFacade;
import com.sap.cloud.lm.sl.cf.core.model.ErrorType;
import com.sap.cloud.lm.sl.cf.process.Constants;
import com.sap.cloud.lm.sl.cf.process.exception.MonitoringException;
import com.sap.cloud.lm.sl.cf.process.message.Messages;

public class MonitorAppDeploySubProcessStep extends AbstractSubProcessMonitorStep {

    public MonitorAppDeploySubProcessStep(ActivitiFacade activitiFacade) {
        super(activitiFacade);
    }

    @Override
    protected AsyncExecutionState onError(DelegateExecution context, ErrorType errorType) throws MonitoringException {
        String subProcessId = StepsUtil.getSubProcessId(context);
        throw new MonitoringException(Messages.SUB_PROCESS_HAS_FAILED, subProcessId);
    }

    @Override
    protected AsyncExecutionState onAbort(DelegateExecution context, ErrorType errorType) throws MonitoringException {
        String subProcessId = StepsUtil.getSubProcessId(context);
        throw new MonitoringException(Messages.SUB_PROCESS_HAS_BEEN_ABORTED, subProcessId);
    }

    @Override
    protected List<String> getProcessVariablesToInject() {
        return Arrays.asList(Constants.VAR_SERVICE_KEYS_CREDENTIALS_TO_INJECT);
    }

}
