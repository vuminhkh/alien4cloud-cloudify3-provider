package alien4cloud.paas.cloudify3;

import java.util.Arrays;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.cloudify3.util.ApplicationUtil;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.paas.plan.TopologyTreeBuilderService;
import alien4cloud.tosca.container.model.topology.Topology;

import com.google.common.util.concurrent.SettableFuture;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:test-context.xml")
@Slf4j
public class TestDeployApache extends AbstractDeploymentTest {

    @Resource
    private CloudifyPaaSProvider cloudifyPaaSProvider;

    @Resource
    private ApplicationUtil applicationUtil;

    @Resource
    private TopologyTreeBuilderService topologyTreeBuilderService;

    @Test
    public void testDeployApache() throws Exception {
        Topology topology = applicationUtil.createAlienApplication("testDeployApache", SINGLE_COMPUTE_TOPOLOGY_WITH_APACHE);
        PaaSTopologyDeploymentContext deploymentContext = new PaaSTopologyDeploymentContext();
        deploymentContext.setDeploymentSetup(generateDeploymentSetup(Arrays.asList("compute")));
        deploymentContext.setPaaSTopology(topologyTreeBuilderService.buildPaaSTopology(topology));
        deploymentContext.setTopology(topology);
        deploymentContext.setDeploymentId("testDeployApache");
        deploymentContext.setRecipeId("testDeployApache");
        final SettableFuture<Object> future = SettableFuture.create();
        cloudifyPaaSProvider.deploy(deploymentContext, new IPaaSCallback<Object>() {
            @Override
            public void onSuccess(Object data) {
                future.set(data);
            }

            @Override
            public void onFailure(Throwable throwable) {
                future.setException(throwable);
            }
        });
        future.get();
    }
}
