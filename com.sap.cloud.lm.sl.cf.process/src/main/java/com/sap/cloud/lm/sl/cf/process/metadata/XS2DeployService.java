package com.sap.cloud.lm.sl.cf.process.metadata;

import java.util.HashSet;
import java.util.Set;

import com.sap.cloud.lm.sl.cf.process.Constants;
import com.sap.cloud.lm.sl.cf.process.steps.AddDomainsStep;
import com.sap.cloud.lm.sl.cf.process.steps.BuildCloudDeployModelStep;
import com.sap.cloud.lm.sl.cf.process.steps.BuildCloudUndeployModelStep;
import com.sap.cloud.lm.sl.cf.process.steps.CheckForCreationConflictsStep;
import com.sap.cloud.lm.sl.cf.process.steps.CollectSystemParametersStep;
import com.sap.cloud.lm.sl.cf.process.steps.CreateOrUpdateServiceBrokersStep;
import com.sap.cloud.lm.sl.cf.process.steps.CreateOrUpdateServicesStep;
import com.sap.cloud.lm.sl.cf.process.steps.CreateSubscriptionsStep;
import com.sap.cloud.lm.sl.cf.process.steps.DeleteDiscontinuedConfigurationEntriesStep;
import com.sap.cloud.lm.sl.cf.process.steps.DeleteServiceBrokersStep;
import com.sap.cloud.lm.sl.cf.process.steps.DeleteServicesStep;
import com.sap.cloud.lm.sl.cf.process.steps.DeleteSubscriptionsStep;
import com.sap.cloud.lm.sl.cf.process.steps.DeleteUnusedReservedRoutesStep;
import com.sap.cloud.lm.sl.cf.process.steps.DetectDeployedMtaStep;
import com.sap.cloud.lm.sl.cf.process.steps.DetectMtaSchemaVersionStep;
import com.sap.cloud.lm.sl.cf.process.steps.DetectTargetStep;
import com.sap.cloud.lm.sl.cf.process.steps.MergeDescriptorsStep;
import com.sap.cloud.lm.sl.cf.process.steps.PrepareAppsDeploymentStep;
import com.sap.cloud.lm.sl.cf.process.steps.PrepareToRestartServiceBrokersStep;
import com.sap.cloud.lm.sl.cf.process.steps.PrepareToUndeployAppsStep;
import com.sap.cloud.lm.sl.cf.process.steps.ProcessDescriptorStep;
import com.sap.cloud.lm.sl.cf.process.steps.ProcessGitSourceStep;
import com.sap.cloud.lm.sl.cf.process.steps.ProcessMtaArchiveStep;
import com.sap.cloud.lm.sl.cf.process.steps.ProcessMtaExtensionDescriptorsStep;
import com.sap.cloud.lm.sl.cf.process.steps.RegisterServiceUrlsStep;
import com.sap.cloud.lm.sl.cf.process.steps.RestartUpdatedSubscribersStep;
import com.sap.cloud.lm.sl.cf.process.steps.UnregisterServiceUrlsStep;
import com.sap.cloud.lm.sl.cf.process.steps.UpdateSubscribersStep;
import com.sap.cloud.lm.sl.cf.process.steps.ValidateDeployParametersStep;
import com.sap.cloud.lm.sl.mta.model.VersionRule;
import com.sap.cloud.lm.sl.slp.model.ParameterMetadata;
import com.sap.cloud.lm.sl.slp.model.ParameterMetadata.ParameterType;
import com.sap.cloud.lm.sl.slp.model.RoadmapStepMetadata;
import com.sap.cloud.lm.sl.slp.model.ServiceMetadata;
import com.sap.cloud.lm.sl.slp.model.StepMetadata;

public class XS2DeployService {

    private static final StepMetadata STEP_START = StepMetadata.builder().id("startEvent").displayName("Start").description(
        "Start").build();
    private static final StepMetadata STEP_END = StepMetadata.builder().id("endEvent").displayName("End").description("End").build();

    private static final RoadmapStepMetadata ROADMAP_PREPARE = RoadmapStepMetadata.builder().id("roadmap_prepare").displayName(
        "Prepare").description("Prepare").children(STEP_START, ProcessGitSourceStep.getMetadata(),
            ValidateDeployParametersStep.getMetadata(), ProcessMtaArchiveStep.getMetadata(),
            ProcessMtaExtensionDescriptorsStep.getMetadata(), DetectMtaSchemaVersionStep.getMetadata(), DetectTargetStep.getMetadata(),
            MergeDescriptorsStep.getMetadata(), DetectDeployedMtaStep.getMetadata(), CollectSystemParametersStep.getMetadata(),
            ProcessDescriptorStep.getMetadata(), BuildCloudDeployModelStep.getMetadata(), BuildCloudUndeployModelStep.getMetadata(),
            CheckForCreationConflictsStep.getMetadata()).build();
    private static final RoadmapStepMetadata ROADMAP_EXECUTE = RoadmapStepMetadata.builder().id("roadmap_execute").displayName(
        "Execute").description("Execute").children(AddDomainsStep.getMetadata(), DeleteUnusedReservedRoutesStep.getMetadata(),
            CreateOrUpdateServicesStep.getMetadata(), PrepareAppsDeploymentStep.getMetadata(), CreateSubscriptionsStep.getMetadata(),
            RegisterServiceUrlsStep.getMetadata(), DeleteSubscriptionsStep.getMetadata(),
            DeleteDiscontinuedConfigurationEntriesStep.getMetadata(), UnregisterServiceUrlsStep.getMetadata(),
            PrepareToUndeployAppsStep.getMetadata(), DeleteServicesStep.getMetadata(), CreateOrUpdateServiceBrokersStep.getMetadata(),
            DeleteServiceBrokersStep.getMetadata(), UpdateSubscribersStep.getMetadata(), RestartUpdatedSubscribersStep.getMetadata(),
            PrepareToRestartServiceBrokersStep.getMetadata(), STEP_END).build();

    private final static Set<ParameterMetadata> PARAMS = new HashSet<ParameterMetadata>();

    static {
        PARAMS.add(ParameterMetadata.builder().id(Constants.PARAM_APP_ARCHIVE_ID).build());
        PARAMS.add(ParameterMetadata.builder().id(Constants.PARAM_TARGET_NAME).build());
        PARAMS.add(ParameterMetadata.builder().id(Constants.PARAM_EXT_DESCRIPTOR_FILE_ID).build());
        PARAMS.add(ParameterMetadata.builder().id(Constants.PARAM_NO_START).type(ParameterType.BOOLEAN).defaultValue(false).build());
        PARAMS.add(ParameterMetadata.builder().id(Constants.PARAM_START_TIMEOUT).type(ParameterType.INTEGER).defaultValue(
            Constants.DEFAULT_START_TIMEOUT).build());
        PARAMS.add(ParameterMetadata.builder().id(Constants.PARAM_UPLOAD_TIMEOUT).type(ParameterType.INTEGER).build());
        PARAMS.add(ParameterMetadata.builder().id(Constants.PARAM_USE_NAMESPACES).type(ParameterType.BOOLEAN).defaultValue(false).build());
        PARAMS.add(ParameterMetadata.builder().id(Constants.PARAM_USE_NAMESPACES_FOR_SERVICES).type(ParameterType.BOOLEAN).defaultValue(
            false).build());
        PARAMS.add(ParameterMetadata.builder().id(Constants.PARAM_ALLOW_INVALID_ENV_NAMES).type(ParameterType.BOOLEAN).defaultValue(
            false).build());
        PARAMS.add(ParameterMetadata.builder().id(Constants.PARAM_VERSION_RULE).defaultValue(VersionRule.SAME_HIGHER.toString()).build());
        PARAMS.add(ParameterMetadata.builder().id(Constants.PARAM_DELETE_SERVICES).type(ParameterType.BOOLEAN).defaultValue(false).build());
        PARAMS.add(
            ParameterMetadata.builder().id(Constants.PARAM_DELETE_SERVICE_KEYS).type(ParameterType.BOOLEAN).defaultValue(false).build());
        PARAMS.add(
            ParameterMetadata.builder().id(Constants.PARAM_DELETE_SERVICE_BROKERS).type(ParameterType.BOOLEAN).defaultValue(false).build());
        PARAMS.add(ParameterMetadata.builder().id(Constants.PARAM_FAIL_ON_CRASHED).type(ParameterType.BOOLEAN).defaultValue(true).build());
        PARAMS.add(ParameterMetadata.builder().id(Constants.PARAM_MTA_ID).build());
        PARAMS.add(ParameterMetadata.builder().id(Constants.PARAM_KEEP_FILES).type(ParameterType.BOOLEAN).defaultValue(false).build());
        PARAMS.add(ParameterMetadata.builder().id(Constants.PARAM_NO_RESTART_SUBSCRIBED_APPS).type(ParameterType.BOOLEAN).defaultValue(
            false).build());
        PARAMS.add(ParameterMetadata.builder().id(Constants.PARAM_NO_FAIL_ON_MISSING_PERMISSIONS).type(ParameterType.BOOLEAN).defaultValue(
            false).build());
        PARAMS.add(ParameterMetadata.builder().id(Constants.PARAM_GIT_URI).defaultValue("").build());
        PARAMS.add(ParameterMetadata.builder().id(Constants.PARAM_GIT_REF).build());
        PARAMS.add(ParameterMetadata.builder().id(Constants.PARAM_GIT_REPO_PATH).build());
        PARAMS.add(ParameterMetadata.builder().id(Constants.PARAM_GIT_SKIP_SSL).type(ParameterType.BOOLEAN).defaultValue(false).build());
    }

    public static ServiceMetadata getMetadata() {
        return ServiceMetadata.builder().id(Constants.DEPLOY_SERVICE_ID).displayName("Deploy").description("Deploy").children(
            ROADMAP_PREPARE, ROADMAP_EXECUTE).versions(Constants.SERVICE_VERSION_1_1, Constants.SERVICE_VERSION_1_2).parameters(
                PARAMS).build();
    }

}
