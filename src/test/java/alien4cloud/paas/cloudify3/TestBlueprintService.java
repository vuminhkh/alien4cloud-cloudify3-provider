package alien4cloud.paas.cloudify3;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import javax.annotation.Resource;

import junitx.framework.FileAssert;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import alien4cloud.paas.cloudify3.dao.BlueprintDAO;
import alien4cloud.paas.cloudify3.model.Blueprint;
import alien4cloud.paas.cloudify3.service.BlueprintService;
import alien4cloud.paas.cloudify3.service.CloudifyDeploymentBuilderService;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:test-context.xml")
@Slf4j
public class TestBlueprintService extends AbstractDeploymentTest {

    @Resource
    private BlueprintService blueprintService;

    @Resource
    private BlueprintDAO blueprintDAO;

    @Resource
    private CloudifyDeploymentBuilderService cloudifyDeploymentBuilderService;

    private boolean record = false;

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

    private void checkVolumeScript(Path generated) {
        Assert.assertTrue(Files.exists(generated.getParent().resolve("cfy3_native/volume/fdisk.sh")));
        Assert.assertTrue(Files.exists(generated.getParent().resolve("cfy3_native/volume/mkfs.sh")));
        Assert.assertTrue(Files.exists(generated.getParent().resolve("cfy3_native/volume/mount.sh")));
        Assert.assertTrue(Files.exists(generated.getParent().resolve("cfy3_native/volume/unmount.sh")));
    }

    @SneakyThrows
    private Path testGeneratedBlueprintFile(String topology) {
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        Path generated = blueprintService.generateBlueprint(cloudifyDeploymentBuilderService.buildCloudifyDeployment(buildPaaSDeploymentContext(
                stackTraceElements[2].getMethodName(), topology)));
        String recordedFile = "src/test/resources/outputs/blueprints/" + topology + ".yaml";
        if (record) {
            Files.copy(generated, Paths.get(recordedFile), StandardCopyOption.REPLACE_EXISTING);
        } else {
            FileAssert.assertEquals(new File(recordedFile), generated.toFile());
        }
        // Check if cloudify accept the blueprint
        blueprintDAO.create(topology, generated.toString());
        blueprintDAO.delete(topology);
        return generated;
    }

    @Test
    public void testGenerateSingleCompute() {
        testGeneratedBlueprintFile(SINGLE_COMPUTE_TOPOLOGY);
    }

    @Test
    public void testGenerateLamp() {
        Path generated = testGeneratedBlueprintFile(LAMP_TOPOLOGY);
        checkVolumeScript(generated);
        Assert.assertTrue(Files.exists(generated.getParent().resolve("apache-type/scripts/start_apache.sh")));
        Assert.assertTrue(Files.exists(generated.getParent().resolve("apache-type/scripts/install_apache.sh")));

        Assert.assertTrue(Files.exists(generated.getParent().resolve("mysql-type/scripts/install_mysql.sh")));
        Assert.assertTrue(Files.exists(generated.getParent().resolve("mysql-type/scripts/start_mysql.sh")));

        Assert.assertTrue(Files.exists(generated.getParent().resolve("php-type/scripts/install_php.sh")));
        Assert.assertTrue(Files.exists(generated.getParent().resolve("wordpress-type/scripts/install_wordpress.sh")));
        Assert.assertTrue(Files.exists(generated.getParent().resolve("wordpress-type/scripts/install_php_module.sh")));
        Assert.assertTrue(Files.exists(generated.getParent().resolve("wordpress-type/scripts/config_wordpress_for_mysql.sh")));
        Assert.assertTrue(Files.exists(generated.getParent().resolve("wordpress-type/scripts/config_wordpress.sh")));
    }

    @Test
    public void testGenerateNetwork() {
        testGeneratedBlueprintFile(NETWORK_TOPOLOGY);
    }

    @Test
    public void testGenerateBlockStorage() {
        testGeneratedBlueprintFile(STORAGE_TOPOLOGY);
    }

    @Test
    public void testGenerateDeletableBlockStorage() {
        testGeneratedBlueprintFile(DELETABLE_STORAGE_TOPOLOGY);
    }

    private void validateTomcatArtifacts(Path generated) {
        Assert.assertTrue(Files.exists(generated.getParent().resolve("cfy3_native/deployment_artifacts/download_artifacts.py")));
        Assert.assertTrue(Files.exists(generated.getParent().resolve("tomcat-war-types/scripts/java_install.sh")));
        Assert.assertTrue(Files.exists(generated.getParent().resolve("tomcat-war-types/scripts/tomcat_install.sh")));
        Assert.assertTrue(Files.exists(generated.getParent().resolve("tomcat-war-types/scripts/tomcat_start.sh")));
        Assert.assertTrue(Files.exists(generated.getParent().resolve("tomcat-war-types/scripts/tomcat_stop.sh")));
        Assert.assertTrue(Files.exists(generated.getParent().resolve("tomcat-war-types/warFiles/helloWorld.war")));
    }

    @Test
    public void testGenerateTomcat() {
        Path generated = testGeneratedBlueprintFile(TOMCAT_TOPOLOGY);
        validateTomcatArtifacts(generated);
        Assert.assertTrue(Files.exists(generated.getParent().resolve("tomcat-war-types/scripts/tomcat_install_war.sh")));
    }

    @Test
    public void testGenerateArtifactsTest() {
        Path generated = testGeneratedBlueprintFile(ARTIFACT_TEST_TOPOLOGY);
        validateTomcatArtifacts(generated);
        Assert.assertTrue(Files.exists(generated.getParent().resolve("artifact-test-types/conf/settings.properties")));
        Assert.assertTrue(Files.exists(generated.getParent().resolve("artifact-test-types/scripts/configureProperties.sh")));
        Assert.assertTrue(Files.exists(generated.getParent().resolve("artifact-test-types/scripts/create.sh")));
        Assert.assertTrue(Files.exists(generated.getParent().resolve("artifact-test-types/scripts/tomcat_install_war.sh")));
    }
}
