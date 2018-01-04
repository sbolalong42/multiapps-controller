package com.sap.cloud.lm.sl.cf.process.steps;

import static com.sap.cloud.lm.sl.cf.process.steps.StepsTestUtil.loadDeploymentDescriptor;
import static com.sap.cloud.lm.sl.cf.process.steps.StepsTestUtil.loadPlatforms;
import static com.sap.cloud.lm.sl.cf.process.steps.StepsTestUtil.loadTargets;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.activiti.engine.delegate.DelegateExecution;
import org.cloudfoundry.client.lib.domain.ServiceKey;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mock;

import com.google.gson.reflect.TypeToken;
import com.sap.cloud.lm.sl.cf.client.lib.domain.CloudApplicationExtended;
import com.sap.cloud.lm.sl.cf.client.lib.domain.CloudServiceExtended;
import com.sap.cloud.lm.sl.cf.core.cf.v1_0.ApplicationsCloudModelBuilder;
import com.sap.cloud.lm.sl.cf.core.cf.v1_0.DomainsCloudModelBuilder;
import com.sap.cloud.lm.sl.cf.core.cf.v1_0.ServiceKeysCloudModelBuilder;
import com.sap.cloud.lm.sl.cf.core.cf.v1_0.ServicesCloudModelBuilder;
import com.sap.cloud.lm.sl.cf.core.model.DeployedMta;
import com.sap.cloud.lm.sl.cf.core.model.SupportedParameters;
import com.sap.cloud.lm.sl.cf.process.Constants;
import com.sap.cloud.lm.sl.common.util.JsonUtil;
import com.sap.cloud.lm.sl.common.util.TestUtil;
import com.sap.cloud.lm.sl.mta.handlers.v1_0.ConfigurationParser;
import com.sap.cloud.lm.sl.mta.handlers.v1_0.DescriptorParser;
import com.sap.cloud.lm.sl.mta.model.SystemParameters;
import com.sap.cloud.lm.sl.mta.model.v1_0.DeploymentDescriptor;
import com.sap.cloud.lm.sl.mta.model.v1_0.Platform;
import com.sap.cloud.lm.sl.mta.model.v1_0.Target;

@RunWith(Parameterized.class)
public class BuildCloudDeployModelStepTest extends SyncActivitiStepTest<BuildCloudDeployModelStep> {

    private static final ConfigurationParser CONFIGURATION_PARSER = new ConfigurationParser();
    private static final DescriptorParser DESCRIPTOR_PARSER = new DescriptorParser();

    private static final Integer MTA_MAJOR_SCHEMA_VERSION = 1;
    private static final Integer MTA_MINOR_SCHEMA_VERSION = 0;

    private static final DeploymentDescriptor DEPLOYMENT_DESCRIPTOR = loadDeploymentDescriptor(DESCRIPTOR_PARSER, "node-hello-mtad.yaml",
        BuildCloudDeployModelStepTest.class);
    private static final Platform PLATFORM = loadPlatforms(CONFIGURATION_PARSER, "platform-types-01.json",
        BuildCloudDeployModelStepTest.class).get(0);
    private static final Target TARGET = loadTargets(CONFIGURATION_PARSER, "platforms-01.json", BuildCloudDeployModelStepTest.class).get(0);

    private static final SystemParameters EMPTY_SYSTEM_PARAMETERS = new SystemParameters(Collections.emptyMap(), Collections.emptyMap(),
        Collections.emptyMap(), Collections.emptyMap());

    private static class StepInput {

        public String servicesToBindLocation;
        public String servicesToCreateLocation;
        public String deployedMtaLocation;
        public String serviceKeysLocation;
        public String appsToDeployLocation;
        public List<String> customDomains;

        public StepInput(String appsToDeployLocation, String servicesToBindLocation, String servicesToCreateLocation,
            String serviceKeysLocation, List<String> customDomains, String deployedMtaLocation) {
            this.servicesToBindLocation = servicesToBindLocation;
            this.servicesToCreateLocation = servicesToCreateLocation;
            this.deployedMtaLocation = deployedMtaLocation;
            this.serviceKeysLocation = serviceKeysLocation;
            this.appsToDeployLocation = appsToDeployLocation;
            this.customDomains = customDomains;
        }

    }

    private static class StepOutput {

        public String newMtaVersion;

        public StepOutput(String newMtaVersion) {
            this.newMtaVersion = newMtaVersion;
        }

    }

    private class BuildCloudDeployModelStepMock extends BuildCloudDeployModelStep {
        @Override
        protected ApplicationsCloudModelBuilder getApplicationsCloudModelBuilder(DelegateExecution context) {
            return applicationsCloudModelBuilder;
        }

        @Override
        protected ServicesCloudModelBuilder getServicesCloudModelBuilder(DelegateExecution context) {
            return servicesCloudModelBuilder;
        }

        @Override
        protected DomainsCloudModelBuilder getDomainsCloudModelBuilder(DelegateExecution context) {
            return domainsCloudModelBuilder;
        }

        @Override
        protected ServiceKeysCloudModelBuilder getServiceKeysCloudModelBuilder(DelegateExecution context,
            DeploymentDescriptor deploymentDescriptor) {
            return serviceKeysCloudModelBuilder;
        }
    }

    @Parameters
    public static Iterable<Object[]> getParameters() {
        return Arrays.asList(new Object[][] {
// @formatter:off
            {
                new StepInput("apps-to-deploy-05.json", "services-to-bind-01.json", "services-to-create-01.json", "service-keys-01.json", Arrays.asList("custom-domain-1", "custom-domain-2"), "deployed-mta-12.json"), new StepOutput("0.1.0"),
            },
            {
                new StepInput("apps-to-deploy-05.json", "services-to-bind-01.json", "services-to-create-01.json", "service-keys-01.json", Arrays.asList("custom-domain-1", "custom-domain-2"), null), new StepOutput("0.1.0"),
            },
// @formatter:on
        });
    }

    private StepOutput output;
    private StepInput input;

    private List<CloudApplicationExtended> appsToDeploy;
    private DeployedMta deployedMta;
    private List<CloudServiceExtended> servicesToBind;
    private Map<String, List<ServiceKey>> serviceKeys;

    @Mock
    private ApplicationsCloudModelBuilder applicationsCloudModelBuilder;
    @Mock
    private DomainsCloudModelBuilder domainsCloudModelBuilder;
    @Mock
    private ServiceKeysCloudModelBuilder serviceKeysCloudModelBuilder;
    @Mock
    private ServicesCloudModelBuilder servicesCloudModelBuilder;

    public BuildCloudDeployModelStepTest(StepInput input, StepOutput output) {
        this.output = output;
        this.input = input;
    }

    @Before
    public void setUp() throws Exception {
        loadParameters();
        prepareContext();
    }

    private void prepareContext() throws Exception {
        context.setVariable(Constants.VAR_MTA_MAJOR_SCHEMA_VERSION, MTA_MAJOR_SCHEMA_VERSION);
        context.setVariable(Constants.VAR_MTA_MINOR_SCHEMA_VERSION, MTA_MINOR_SCHEMA_VERSION);

        StepsUtil.setSystemParameters(context, EMPTY_SYSTEM_PARAMETERS);
        StepsUtil.setMtaModules(context, Collections.emptySet());
        StepsUtil.setMtaArchiveModules(context, Collections.emptySet());
        StepsUtil.setDeploymentDescriptor(context, DEPLOYMENT_DESCRIPTOR);
        StepsUtil.setXsPlaceholderReplacementValues(context, getDummyReplacementValues());

        StepsUtil.setPlatform(context, PLATFORM);
        StepsUtil.setTarget(context, TARGET);
    }

    private Map<String, Object> getDummyReplacementValues() {
        Map<String, Object> result = new TreeMap<>();
        result.put(SupportedParameters.XSA_ROUTER_PORT_PLACEHOLDER, 0);
        return result;
    }

    @Test
    public void testExecute() throws Exception {
        step.execute(context);

        assertStepFinishedSuccessfully();

        TestUtil.test(() -> StepsUtil.getServicesToBind(context), "R:" + input.servicesToBindLocation, getClass());
        TestUtil.test(() -> StepsUtil.getServicesToCreate(context), "R:" + input.servicesToCreateLocation, getClass());
        TestUtil.test(() -> StepsUtil.getServiceKeysToCreate(context), "R:" + input.serviceKeysLocation, getClass());
        TestUtil.test(() -> StepsUtil.getAppsToDeploy(context), "R:" + input.appsToDeployLocation, getClass());
        assertEquals(input.customDomains, StepsUtil.getCustomDomains(context));

        assertEquals(output.newMtaVersion, StepsUtil.getNewMtaVersion(context));
    }

    private void loadParameters() throws Exception {
        String appsToDeployString = TestUtil.getResourceAsString(input.appsToDeployLocation, getClass());
        appsToDeploy = JsonUtil.fromJson(appsToDeployString, new TypeToken<List<CloudApplicationExtended>>() {
        }.getType());

        String servicesToBindString = TestUtil.getResourceAsString(input.servicesToBindLocation, getClass());
        servicesToBind = JsonUtil.fromJson(servicesToBindString, new TypeToken<List<CloudServiceExtended>>() {
        }.getType());

        String serviceKeysString = TestUtil.getResourceAsString(input.serviceKeysLocation, getClass());
        serviceKeys = JsonUtil.fromJson(serviceKeysString, new TypeToken<Map<String, List<ServiceKey>>>() {
        }.getType());

        if (input.deployedMtaLocation != null) {
            String deployedMtaString = TestUtil.getResourceAsString(input.deployedMtaLocation, getClass());
            deployedMta = JsonUtil.fromJson(deployedMtaString, DeployedMta.class);
        }

        when(applicationsCloudModelBuilder.build(any(), any(), any())).thenReturn(appsToDeploy);
        when(domainsCloudModelBuilder.build()).thenReturn(input.customDomains);
        when(servicesCloudModelBuilder.build(any())).thenReturn(servicesToBind);
        when(serviceKeysCloudModelBuilder.build()).thenReturn(serviceKeys);
        StepsUtil.setDeployedMta(context, deployedMta);
    }

    @Override
    protected BuildCloudDeployModelStep createStep() {
        return new BuildCloudDeployModelStepMock();
    }

}
