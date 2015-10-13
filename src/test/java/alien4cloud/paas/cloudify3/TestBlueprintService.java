package alien4cloud.paas.cloudify3;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import javax.annotation.Resource;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import alien4cloud.paas.cloudify3.model.Blueprint;
import alien4cloud.paas.cloudify3.restclient.BlueprintClient;
import alien4cloud.paas.cloudify3.service.BlueprintService;
import alien4cloud.paas.cloudify3.service.CloudifyDeploymentBuilderService;
import alien4cloud.paas.cloudify3.util.FileTestUtil;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.utils.FileUtil;
import lombok.SneakyThrows;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:test-context.xml")
public class TestBlueprintService extends AbstractDeploymentTest {

    @Resource
    private BlueprintService blueprintService;

    @Resource
    private BlueprintClient blueprintDAO;

    @Resource
    private CloudifyDeploymentBuilderService cloudifyDeploymentBuilderService;

    private boolean record = true;

    @Override
    @Before
    public void before() throws Exception {
        super.before();
        Thread.sleep(1000L);
        Blueprint[] blueprints = blueprintDAO.list();
        for (Blueprint blueprint : blueprints) {
            blueprintDAO.delete(blueprint.getId());
        }
        Thread.sleep(1000L);
    }

    private interface DeploymentContextVisitor {
        void visitDeploymentContext(PaaSTopologyDeploymentContext context) throws Exception;
    }

    @SneakyThrows
    private Path testGeneratedBlueprintFile(String topology) {
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        return testGeneratedBlueprintFile(topology, topology, stackTraceElements[2].getMethodName(), null);
    }

    @SneakyThrows
    private Path testGeneratedBlueprintFile(String topology, String outputFile, String testName, DeploymentContextVisitor contextVisitor) {
        PaaSTopologyDeploymentContext context = buildPaaSDeploymentContext(testName, topology);
        if (contextVisitor != null) {
            contextVisitor.visitDeploymentContext(context);
        }
        Path generated = blueprintService.generateBlueprint(cloudifyDeploymentBuilderService.buildCloudifyDeployment(context));
        Path generatedDirectory = generated.getParent();
        String recordedDirectory = "src/test/resources/outputs/blueprints/" + outputFile;
        if (record) {
            FileUtil.delete(Paths.get(recordedDirectory));
            FileUtil.copy(generatedDirectory, Paths.get(recordedDirectory), StandardCopyOption.REPLACE_EXISTING);
            // Check if cloudify accept the blueprint
            blueprintDAO.create(topology, generated.toString());
            blueprintDAO.delete(topology);
        } else {
            FileTestUtil.assertFilesAreSame(Paths.get(recordedDirectory), generatedDirectory);
        }
        return generated;
    }

    @Test
    public void testGenerateSingleCompute() {
        testGeneratedBlueprintFile(SINGLE_COMPUTE_TOPOLOGY);
    }

    @Test
    public void testGenerateNetwork() {
        testGeneratedBlueprintFile(NETWORK_TOPOLOGY);
    }

    @Test
    public void testGenerateLamp() {
        // testGeneratedBlueprintFile(LAMP_TOPOLOGY);
        Assert.fail("Fix test");
    }

    @Test
    public void testGenerateBlockStorage() {
        // testGeneratedBlueprintFile(STORAGE_TOPOLOGY);
        Assert.fail("Fix test");
    }

    @Test
    public void testGenerateTomcat() {
        // testGeneratedBlueprintFile(TOMCAT_TOPOLOGY);
        Assert.fail("Fix test");
    }

    @Test
    public void testGenerateArtifactsTest() {
        // testGeneratedBlueprintFile(ARTIFACT_TEST_TOPOLOGY);
        Assert.fail("Fix test");
    }

    @Test
    public void testGenerateOverriddenArtifactsTest() {
        // testGeneratedBlueprintFile(ARTIFACT_TEST_TOPOLOGY, ARTIFACT_TEST_TOPOLOGY + "Overridden", "testGenerateOverridenArtifactsTest",
        // new DeploymentContextVisitor() {
        // @Override
        // public void visitDeploymentContext(PaaSTopologyDeploymentContext context) throws Exception {
        // overrideArtifact(context, "War", "war_file", Paths.get("src/test/resources/data/war-examples/helloWorld.war"));
        // }
        // });
        Assert.fail("Fix test");
    }

    @Test
    public void testGenerateHAGroup() {
        // testGeneratedBlueprintFile(HA_GROUPS_TOPOLOGY);
        Assert.fail("Fix test");
    }
}
