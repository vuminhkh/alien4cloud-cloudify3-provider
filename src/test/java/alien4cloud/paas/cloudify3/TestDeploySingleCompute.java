package alien4cloud.paas.cloudify3;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import alien4cloud.paas.cloudify3.service.DeploymentService;
import alien4cloud.paas.cloudify3.service.model.AlienDeployment;
import alien4cloud.paas.cloudify3.util.ApplicationUtil;
import alien4cloud.paas.cloudify3.util.DeploymentUtil;
import alien4cloud.tosca.container.model.topology.Topology;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:test-context.xml")
@Slf4j
public class TestDeploySingleCompute extends AbstractTest {

    @Resource(name = "cloudify-deployment-service")
    private DeploymentService deploymentService;

    @Resource
    private ApplicationUtil applicationUtil;

    @Resource
    private DeploymentUtil deploymentUtil;

    @Test
    public void testDeploySingleCompute() throws Exception {
        Topology topology = applicationUtil.createAlienApplication("testDeploySingleCompute", SINGLE_COMPUTE_TOPOLOGY);
        AlienDeployment deployment = deploymentUtil.buildAlienDeployment("testDeploySingleCompute", "testDeploySingleCompute", topology,
                generateDeploymentSetup(topology.getNodeTemplates().keySet()));
        deploymentService.deploy(deployment).get();
    }
}
