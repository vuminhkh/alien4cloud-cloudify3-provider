package alien4cloud.paas.cloudify3;

import lombok.extern.slf4j.Slf4j;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:test-context.xml")
@Slf4j
public class TestDeployMysql extends AbstractDeploymentTest {

    @Test
    public void testDeployMysql() throws Exception {
        launchTest("testDeployMysql", SINGLE_COMPUTE_TOPOLOGY_WITH_MYSQL);
    }
}
