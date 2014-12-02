package alien4cloud.paas.cloudify3;

import javax.annotation.Resource;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * @author Minh Khang VU
 */
@Component
public class RestClient {

    @Resource
    private RestTemplate restTemplate;

    public <T> T getObject(String url, Class<T> clazz) {
        return restTemplate.getForObject(url, clazz);
    }
}
