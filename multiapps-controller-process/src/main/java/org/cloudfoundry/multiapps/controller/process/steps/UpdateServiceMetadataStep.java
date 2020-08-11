package org.cloudfoundry.multiapps.controller.process.steps;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.inject.Named;

import org.cloudfoundry.client.lib.CloudControllerClient;
import org.cloudfoundry.multiapps.controller.client.lib.domain.CloudServiceInstanceExtended;
import org.cloudfoundry.multiapps.controller.core.model.ServiceOperation;
import org.cloudfoundry.multiapps.controller.core.util.MethodExecution;
import org.cloudfoundry.multiapps.controller.process.Messages;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;

@Named("updateServiceMetadataStep")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class UpdateServiceMetadataStep extends ServiceStep {

    @Override
    protected MethodExecution<String> executeOperation(ProcessContext context, CloudControllerClient controllerClient,
                                                       CloudServiceInstanceExtended service) {
        return updateServiceMetadata(controllerClient, service);
    }

    private MethodExecution<String> updateServiceMetadata(CloudControllerClient controllerClient, CloudServiceInstanceExtended service) {
        getStepLogger().debug(Messages.UPDATING_METADATA_OF_SERVICE_INSTANCE_0, service.getName(), service.getResourceName());
        updateServiceMetadata(service, controllerClient);
        getStepLogger().debug(Messages.UPDATING_METADATA_OF_SERVICE_INSTANCE_0_DONE, service.getName());
        return new MethodExecution<>(null, MethodExecution.ExecutionState.FINISHED);
    }

    private void updateServiceMetadata(CloudServiceInstanceExtended serviceToProcess, CloudControllerClient client) {
        UUID serviceGuid = client.getServiceInstance(serviceToProcess.getName())
                                 .getMetadata()
                                 .getGuid();
        client.updateServiceInstanceMetadata(serviceGuid, serviceToProcess.getV3Metadata());
    }

    @Override
    protected List<AsyncExecution> getAsyncStepExecutions(ProcessContext context) {
        return Collections.singletonList(new PollServiceCreateOrUpdateOperationsExecution(getServiceOperationGetter(),
                                                                                          getServiceProgressReporter()));
    }

    @Override
    protected ServiceOperation.Type getOperationType() {
        return ServiceOperation.Type.UPDATE;
    }

}
