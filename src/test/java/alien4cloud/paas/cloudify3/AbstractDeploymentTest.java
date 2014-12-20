package alien4cloud.paas.cloudify3;

import javax.annotation.Resource;

import org.junit.Before;

import alien4cloud.paas.cloudify3.dao.BlueprintDAO;
import alien4cloud.paas.cloudify3.dao.DeploymentDAO;
import alien4cloud.paas.cloudify3.model.Blueprint;
import alien4cloud.paas.cloudify3.model.Deployment;
import alien4cloud.paas.cloudify3.service.DeploymentService;
import alien4cloud.paas.model.PaaSDeploymentContext;

public class AbstractDeploymentTest extends AbstractTest {

    @Resource
    private BlueprintDAO blueprintDAO;

    @Resource
    private DeploymentDAO deploymentDAO;

    @Resource(name = "cloudify-deployment-service")
    private DeploymentService deploymentService;

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
}
