package alien4cloud.paas.cloudify3;

import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import alien4cloud.paas.cloudify3.model.NodeInstanceStatus;
import alien4cloud.paas.cloudify3.service.DeploymentService;
import alien4cloud.paas.cloudify3.service.EventService;
import alien4cloud.paas.cloudify3.service.StatusService;
import alien4cloud.paas.cloudify3.service.model.AlienDeployment;
import alien4cloud.paas.cloudify3.util.ApplicationUtil;
import alien4cloud.paas.cloudify3.util.DeploymentUtil;
import alien4cloud.paas.model.AbstractMonitorEvent;
import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.InstanceInformation;
import alien4cloud.paas.model.InstanceStatus;
import alien4cloud.paas.model.PaaSDeploymentStatusMonitorEvent;
import alien4cloud.paas.model.PaaSInstanceStateMonitorEvent;
import alien4cloud.tosca.container.model.topology.Topology;

import com.google.common.collect.Lists;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:test-context.xml")
@Slf4j
public class TestDeploySingleCompute extends AbstractTest {

    @Resource(name = "cloudify-deployment-service")
    private DeploymentService deploymentService;

    @Resource
    private StatusService statusService;

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
        AlienDeployment deployment = deploymentUtil.buildAlienDeployment("testDeploySingleCompute", "testDeploySingleCompute", topology,
                generateDeploymentSetup(topology.getNodeTemplates().keySet()));
        deploymentService.deploy(deployment).get();
        log.info("Finished deploying {}", deployment.getDeploymentId());
        Thread.sleep(1000L);
        Assert.assertEquals(DeploymentStatus.DEPLOYED, statusService.getStatus(deployment.getDeploymentId()));
        Map<String, Map<String, InstanceInformation>> instanceInfos = statusService.getInstancesInformation(deployment.getDeploymentId(), topology);
        Assert.assertEquals(1, instanceInfos.size());
        Assert.assertTrue(instanceInfos.containsKey("compute"));
        Map<String, InstanceInformation> computeInfo = instanceInfos.get("compute");
        Assert.assertEquals(1, computeInfo.size());
        InstanceInformation computeInstanceInfo = computeInfo.values().iterator().next();
        Assert.assertEquals(NodeInstanceStatus.STARTED, computeInstanceInfo.getState());
        Assert.assertNotNull(computeInstanceInfo.getRuntimeProperties().get("ip"));
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

        deploymentService.undeploy(deployment.getDeploymentId()).get();
        log.info("Finished un-deploying {}", deployment.getDeploymentId());
    }
}
