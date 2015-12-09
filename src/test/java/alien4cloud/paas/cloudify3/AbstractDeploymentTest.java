package alien4cloud.paas.cloudify3;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.annotation.Resource;

import org.apache.commons.collections4.MapUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

import alien4cloud.common.AlienConstants;
import alien4cloud.component.repository.ArtifactLocalRepository;
import alien4cloud.component.repository.ArtifactRepositoryConstants;
import alien4cloud.model.components.DeploymentArtifact;
import alien4cloud.model.deployment.DeploymentTopology;
import alien4cloud.model.orchestrators.locations.Location;
import alien4cloud.model.topology.Topology;
import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.cloudify3.model.Deployment;
import alien4cloud.paas.cloudify3.restclient.DeploymentClient;
import alien4cloud.paas.cloudify3.service.DeploymentService;
import alien4cloud.paas.cloudify3.service.EventService;
import alien4cloud.paas.cloudify3.util.ApplicationUtil;
import alien4cloud.paas.cloudify3.util.LocationUtil;
import alien4cloud.paas.model.NodeOperationExecRequest;
import alien4cloud.paas.model.PaaSDeploymentContext;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.paas.plan.TopologyTreeBuilderService;
import alien4cloud.paas.wf.WorkflowsBuilderService;
import alien4cloud.paas.wf.WorkflowsBuilderService.TopologyContext;
import alien4cloud.utils.ReflectionUtil;

import com.google.common.collect.Maps;
import com.google.common.io.Closeables;
import com.google.common.util.concurrent.SettableFuture;

public class AbstractDeploymentTest extends AbstractTest {

    @Resource
    private EventService eventService;

    @Resource
    private DeploymentClient deploymentDAO;

    @Resource(name = "cloudify-deployment-service")
    private DeploymentService deploymentService;

    @Resource
    private ApplicationUtil applicationUtil;

    @Resource
    private TopologyTreeBuilderService topologyTreeBuilderService;

    @Resource
    private CloudifyOrchestrator cloudifyPaaSProvider;

    @Resource
    private ArtifactLocalRepository artifactRepository;

    @Resource
    private WorkflowsBuilderService workflowBuilderService;

    private void cleanDeployments() throws Exception {
        Date now = new Date();
        // Clean deployment
        Deployment[] deployments = deploymentDAO.list();
        if (deployments.length > 0) {
            for (Deployment deployment : deployments) {
                PaaSDeploymentContext context = new PaaSDeploymentContext();
                context.setDeployment(new alien4cloud.model.deployment.Deployment());
                context.getDeployment().setId(deployment.getId());
                context.getDeployment().setOrchestratorDeploymentId(deployment.getId());
                deploymentService.undeploy(context).get();
            }
            Thread.sleep(1000L);
            // Clean internal events queue
            // eventService.getEventsSince(now, Integer.MAX_VALUE).get();
        }
    }

    @Override
    @Before
    public void before() throws Exception {
        super.before();
        if (online) {
            cleanDeployments();
        }
    }

    @After
    public void after() throws Exception {
        // cleanDeployments();
    }

    protected PaaSTopologyDeploymentContext buildPaaSDeploymentContext(String appName, String topologyName) {
        Topology topology = applicationUtil.createAlienApplication(appName, topologyName);
        // init the workflows
        TopologyContext topologyContext = workflowBuilderService.buildTopologyContext(topology);
        workflowBuilderService.initWorkflows(topologyContext);
        DeploymentTopology deploymentTopology = generateDeploymentSetup(topology);
        ReflectionUtil.mergeObject(topology, deploymentTopology, "id");
        PaaSTopologyDeploymentContext deploymentContext = new PaaSTopologyDeploymentContext();
        deploymentContext.setPaaSTopology(topologyTreeBuilderService.buildPaaSTopology(topology));
        deploymentContext.setDeploymentTopology(deploymentTopology);
        alien4cloud.model.deployment.Deployment deployment = new alien4cloud.model.deployment.Deployment();
        deployment.setId(appName);
        deployment.setOrchestratorDeploymentId(appName);
        deploymentContext.setDeployment(deployment);
        Map<String, Location> locationMap = Maps.newHashMap();
        Location location = new Location();
        location.setInfrastructureType(LocationUtil.getType());
        locationMap.put(AlienConstants.GROUP_ALL, location);
        deploymentContext.setLocations(locationMap);
        return deploymentContext;
    }

    protected void launchTest(PaaSTopologyDeploymentContext deploymentContext) throws ExecutionException, InterruptedException {
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

    protected void overrideArtifact(PaaSTopologyDeploymentContext deploymentContext, String nodeName, String artifactId, Path newArtifactContent)
            throws IOException {
        DeploymentArtifact artifact = deploymentContext.getPaaSTopology().getAllNodes().get(nodeName).getNodeTemplate().getArtifacts().get(artifactId);
        if (ArtifactRepositoryConstants.ALIEN_ARTIFACT_REPOSITORY.equals(artifact.getArtifactRepository())) {
            artifactRepository.deleteFile(artifact.getArtifactRef());
        }
        InputStream artifactStream = Files.newInputStream(newArtifactContent);
        try {
            String artifactFileId = artifactRepository.storeFile(artifactStream);
            artifact.setArtifactName(newArtifactContent.getFileName().toString());
            artifact.setArtifactRef(artifactFileId);
            artifact.setArtifactRepository(ArtifactRepositoryConstants.ALIEN_ARTIFACT_REPOSITORY);
        } finally {
            Closeables.close(artifactStream, true);
        }
    }

    protected void executeCustomCommand(PaaSTopologyDeploymentContext context, NodeOperationExecRequest nodeOperationExecRequest) throws ExecutionException,
            InterruptedException {
        final SettableFuture<Map<String, String>> future = SettableFuture.create();
        cloudifyPaaSProvider.executeOperation(context, nodeOperationExecRequest, new IPaaSCallback<Map<String, String>>() {
            @Override
            public void onSuccess(Map<String, String> data) {
                future.set(data);
            }

            @Override
            public void onFailure(Throwable throwable) {
                future.setException(throwable);
            }
        });
        Map<String, String> data = future.get();
        Assert.assertNotNull(data);
        Assert.assertTrue(MapUtils.isNotEmpty(data));
    }

    protected PaaSTopologyDeploymentContext buildPaaSDeploymentContext(String topologyName) {
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        String deploymentId = stackTraceElements[2].getMethodName();
        return buildPaaSDeploymentContext(deploymentId, topologyName);
    }

    protected String launchTest(String topologyName) throws ExecutionException, InterruptedException {
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        String deploymentId = stackTraceElements[2].getMethodName();
        launchTest(buildPaaSDeploymentContext(deploymentId, topologyName));
        return deploymentId;
    }
}
