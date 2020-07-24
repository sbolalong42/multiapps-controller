package org.cloudfoundry.multiapps.controller.process.steps;

import java.text.MessageFormat;
import java.util.Collections;

import javax.inject.Named;

import org.cloudfoundry.client.lib.domain.CloudTask;
import org.cloudfoundry.multiapps.controller.process.Messages;
import org.cloudfoundry.multiapps.controller.process.util.TaskHookParser;
import org.cloudfoundry.multiapps.controller.process.variables.Variables;
import org.cloudfoundry.multiapps.mta.model.Hook;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;

@Named("determineTasksFromHookStep")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class DetermineTasksFromHookStep extends SyncFlowableStep {

    protected final TaskHookParser hookParser = new TaskHookParser();

    @Override
    protected StepPhase executeStep(ProcessContext context) {
        Hook hook = context.getVariable(Variables.HOOK_FOR_EXECUTION);

        getStepLogger().info(Messages.EXECUTING_HOOK_0, hook.getName());

        CloudTask task = hookParser.parse(hook);
        context.setVariable(Variables.TASKS_TO_EXECUTE, Collections.singletonList(task));

        return StepPhase.DONE;
    }

    @Override
    protected String getStepErrorMessage(ProcessContext context) {
        return MessageFormat.format(Messages.ERROR_EXECUTING_HOOK, context.getVariable(Variables.HOOK_FOR_EXECUTION)
                                                                          .getName());
    }

}
