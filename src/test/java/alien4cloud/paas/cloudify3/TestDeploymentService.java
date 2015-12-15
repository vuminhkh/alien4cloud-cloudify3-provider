package alien4cloud.paas.cloudify3;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import alien4cloud.paas.cloudify3.util.DeploymentLauncher;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:test-context.xml")
@Slf4j
@Ignore
/**
 * This is not a test, it's more an utility class to rapidly bring up a deployment
 */
public class TestDeploymentService extends AbstractTest {

    @Resource
    private DeploymentLauncher deploymentLauncher;

    @org.junit.Test
    public void deploySingleCompute() throws Exception {
        deploymentLauncher.launch(SINGLE_COMPUTE_TOPOLOGY);
    }

    @org.junit.Test
    public void deployLamp() throws Exception {
        deploymentLauncher.launch(LAMP_TOPOLOGY);
    }

    @org.junit.Test
    public void deployBlockStorage() throws Exception {
        deploymentLauncher.launch(STORAGE_TOPOLOGY);
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
    public void deployNetwork() throws Exception {
        deploymentLauncher.launch(NETWORK_TOPOLOGY);
    }

    @org.junit.Test
    public void deployTomcat() throws Exception {
        deploymentLauncher.launch(TOMCAT_TOPOLOGY);
    }

    @org.junit.Test
    public void deployArtifactTest() throws Exception {
        deploymentLauncher.launch(ARTIFACT_TEST_TOPOLOGY);
    }
}
