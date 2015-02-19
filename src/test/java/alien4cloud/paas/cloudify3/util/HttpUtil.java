package alien4cloud.paas.cloudify3.util;

import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class HttpUtil {

    public void checkUrl(String url, long timeout) {
        long before = System.currentTimeMillis();
        CloseableHttpClient httpClient = HttpClients.custom().build();
        while (true) {
            try {
                HttpGet httpGet = new HttpGet(url);
                CloseableHttpResponse response = httpClient.execute(httpGet);
                try {
                    if (log.isDebugEnabled()) {
                        log.debug("Status code " + response.getStatusLine().getStatusCode());
                    }
                    Assert.assertTrue(response.getStatusLine().getStatusCode() >= 200 && response.getStatusLine().getStatusCode() < 300);
                    if (log.isDebugEnabled()) {
                        log.debug(EntityUtils.toString(response.getEntity()));
                    }
                    return;
                } finally {
                    response.close();
                }
            } catch (IOException e) {
                if (System.currentTimeMillis() - before > timeout) {
                    Assert.fail("Test timeout");
                }
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException e1) {
                }
            }
        }
    }
}
