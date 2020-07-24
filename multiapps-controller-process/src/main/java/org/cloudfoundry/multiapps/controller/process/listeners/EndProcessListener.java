package org.cloudfoundry.multiapps.controller.process.listeners;

import javax.inject.Inject;
import javax.inject.Named;

import org.cloudfoundry.multiapps.controller.api.model.Operation;
import org.cloudfoundry.multiapps.controller.process.util.OperationInFinalStateHandler;
import org.flowable.engine.delegate.DelegateExecution;

@Named("endProcessListener")
public class EndProcessListener extends AbstractProcessExecutionListener {

    private static final long serialVersionUID = 2L;

    private final OperationInFinalStateHandler eventHandler;

    @Inject
    public EndProcessListener(OperationInFinalStateHandler eventHandler) {
        this.eventHandler = eventHandler;
    }

    @Override
    protected void notifyInternal(DelegateExecution execution) {
        if (isRootProcess(execution)) {
            eventHandler.handle(execution, Operation.State.FINISHED);
        }
    }

}
