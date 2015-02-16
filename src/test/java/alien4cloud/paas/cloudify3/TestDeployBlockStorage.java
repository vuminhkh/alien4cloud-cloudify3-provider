package alien4cloud.paas.cloudify3;

import lombok.extern.slf4j.Slf4j;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:test-context.xml")
@Slf4j
public class TestDeployBlockStorage extends AbstractDeploymentTest {

    /*
     * Many cloud images are not configured to automatically bring up all network cards that are available. They will usually only have a single network card
     * configured. To correctly set up a host in the cloud with multiple network cards, log on to the machine and bring up the additional interfaces.
     * 
     * On an Ubuntu Image, this usually looks like this:
     * echo $'auto eth1\niface eth1 inet dhcp' | sudo tee /etc/network/interfaces.d/eth1.cfg > /dev/null
     * sudo ifup eth1
     */
    @Test
    public void testDeployBlockStorage() throws Exception {
        launchTest("testDeployBlockStorage", BLOCK_STORAGE_TOPOLOGY);
    }
}
