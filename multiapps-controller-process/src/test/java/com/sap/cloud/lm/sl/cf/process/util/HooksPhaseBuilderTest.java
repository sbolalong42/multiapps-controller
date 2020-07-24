package com.sap.cloud.lm.sl.cf.process.util;

import java.util.Collections;
import java.util.List;

import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.sap.cloud.lm.sl.cf.core.cf.CloudControllerClientProvider;
import com.sap.cloud.lm.sl.cf.core.model.HookPhase;
import com.sap.cloud.lm.sl.cf.core.model.Phase;
import com.sap.cloud.lm.sl.cf.core.model.SubprocessPhase;
import com.sap.cloud.lm.sl.cf.process.mock.MockDelegateExecution;
import com.sap.cloud.lm.sl.cf.process.steps.ProcessContext;
import com.sap.cloud.lm.sl.cf.process.variables.Variables;
import com.sap.cloud.lm.sl.cf.web.api.model.ProcessType;

class HooksPhaseBuilderTest {

    private final ProcessContext context = createContext();
    @Mock
    private ProcessTypeParser processTypeParser;

    HooksPhaseBuilderTest() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void testBuildHookPhaseForDeployProcess() {
        Mockito.when(processTypeParser.getProcessType(context.getExecution()))
               .thenReturn(ProcessType.DEPLOY);
        HooksPhaseBuilder hooksPhaseBuilder = new HooksPhaseBuilder(processTypeParser);
        List<HookPhase> hookPhases = hooksPhaseBuilder.buildHookPhases(Collections.singletonList(HookPhase.BEFORE_STOP), context);
        Assertions.assertEquals(Collections.singletonList(HookPhase.DEPLOY_APPLICATION_BEFORE_STOP), hookPhases);
    }

    @Test
    void testBuildHookPhaseForBlueGreenDeployProcessWithSubprocessPhaseBeforeApplicationStop() {
        Mockito.when(processTypeParser.getProcessType(context.getExecution()))
               .thenReturn(ProcessType.BLUE_GREEN_DEPLOY);
        context.setVariable(Variables.SUBPROCESS_PHASE, SubprocessPhase.BEFORE_APPLICATION_STOP);
        HooksPhaseBuilder hooksPhaseBuilder = new HooksPhaseBuilder(processTypeParser);
        List<HookPhase> hookPhases = hooksPhaseBuilder.buildHookPhases(Collections.singletonList(HookPhase.BEFORE_STOP), context);
        Assertions.assertEquals(Collections.singletonList(HookPhase.BLUE_GREEN_APPLICATION_BEFORE_STOP_IDLE), hookPhases);
    }

    @Test
    void testBuildHookPhaseForBlueGreenDeployProcessWithSubprocessPhaseBeforeApplicationStart() {
        Mockito.when(processTypeParser.getProcessType(context.getExecution()))
               .thenReturn(ProcessType.BLUE_GREEN_DEPLOY);
        context.setVariable(Variables.SUBPROCESS_PHASE, SubprocessPhase.BEFORE_APPLICATION_START);
        HooksPhaseBuilder hooksPhaseBuilder = new HooksPhaseBuilder(processTypeParser);
        List<HookPhase> hookPhases = hooksPhaseBuilder.buildHookPhases(Collections.singletonList(HookPhase.BEFORE_START), context);
        Assertions.assertEquals(Collections.singletonList(HookPhase.BLUE_GREEN_APPLICATION_BEFORE_START_IDLE), hookPhases);
    }

    @Test
    void testBuildHookPhaseForBlueGreenProcessWithPhaseUndeploy() {
        Mockito.when(processTypeParser.getProcessType(context.getExecution()))
               .thenReturn(ProcessType.BLUE_GREEN_DEPLOY);
        context.setVariable(Variables.PHASE, Phase.UNDEPLOY);
        HooksPhaseBuilder hooksPhaseBuilder = new HooksPhaseBuilder(processTypeParser);
        List<HookPhase> hookPhases = hooksPhaseBuilder.buildHookPhases(Collections.singletonList(HookPhase.AFTER_STOP), context);
        Assertions.assertEquals(Collections.singletonList(HookPhase.BLUE_GREEN_APPLICATION_AFTER_STOP_LIVE), hookPhases);
    }

    @Test
    void testBuildHookPhaseForBlueGreenProcessWithPhaseAfterResume() {
        Mockito.when(processTypeParser.getProcessType(context.getExecution()))
               .thenReturn(ProcessType.BLUE_GREEN_DEPLOY);
        context.setVariable(Variables.PHASE, Phase.AFTER_RESUME);
        HooksPhaseBuilder hooksPhaseBuilder = new HooksPhaseBuilder(processTypeParser);
        List<HookPhase> hookPhases = hooksPhaseBuilder.buildHookPhases(Collections.singletonList(HookPhase.BEFORE_START), context);
        Assertions.assertEquals(Collections.singletonList(HookPhase.BLUE_GREEN_APPLICATION_BEFORE_START_LIVE), hookPhases);
    }

    private ProcessContext createContext() {
        DelegateExecution delegateExecution = MockDelegateExecution.createSpyInstance();
        StepLogger stepLogger = Mockito.mock(StepLogger.class);
        CloudControllerClientProvider cloudControllerClientProvider = Mockito.mock(CloudControllerClientProvider.class);
        return new ProcessContext(delegateExecution, stepLogger, cloudControllerClientProvider);
    }
}
