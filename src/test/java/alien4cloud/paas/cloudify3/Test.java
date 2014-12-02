package alien4cloud.paas.cloudify3;

import javax.annotation.Resource;

import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:test-context.xml")
public class Test {

    @Resource
    private RestClient restClient;

    @org.junit.Test
    public void test() {
        System.out.println(restClient.getObject("http://11.0.0.7/backend/", String.class));
    }
}
