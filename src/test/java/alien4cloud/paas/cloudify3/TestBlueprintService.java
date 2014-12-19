package alien4cloud.paas.cloudify3;

import java.io.File;
import java.nio.file.Path;

import javax.annotation.Resource;

import junitx.framework.FileAssert;
import lombok.extern.slf4j.Slf4j;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import alien4cloud.paas.cloudify3.service.BlueprintService;
import alien4cloud.paas.cloudify3.service.model.CloudifyDeployment;
import alien4cloud.paas.cloudify3.util.ApplicationUtil;
import alien4cloud.paas.cloudify3.util.DeploymentUtil;
import alien4cloud.tosca.container.model.topology.Topology;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:test-context.xml")
@Slf4j
public class TestBlueprintService extends AbstractTest {

    @Resource
    private BlueprintService blueprintService;

    @Resource
    private ApplicationUtil applicationUtil;

    @Resource
    private DeploymentUtil deploymentUtil;

    @Test
    public void testGenerateSingleCompute() {
        Topology topology = applicationUtil.createAlienApplication("testGenerateSingleCompute", SINGLE_COMPUTE_TOPOLOGY);
        CloudifyDeployment alienDeployment = deploymentUtil.buildAlienDeployment("testGenerateSingleCompute", "testGenerateSingleCompute", topology,
                generateDeploymentSetup(topology.getNodeTemplates().keySet()));
        Path generated = blueprintService.generateBlueprint(alienDeployment);
        FileAssert.assertEquals(new File("src/test/resources/outputs/blueprints/single_compute.yaml"), generated.toFile());
    }

    @Test
    public void testGenerateSingleComputeWithApache() {
        Topology topology = applicationUtil.createAlienApplication("testGenerateSingleComputeWithApache", SINGLE_COMPUTE_TOPOLOGY_WITH_APACHE);
        CloudifyDeployment alienDeployment = deploymentUtil.buildAlienDeployment("testGenerateSingleComputeWithApache", "testGenerateSingleComputeWithApache",
                topology, generateDeploymentSetup(topology.getNodeTemplates().keySet()));
        Path generated = blueprintService.generateBlueprint(alienDeployment);
        FileAssert.assertEquals(new File("src/test/resources/outputs/blueprints/single_compute.yaml"), generated.toFile());
    }
}
