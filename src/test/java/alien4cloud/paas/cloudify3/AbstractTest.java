package alien4cloud.paas.cloudify3;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;

import javax.annotation.Resource;

import org.junit.Before;
import org.junit.BeforeClass;

import alien4cloud.model.application.DeploymentSetup;
import alien4cloud.model.cloud.CloudResourceMatcherConfig;
import alien4cloud.model.cloud.ComputeTemplate;
import alien4cloud.model.cloud.MatchedComputeTemplate;
import alien4cloud.paas.cloudify3.configuration.CloudConfigurationHolder;
import alien4cloud.paas.cloudify3.configuration.CloudifyComputeTemplate;
import alien4cloud.paas.cloudify3.dao.BlueprintDAO;
import alien4cloud.paas.cloudify3.dao.DeploymentDAO;
import alien4cloud.paas.cloudify3.model.Blueprint;
import alien4cloud.paas.cloudify3.model.Deployment;
import alien4cloud.paas.cloudify3.service.ComputeTemplateMatcherService;
import alien4cloud.paas.cloudify3.util.CSARUtil;
import alien4cloud.utils.FileUtil;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class AbstractTest {

    public static final String BLUEPRINT_ID = "testBlueprint";

    public static final String SINGLE_COMPUTE_TOPOLOGY = "single_compute";

    @Resource
    private CSARUtil csarUtil;

    @Resource
    private BlueprintDAO blueprintDAO;

    @Resource
    private DeploymentDAO deploymentDAO;

    @Resource
    private CloudConfigurationHolder cloudConfigurationHolder;

    @Resource
    private ComputeTemplateMatcherService computeTemplateMatcherService;

    private ComputeTemplate computeTemplate = new ComputeTemplate("image", "flavor");

    @BeforeClass
    public static void cleanup() throws IOException {
        FileUtil.delete(CSARUtil.ARTIFACTS_DIRECTORY);
        FileUtil.delete(Paths.get("target/alien"));
    }

    @Before
    public void before() throws Exception {
        CloudifyComputeTemplate mediumLinux = new CloudifyComputeTemplate();
        mediumLinux.setImage("727df994-2e1b-404e-9276-b248223a835d");
        mediumLinux.setFlavor("2");
        cloudConfigurationHolder.getConfiguration().getComputeTemplates().put("MEDIUM_LINUX", mediumLinux);

        CloudResourceMatcherConfig matcherConfig = new CloudResourceMatcherConfig();
        matcherConfig.setMatchedComputeTemplates(Lists.newArrayList(new MatchedComputeTemplate(computeTemplate, "MEDIUM_LINUX")));
        computeTemplateMatcherService.configure(matcherConfig);

        csarUtil.uploadNormativeTypes();

        // Clean deployment
        Deployment[] deployments = deploymentDAO.list();
        if (deployments.length > 0) {
            for (Deployment deployment : deployments) {
                deploymentDAO.delete(deployment.getId());
            }
        }
        Thread.sleep(1000L);

        // Clean blueprint
        Blueprint[] blueprints = blueprintDAO.list();
        if (blueprints.length > 0) {
            for (Blueprint blueprint : blueprints) {
                blueprintDAO.delete(blueprint.getId());
            }
        }
        Thread.sleep(1000L);
    }

    protected DeploymentSetup generateDeploymentSetup(Collection<String> nodeIds) {
        DeploymentSetup deploymentSetup = new DeploymentSetup();
        Map<String, ComputeTemplate> resourcesMapping = Maps.newHashMap();
        deploymentSetup.setCloudResourcesMapping(resourcesMapping);
        for (String nodeId : nodeIds) {
            resourcesMapping.put(nodeId, computeTemplate);
        }
        return deploymentSetup;
    }
}
