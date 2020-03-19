package com.sap.cloud.lm.sl.cf.process.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sap.cloud.lm.sl.cf.core.model.Phase;
import com.sap.cloud.lm.sl.cf.process.Constants;
import com.sap.cloud.lm.sl.cf.process.mock.MockDelegateExecution;

public class SetUndeployPhaseListenerTest {

    private ExecutionListener executionListener;

    protected final DelegateExecution execution = MockDelegateExecution.createSpyInstance();

    @BeforeEach
    public void setUp() {
        executionListener = new SetUndeployPhaseListener();
    }

    @Test
    public void testPhaseIsSet() {
        executionListener.notify(execution);
        assertEquals(Phase.UNDEPLOY, Phase.valueOf((String) execution.getVariable(Constants.VAR_PHASE)));
    }
}
