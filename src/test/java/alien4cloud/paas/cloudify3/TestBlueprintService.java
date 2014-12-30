package alien4cloud.paas.cloudify3;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.annotation.Resource;

import junitx.framework.FileAssert;
import lombok.extern.slf4j.Slf4j;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.Assert;

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

    @Before
    @Override
    public void before() throws Exception {
        super.before();
    }

    @Test
    public void testGenerateSingleCompute() {
        Topology topology = applicationUtil.createAlienApplication("testGenerateSingleCompute", SINGLE_COMPUTE_TOPOLOGY);
        CloudifyDeployment alienDeployment = deploymentUtil.buildAlienDeployment("testGenerateSingleCompute", "testGenerateSingleCompute", topology,
                generateDeploymentSetup(topology));
        Path generated = blueprintService.generateBlueprint(alienDeployment);
        FileAssert.assertEquals(new File("src/test/resources/outputs/blueprints/single_compute.yaml"), generated.toFile());
    }

    @Test
    public void testGenerateSingleComputeWithApache() {
        Topology topology = applicationUtil.createAlienApplication("testGenerateSingleComputeWithApache", SINGLE_COMPUTE_TOPOLOGY_WITH_APACHE);
        CloudifyDeployment alienDeployment = deploymentUtil.buildAlienDeployment("testGenerateSingleComputeWithApache", "testGenerateSingleComputeWithApache",
                topology, generateDeploymentSetup(topology));
        Path generated = blueprintService.generateBlueprint(alienDeployment);
        FileAssert.assertEquals(new File("src/test/resources/outputs/blueprints/single_compute_with_apache.yaml"), generated.toFile());
        Assert.isTrue(Files.exists(generated.getParent().resolve("apache-type/alien.nodes.Apache/scripts/start_apache.sh")));
        Assert.isTrue(Files.exists(generated.getParent().resolve("apache-type/alien.nodes.Apache/scripts/install_apache.sh")));
    }

    @Test
    public void testGenerateLamp() {
        Topology topology = applicationUtil.createAlienApplication("testGenerateLamp", LAMP_TOPOLOGY);
        CloudifyDeployment alienDeployment = deploymentUtil.buildAlienDeployment("testGenerateLamp", "testGenerateLamp",
                topology, generateDeploymentSetup(topology));
        blueprintService.generateBlueprint(alienDeployment);
    }
}
