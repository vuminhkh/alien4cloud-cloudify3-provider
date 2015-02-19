package alien4cloud.paas.cloudify3;

import java.util.Date;
import java.util.concurrent.ExecutionException;

import javax.annotation.Resource;

import org.junit.Before;

import alien4cloud.model.topology.Topology;
import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.cloudify3.dao.DeploymentDAO;
import alien4cloud.paas.cloudify3.model.Deployment;
import alien4cloud.paas.cloudify3.service.DeploymentService;
import alien4cloud.paas.cloudify3.service.EventService;
import alien4cloud.paas.cloudify3.util.ApplicationUtil;
import alien4cloud.paas.model.PaaSDeploymentContext;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.paas.plan.TopologyTreeBuilderService;

import com.google.common.util.concurrent.SettableFuture;

public class AbstractDeploymentTest extends AbstractTest {

    @Resource
    private EventService eventService;

    @Resource
    private DeploymentDAO deploymentDAO;

    @Resource(name = "cloudify-deployment-service")
    private DeploymentService deploymentService;

    @Resource
    private ApplicationUtil applicationUtil;

    @Resource
    private TopologyTreeBuilderService topologyTreeBuilderService;

    @Resource
    private CloudifyPaaSProvider cloudifyPaaSProvider;

    private void cleanDeployments() throws Exception {
        Date now = new Date();
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
        // Clean internal events queue
        eventService.getEventsSince(now, Integer.MAX_VALUE);
    }

    @Override
    @Before
    public void before() throws Exception {
        super.before();
        cleanDeployments();
    }

    protected PaaSTopologyDeploymentContext buildPaaSDeploymentContext(String appName, String topologyName) {
        Topology topology = applicationUtil.createAlienApplication(appName, topologyName);
        PaaSTopologyDeploymentContext deploymentContext = new PaaSTopologyDeploymentContext();
        deploymentContext.setDeploymentSetup(generateDeploymentSetup(topology));
        deploymentContext.setPaaSTopology(topologyTreeBuilderService.buildPaaSTopology(topology));
        deploymentContext.setTopology(topology);
        deploymentContext.setDeploymentId(appName);
        deploymentContext.setRecipeId(appName);
        return deploymentContext;
    }

    protected String launchTest(String topologyName) throws ExecutionException, InterruptedException {
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        String deploymentId = stackTraceElements[2].getMethodName();
        final SettableFuture<Object> future = SettableFuture.create();
        cloudifyPaaSProvider.deploy(buildPaaSDeploymentContext(deploymentId, topologyName), new IPaaSCallback<Object>() {

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
        return deploymentId;
    }
}
