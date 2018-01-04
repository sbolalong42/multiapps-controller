package com.sap.cloud.lm.sl.cf.process.steps;

import java.util.List;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.sap.activiti.common.ExecutionStatus;
import com.sap.cloud.lm.sl.cf.core.cf.HandlerFactory;
import com.sap.cloud.lm.sl.cf.core.helpers.MtaDescriptorMerger;
import com.sap.cloud.lm.sl.cf.process.message.Messages;
import com.sap.cloud.lm.sl.common.SLException;
import com.sap.cloud.lm.sl.mta.model.v1_0.DeploymentDescriptor;
import com.sap.cloud.lm.sl.mta.model.v1_0.Platform;
import com.sap.cloud.lm.sl.mta.model.v1_0.Target;

@Component("mergeDescriptorsStep")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class MergeDescriptorsStep extends SyncActivitiStep {

    protected MtaDescriptorMerger getMtaDescriptorMerger(HandlerFactory factory, Platform platform, Target target) {
        return new MtaDescriptorMerger(factory, platform, target);
    }

    @Override
    protected ExecutionStatus executeStep(ExecutionWrapper execution) throws SLException {
        getStepLogger().logActivitiTask();

        getStepLogger().info(Messages.MERGING_DESCRIPTORS);
        try {
            String deploymentDescriptorString = StepsUtil.getDeploymentDescriptorString(execution.getContext());
            List<String> extensionDescriptorStrings = StepsUtil.getExtensionDescriptorStrings(execution.getContext());

            HandlerFactory handlerFactory = StepsUtil.getHandlerFactory(execution.getContext());

            Target target = StepsUtil.getTarget(execution.getContext());
            Platform platform = StepsUtil.getPlatform(execution.getContext());

            DeploymentDescriptor descriptor = getMtaDescriptorMerger(handlerFactory, platform, target).merge(deploymentDescriptorString,
                extensionDescriptorStrings);

            StepsUtil.setUnresolvedDeploymentDescriptor(execution.getContext(), descriptor);
        } catch (SLException e) {
            getStepLogger().error(e, Messages.ERROR_MERGING_DESCRIPTORS);
            throw e;
        }
        getStepLogger().debug(Messages.DESCRIPTORS_MERGED);

        return ExecutionStatus.SUCCESS;
    }

}
