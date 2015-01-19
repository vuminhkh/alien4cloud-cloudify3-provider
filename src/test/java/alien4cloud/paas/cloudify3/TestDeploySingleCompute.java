package alien4cloud.paas.cloudify3;

import java.util.Date;
import java.util.List;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import alien4cloud.model.topology.Topology;
import alien4cloud.paas.cloudify3.model.NodeInstanceStatus;
import alien4cloud.paas.cloudify3.service.DeploymentService;
import alien4cloud.paas.cloudify3.service.EventService;
import alien4cloud.paas.cloudify3.service.model.CloudifyDeployment;
import alien4cloud.paas.cloudify3.util.ApplicationUtil;
import alien4cloud.paas.cloudify3.util.DeploymentUtil;
import alien4cloud.paas.model.AbstractMonitorEvent;
import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.InstanceStatus;
import alien4cloud.paas.model.PaaSDeploymentContext;
import alien4cloud.paas.model.PaaSDeploymentStatusMonitorEvent;
import alien4cloud.paas.model.PaaSInstanceStateMonitorEvent;

import com.google.common.collect.Lists;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:test-context.xml")
@Slf4j
public class TestDeploySingleCompute extends AbstractDeploymentTest {

    @Resource(name = "cloudify-deployment-service")
    private DeploymentService deploymentService;

    @Resource
    private EventService eventService;

    @Resource
    private ApplicationUtil applicationUtil;

    @Resource
    private DeploymentUtil deploymentUtil;

    @Test
    public void testDeploySingleCompute() throws Exception {
        Date beginTestTimestamp = new Date();
        Topology topology = applicationUtil.createAlienApplication("testDeploySingleCompute", SINGLE_COMPUTE_TOPOLOGY);
        CloudifyDeployment deployment = deploymentUtil.buildAlienDeployment("testDeploySingleCompute", "testDeploySingleCompute", topology,
                generateDeploymentSetup(topology));
        deploymentService.deploy(deployment).get();
        log.info("Finished deploying {}", deployment.getDeploymentId());
        Thread.sleep(1000L);
        AbstractMonitorEvent[] events = eventService.getEventsSince(beginTestTimestamp, Integer.MAX_VALUE).get();
        List<PaaSDeploymentStatusMonitorEvent> deploymentStatusEvents = Lists.newArrayList();
        List<PaaSInstanceStateMonitorEvent> instanceStateMonitorEvents = Lists.newArrayList();
        for (AbstractMonitorEvent event : events) {
            if (event instanceof PaaSDeploymentStatusMonitorEvent) {
                deploymentStatusEvents.add((PaaSDeploymentStatusMonitorEvent) event);
            } else {
                instanceStateMonitorEvents.add((PaaSInstanceStateMonitorEvent) event);
            }
        }
        // Check deployment status events
        Assert.assertEquals(2, deploymentStatusEvents.size());
        Assert.assertEquals(DeploymentStatus.DEPLOYMENT_IN_PROGRESS, deploymentStatusEvents.get(0).getDeploymentStatus());
        Assert.assertEquals(DeploymentStatus.DEPLOYED, deploymentStatusEvents.get(1).getDeploymentStatus());
        // Check instance state events
        Assert.assertEquals(2, instanceStateMonitorEvents.size());
        Assert.assertEquals(NodeInstanceStatus.CREATED, instanceStateMonitorEvents.get(0).getInstanceState());
        Assert.assertEquals(NodeInstanceStatus.STARTED, instanceStateMonitorEvents.get(1).getInstanceState());
        Assert.assertEquals(InstanceStatus.PROCESSING, instanceStateMonitorEvents.get(0).getInstanceStatus());
        Assert.assertEquals(InstanceStatus.SUCCESS, instanceStateMonitorEvents.get(1).getInstanceStatus());
        PaaSDeploymentContext context = new PaaSDeploymentContext();
        context.setDeploymentId("testDeploySingleCompute");
        context.setRecipeId("testDeploySingleCompute");
        deploymentService.undeploy(context).get();
        log.info("Finished un-deploying {}", deployment.getDeploymentId());
    }
}
