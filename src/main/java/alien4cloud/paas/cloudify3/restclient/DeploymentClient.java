package alien4cloud.paas.cloudify3.restclient;

import java.util.Map;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import alien4cloud.paas.cloudify3.model.Deployment;
import alien4cloud.paas.cloudify3.util.FutureUtil;

import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ListenableFuture;

@Component
@Slf4j
public class DeploymentClient extends AbstractClient {

    public static final String DEPLOYMENTS_PATH = "/deployments";

    @Override
    protected String getPath() {
        return DEPLOYMENTS_PATH;
    }

    public ListenableFuture<Deployment[]> asyncList() {
        if (log.isDebugEnabled()) {
            log.debug("List deployment");
        }
        return FutureUtil.unwrapRestResponse(getForEntity(getBaseUrl(), Deployment[].class));
    }

    @SneakyThrows
    public Deployment[] list() {
        return asyncList().get();
    }

    public ListenableFuture<Deployment> asyncCreate(String id, String blueprintId, Map<String, Object> inputs) {
        if (log.isDebugEnabled()) {
            log.debug("Create deployment {} for blueprint {}", id, blueprintId);
        }
        Map<String, Object> request = Maps.newHashMap();
        request.put("blueprint_id", blueprintId);
        request.put("inputs", inputs);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        return FutureUtil.unwrapRestResponse(exchange(getSuffixedUrl("/{id}"), HttpMethod.PUT, createHttpEntity(request, headers), Deployment.class, id));
    }

    @SneakyThrows
    public Deployment create(String id, String blueprintId, Map<String, Object> inputs) {
        return asyncCreate(id, blueprintId, inputs).get();
    }

    public ListenableFuture<Deployment> asyncRead(String id) {
        if (log.isDebugEnabled()) {
            log.debug("Read deployment {}", id);
        }
        return FutureUtil.unwrapRestResponse(getForEntity(getSuffixedUrl("/{id}"), Deployment.class, id));
    }

    @SneakyThrows
    public Deployment read(String id) {
        return asyncRead(id).get();
    }

    public ListenableFuture<?> asyncDelete(String id) {
        if (log.isDebugEnabled()) {
            log.debug("Delete deployment {}", id);
        }
        return FutureUtil.toGuavaFuture(delete(getSuffixedUrl("/{id}"), id));
    }

    @SneakyThrows
    public void delete(String id) {
        asyncDelete(id).get();
    }
}
