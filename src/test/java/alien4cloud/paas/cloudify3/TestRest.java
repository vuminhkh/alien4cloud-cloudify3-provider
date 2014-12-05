package alien4cloud.paas.cloudify3;

import java.util.Map;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.junit.Assert;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import alien4cloud.paas.cloudify3.dao.BlueprintDAO;
import alien4cloud.paas.cloudify3.dao.DeploymentDAO;
import alien4cloud.paas.cloudify3.dao.ExecutionDAO;
import alien4cloud.paas.cloudify3.model.Blueprint;
import alien4cloud.paas.cloudify3.model.Deployment;
import alien4cloud.paas.cloudify3.model.Execution;
import alien4cloud.paas.cloudify3.model.ExecutionStatus;

import com.google.common.collect.Maps;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:test-context.xml")
@Slf4j
public class TestRest {

    public static final String BLUEPRINTS_PATH = "./src/test/resources/blueprints/";

    public static final String BLUEPRINT_ID = "nodecellar";

    public static final String DEPLOYMENT_ID = "deploymentOfNodeCellar";

    public static final String BLUEPRINT_FILE = "singlehost-blueprint.yaml";

    public static final Map<String, Object> DEPLOYMENT_INPUTS = Maps.newHashMap();

    static {
        DEPLOYMENT_INPUTS.put("host_ip", "localhost");
        DEPLOYMENT_INPUTS.put("agent_user", "vagrant");
        DEPLOYMENT_INPUTS.put("agent_private_key_path", "/home/vagrant/.ssh/id_rsa");
    }

    @Resource
    private BlueprintDAO blueprintDAO;

    @Resource
    private DeploymentDAO deploymentDAO;

    @Resource
    private ExecutionDAO executionDAO;

    @Before
    public void before() throws InterruptedException {
        if (deploymentDAO.list().length > 0) {
            deploymentDAO.delete(DEPLOYMENT_ID);
        }
        Thread.sleep(1000L);
        if (blueprintDAO.list().length > 0) {
            blueprintDAO.delete(BLUEPRINT_ID);
        }
        Thread.sleep(1000L);
    }

    @org.junit.Test
    public void testBluePrint() throws InterruptedException {
        Blueprint[] blueprints = blueprintDAO.list();
        Assert.assertEquals(0, blueprints.length);
        blueprintDAO.create(BLUEPRINT_ID, BLUEPRINTS_PATH + BLUEPRINT_ID + "/" + BLUEPRINT_FILE);
        Thread.sleep(1000L);
        blueprints = blueprintDAO.list();
        Assert.assertEquals(1, blueprints.length);
        for (Blueprint blueprint : blueprints) {
            Blueprint readBlueprint = blueprintDAO.read(blueprint.getId());
            Assert.assertEquals(blueprint, readBlueprint);
        }
        blueprintDAO.delete(BLUEPRINT_ID);
        Thread.sleep(1000L);
        blueprints = blueprintDAO.list();
        Assert.assertEquals(0, blueprints.length);
    }

    @org.junit.Test
    public void testDeployment() throws InterruptedException {
        blueprintDAO.create(BLUEPRINT_ID, BLUEPRINTS_PATH + BLUEPRINT_ID + "/" + BLUEPRINT_FILE);
        Deployment[] deployments = deploymentDAO.list();
        Assert.assertEquals(0, deployments.length);
        deploymentDAO.create(DEPLOYMENT_ID, BLUEPRINT_ID, DEPLOYMENT_INPUTS);
        Thread.sleep(1000L);
        deployments = deploymentDAO.list();
        Assert.assertEquals(1, deployments.length);
        for (Deployment deployment : deployments) {
            Deployment readDeployment = deploymentDAO.read(deployment.getId());
            Assert.assertEquals(deployment, readDeployment);
        }
        Thread.sleep(20000L);
        deploymentDAO.delete(DEPLOYMENT_ID);
        Thread.sleep(1000L);
        deployments = deploymentDAO.list();
        Assert.assertEquals(0, deployments.length);
        blueprintDAO.delete(BLUEPRINT_ID);
    }

    @org.junit.Test
    public void testExecution() throws InterruptedException {
        blueprintDAO.create(BLUEPRINT_ID, BLUEPRINTS_PATH + BLUEPRINT_ID + "/" + BLUEPRINT_FILE);
        Deployment deployment = deploymentDAO.create(DEPLOYMENT_ID, BLUEPRINT_ID, DEPLOYMENT_INPUTS);
        while (true) {
            boolean executionRunning = true;
            Execution[] executions = executionDAO.list(DEPLOYMENT_ID);
            for (Execution execution : executions) {
                log.info("Execution {} of workflow {} is in status {}", execution.getId(), execution.getWorkflowId(), execution.getStatus());
                executionRunning = executionRunning && !ExecutionStatus.isTerminated(execution.getStatus());
            }
            if (executionRunning) {
                log.info("Sleep to wait for all execution finish");
                Thread.sleep(2000L);
            } else {
                log.info("All execution has finished");
                break;
            }
        }
        deploymentDAO.delete(DEPLOYMENT_ID);
        Thread.sleep(1000L);
        blueprintDAO.delete(BLUEPRINT_ID);
    }
}
