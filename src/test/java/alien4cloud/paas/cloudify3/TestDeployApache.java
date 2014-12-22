package alien4cloud.paas.cloudify3;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import alien4cloud.paas.cloudify3.util.ApplicationUtil;
import alien4cloud.paas.plan.TopologyTreeBuilderService;

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
        launchTest("testDeployApache", SINGLE_COMPUTE_TOPOLOGY_WITH_APACHE);
    }
}
