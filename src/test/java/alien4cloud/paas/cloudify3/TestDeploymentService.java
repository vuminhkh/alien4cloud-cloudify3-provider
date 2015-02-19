package alien4cloud.paas.cloudify3;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.cloudify3.configuration.MappingConfigurationHolder;
import alien4cloud.paas.cloudify3.dao.NodeInstanceDAO;
import alien4cloud.paas.cloudify3.model.NodeInstance;
import alien4cloud.paas.cloudify3.model.NodeInstanceStatus;
import alien4cloud.paas.cloudify3.service.EventService;
import alien4cloud.paas.cloudify3.service.StatusService;
import alien4cloud.paas.cloudify3.util.HttpUtil;
import alien4cloud.paas.model.AbstractMonitorEvent;
import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.InstanceStatus;
import alien4cloud.paas.model.PaaSDeploymentStatusMonitorEvent;
import alien4cloud.paas.model.PaaSInstanceStateMonitorEvent;

import com.google.common.collect.Lists;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:test-context.xml")
@Slf4j
public class TestDeploymentService extends AbstractDeploymentTest {

    @Resource
    private NodeInstanceDAO nodeInstanceDAO;

    @Resource
    private MappingConfigurationHolder mappingConfigurationHolder;

    @Resource
    private HttpUtil httpUtil;

    @Resource
    private EventService eventService;

    @Resource
    private StatusService statusService;

    private void getEvents(Date beginTestTimestamp, List<PaaSDeploymentStatusMonitorEvent> deploymentStatusEvents,
            List<PaaSInstanceStateMonitorEvent> instanceStateMonitorEvents) throws ExecutionException, InterruptedException {
        AbstractMonitorEvent[] events = eventService.getEventsSince(beginTestTimestamp, Integer.MAX_VALUE).get();
        for (AbstractMonitorEvent event : events) {
            if (event instanceof PaaSDeploymentStatusMonitorEvent) {
                deploymentStatusEvents.add((PaaSDeploymentStatusMonitorEvent) event);
            } else {
                instanceStateMonitorEvents.add((PaaSInstanceStateMonitorEvent) event);
            }
        }
    }

    @org.junit.Test
    public void testDeploySingleCompute() throws Exception {
        Date beginTestTimestamp = new Date();
        statusService.getStatus("testDeploySingleCompute", new IPaaSCallback<DeploymentStatus>() {
            @Override
            public void onSuccess(DeploymentStatus deploymentStatus) {
                Assert.assertEquals(DeploymentStatus.UNDEPLOYED, deploymentStatus);
            }

            @Override
            public void onFailure(Throwable throwable) {
                Assert.fail();
            }
        });
        String deploymentId = launchTest(SINGLE_COMPUTE_TOPOLOGY);
        
        // Sleep a little bit so that we are sure that events are all well generated
        Thread.sleep(2000L);
        
        List<PaaSDeploymentStatusMonitorEvent> deploymentStatusEvents = Lists.newArrayList();
        List<PaaSInstanceStateMonitorEvent> instanceStateMonitorEvents = Lists.newArrayList();

        getEvents(beginTestTimestamp, deploymentStatusEvents, instanceStateMonitorEvents);
        getEvents(beginTestTimestamp, deploymentStatusEvents, instanceStateMonitorEvents);

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

        deploymentStatusEvents.clear();
        instanceStateMonitorEvents.clear();
        beginTestTimestamp = new Date();

        // Sleep a little bit so that we are sure that events are all well generated
        Thread.sleep(2000L);
        
        // Retrieve fictive event for instance state runtime properties
        getEvents(beginTestTimestamp, deploymentStatusEvents, instanceStateMonitorEvents);
        Assert.assertEquals(0, deploymentStatusEvents.size());
        Assert.assertEquals(1, instanceStateMonitorEvents.size());
        Assert.assertEquals(NodeInstanceStatus.STARTED, instanceStateMonitorEvents.get(0).getInstanceState());
        Assert.assertNotNull(instanceStateMonitorEvents.get(0).getRuntimeProperties().get("ip"));
        Assert.assertNotNull(instanceStateMonitorEvents.get(0).getAttributes().get("ip_address"));

        statusService.getStatus(deploymentId, new IPaaSCallback<DeploymentStatus>() {
            @Override
            public void onSuccess(DeploymentStatus deploymentStatus) {
                Assert.assertEquals(DeploymentStatus.DEPLOYED, deploymentStatus);
            }

            @Override
            public void onFailure(Throwable throwable) {
                Assert.fail();
            }
        });
    }

    @org.junit.Test
    public void testDeployLamp() throws Exception {
        String deploymentId = launchTest(LAMP_TOPOLOGY);
        NodeInstance[] nodeInstances = nodeInstanceDAO.list(deploymentId);
        String ipServerLamp = null;
        for (NodeInstance nodeInstance : nodeInstances) {
            if ((mappingConfigurationHolder.getMappingConfiguration().getGeneratedNodePrefix() + "_floating_ip_Server").equals(nodeInstance.getNodeId())) {
                ipServerLamp = (String) nodeInstance.getRuntimeProperties().get("floating_ip_address");
            }
        }
        Assert.assertNotNull(ipServerLamp);
        httpUtil.checkUrl("http://" + ipServerLamp + "/wordpress/wp-admin/install.php", 120000L);
    }

    @org.junit.Test
    @Ignore
    public void testDeployBlockStorage() throws Exception {
        launchTest(STORAGE_TOPOLOGY);
    }

    /*
     * Many cloud images are not configured to automatically bring up all network cards that are available. They will usually only have a single network card
     * configured. To correctly set up a host in the cloud with multiple network cards, log on to the machine and bring up the additional interfaces.
     * 
     * On an Ubuntu Image, this usually looks like this:
     * echo $'auto eth1\niface eth1 inet dhcp' | sudo tee /etc/network/interfaces.d/eth1.cfg > /dev/null
     * sudo ifup eth1
     */
    @org.junit.Test
    @Ignore
    public void testDeployNetwork() throws Exception {
        launchTest(NETWORK_TOPOLOGY);
    }
}
