package alien4cloud.paas.cloudify3;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import javax.annotation.Resource;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import alien4cloud.model.application.DeploymentSetup;
import alien4cloud.model.cloud.CloudResourceMatcherConfig;
import alien4cloud.model.cloud.ComputeTemplate;
import alien4cloud.model.cloud.MatchedComputeTemplate;
import alien4cloud.paas.cloudify3.configuration.CloudConfigurationHolder;
import alien4cloud.paas.cloudify3.configuration.CloudifyComputeTemplate;
import alien4cloud.paas.cloudify3.dao.BlueprintDAO;
import alien4cloud.paas.cloudify3.matcher.ComputeTemplateMatcher;
import alien4cloud.paas.cloudify3.service.BlueprintService;
import alien4cloud.paas.cloudify3.service.model.AlienDeployment;
import alien4cloud.paas.cloudify3.util.ApplicationUtil;
import alien4cloud.paas.cloudify3.util.CSARUtil;
import alien4cloud.paas.cloudify3.util.DeploymentUtil;
import alien4cloud.tosca.container.model.topology.NodeTemplate;
import alien4cloud.tosca.container.model.topology.Topology;
import alien4cloud.utils.FileUtil;
import junitx.framework.FileAssert;
import lombok.extern.slf4j.Slf4j;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:test-context.xml")
@Slf4j
public class TestBlueprintService {

    public static final String BLUEPRINT_ID = "testBlueprint";

    public static final String SINGLE_COMPUTE_TOPOLOGY = "single_compute";

    @Resource
    private BlueprintDAO blueprintDAO;

    @Resource
    private BlueprintService blueprintService;

    @Resource
    private CloudConfigurationHolder cloudConfigurationHolder;

    @Resource
    private ApplicationUtil applicationUtil;

    @Resource
    private CSARUtil csarUtil;

    @Resource
    private DeploymentUtil deploymentUtil;

    @Resource
    private ComputeTemplateMatcher computeTemplateMatcher;

    @BeforeClass
    public static void cleanup() throws IOException {
        FileUtil.delete(CSARUtil.ARTIFACTS_DIRECTORY);
        FileUtil.delete(Paths.get("target/alien"));
    }

    @Before
    public void before() throws Exception {
        CloudifyComputeTemplate mediumLinux = new CloudifyComputeTemplate();
        mediumLinux.setImage("cac5bf41-6249-4511-b0c9-a167b48b3f1d");
        mediumLinux.setFlavor("2");
        cloudConfigurationHolder.getConfiguration().getComputeTemplates().put("MEDIUM_LINUX", mediumLinux);

        ComputeTemplate computeTemplate = new ComputeTemplate("image", "flavor");

        CloudResourceMatcherConfig matcherConfig = new CloudResourceMatcherConfig();
        matcherConfig.setMatchedComputeTemplates(Lists.newArrayList(new MatchedComputeTemplate(computeTemplate, "MEDIUM_LINUX")));
        computeTemplateMatcher.configure(matcherConfig);

        if (blueprintDAO.list().length > 0) {
            blueprintDAO.delete(BLUEPRINT_ID);
        }
        Thread.sleep(1000L);
        csarUtil.uploadNormativeTypes();
    }

    @Test
    public void testGenerateSingleCompute() {
        Topology topology = applicationUtil.createAlienApplication("testGenerateSingleCompute", SINGLE_COMPUTE_TOPOLOGY);
        NodeTemplate singleCompute = topology.getNodeTemplates().values().iterator().next();
        String singleComputeName = topology.getNodeTemplates().keySet().iterator().next();

        // Create the mapping for the only one compute node
        DeploymentSetup deploymentSetup = new DeploymentSetup();
        Map<String, ComputeTemplate> resourcesMapping = Maps.newHashMap();
        resourcesMapping.put(singleComputeName, new ComputeTemplate("image", "flavor"));
        deploymentSetup.setCloudResourcesMapping(resourcesMapping);

        AlienDeployment alienDeployment = deploymentUtil.buildAlienDeployment("testGenerateSingleCompute", topology, deploymentSetup);
        Path generated = blueprintService.generateBlueprint(alienDeployment);
        FileAssert.assertEquals(new File("src/test/resources/outputs/blueprints/single_compute.yaml"), generated.toFile());
    }
}
