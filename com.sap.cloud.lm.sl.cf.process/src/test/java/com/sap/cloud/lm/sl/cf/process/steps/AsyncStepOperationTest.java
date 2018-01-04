package com.sap.cloud.lm.sl.cf.process.steps;

import java.util.List;

import org.junit.Test;

public abstract class AsyncStepOperationTest<AsyncStep extends SyncActivitiStep> extends SyncActivitiStepTest<AsyncStep> {

    protected abstract List<AsyncExecution> getAsyncOperations();

    @Test
    public void testExecuteOperations() throws Exception {
        step.createStepLogger(context);
        ExecutionWrapper wrapper = step.createExecutionWrapper(context);

        for (AsyncExecution operation : getAsyncOperations()) {
            AsyncExecutionState result = operation.execute(wrapper);
            validateOperationExecutionResult(result);
        }

    }

    protected abstract void validateOperationExecutionResult(AsyncExecutionState result);
}
