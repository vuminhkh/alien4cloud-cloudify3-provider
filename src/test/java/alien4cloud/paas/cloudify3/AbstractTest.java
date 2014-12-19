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
import alien4cloud.paas.cloudify3.configuration.Flavor;
import alien4cloud.paas.cloudify3.configuration.Image;
import alien4cloud.paas.cloudify3.dao.BlueprintDAO;
import alien4cloud.paas.cloudify3.dao.DeploymentDAO;
import alien4cloud.paas.cloudify3.model.Blueprint;
import alien4cloud.paas.cloudify3.model.Deployment;
import alien4cloud.paas.cloudify3.service.ComputeTemplateMatcherService;
import alien4cloud.paas.cloudify3.service.DeploymentService;
import alien4cloud.paas.cloudify3.util.CSARUtil;
import alien4cloud.paas.model.PaaSDeploymentContext;
import alien4cloud.utils.FileUtil;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class AbstractTest {

    public static final String SINGLE_COMPUTE_TOPOLOGY = "single_compute";

    public static final String SINGLE_COMPUTE_TOPOLOGY_WITH_APACHE = "single_compute_with_apache";

    @Resource
    private CSARUtil csarUtil;

    @Resource
    private BlueprintDAO blueprintDAO;

    @Resource
    private DeploymentDAO deploymentDAO;

    @Resource(name = "cloudify-deployment-service")
    private DeploymentService deploymentService;

    @Resource
    private CloudConfigurationHolder cloudConfigurationHolder;

    @Resource
    private ComputeTemplateMatcherService computeTemplateMatcherService;

    private ComputeTemplate computeTemplate = new ComputeTemplate("alien_image", "alien_flavor");

    @BeforeClass
    public static void cleanup() throws IOException {
        FileUtil.delete(CSARUtil.ARTIFACTS_DIRECTORY);
        FileUtil.delete(Paths.get("target/alien"));
    }

    @Before
    public void before() throws Exception {
        cloudConfigurationHolder.getConfiguration().setUrl("http://129.185.67.87:8100");
        cloudConfigurationHolder.getConfiguration().setImages(Sets.newHashSet(new Image("727df994-2e1b-404e-9276-b248223a835d", "Ubuntu Precise")));
        cloudConfigurationHolder.getConfiguration().setFlavors(Sets.newHashSet(new Flavor("2", "Medium")));

        CloudResourceMatcherConfig matcherConfig = new CloudResourceMatcherConfig();
        matcherConfig.setMatchedComputeTemplates(Lists.newArrayList(new MatchedComputeTemplate(computeTemplate, "Medium_Ubuntu_Precise")));
        computeTemplateMatcherService.configure(matcherConfig);

        csarUtil.uploadNormativeTypes();
        csarUtil.uploadApacheTypes();

        // Clean deployment
        Deployment[] deployments = deploymentDAO.list();
        if (deployments.length > 0) {
            for (Deployment deployment : deployments) {
                PaaSDeploymentContext context = new PaaSDeploymentContext();
                context.setDeploymentId(deployment.getId());
                context.setRecipeId(deployment.getBlueprintId());
                deploymentService.undeploy(context).get();
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
