package alien4cloud.paas.cloudify3;

import java.util.concurrent.ExecutionException;

import javax.annotation.Resource;

import org.junit.Before;

import alien4cloud.model.topology.Topology;
import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.cloudify3.dao.BlueprintDAO;
import alien4cloud.paas.cloudify3.dao.DeploymentDAO;
import alien4cloud.paas.cloudify3.model.Blueprint;
import alien4cloud.paas.cloudify3.model.Deployment;
import alien4cloud.paas.cloudify3.service.DeploymentService;
import alien4cloud.paas.cloudify3.util.ApplicationUtil;
import alien4cloud.paas.model.PaaSDeploymentContext;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.paas.plan.TopologyTreeBuilderService;

import com.google.common.util.concurrent.SettableFuture;

public class AbstractDeploymentTest extends AbstractTest {

    @Resource
    private BlueprintDAO blueprintDAO;

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

    @Override
    @Before
    public void before() throws Exception {
        super.before();

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

    protected void launchTest(String appName, String topologyName) throws ExecutionException, InterruptedException {
        final SettableFuture<Object> future = SettableFuture.create();
        cloudifyPaaSProvider.deploy(buildPaaSDeploymentContext(appName, topologyName), new IPaaSCallback<Object>() {
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
