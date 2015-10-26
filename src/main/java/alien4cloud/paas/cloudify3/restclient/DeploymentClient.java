package alien4cloud.paas.cloudify3.restclient;

import java.util.Map;

import alien4cloud.paas.cloudify3.model.Blueprint;
import com.google.common.util.concurrent.AsyncFunction;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

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
        return FutureUtil.unwrapRestResponse(getRestTemplate().getForEntity(getBaseUrl(), Deployment[].class));
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
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        return FutureUtil.unwrapRestResponse(getRestTemplate().exchange(getSuffixedUrl("/{id}"), HttpMethod.PUT, new HttpEntity<>(request, headers),
                Deployment.class, id));
    }

    @SneakyThrows
    public Deployment create(String id, String blueprintId, Map<String, Object> inputs) {
        return asyncCreate(id, blueprintId, inputs).get();
    }

    public ListenableFuture<Deployment> asyncRead(String id) {
        if (log.isDebugEnabled()) {
            log.debug("Read deployment {}", id);
        }
        return FutureUtil.unwrapRestResponse(getRestTemplate().getForEntity(getSuffixedUrl("/{id}"), Deployment.class, id));
    }

    @SneakyThrows
    public Deployment read(String id) {
        return asyncRead(id).get();
    }

    public ListenableFuture<?> asyncDelete(String id) {
        if (log.isDebugEnabled()) {
            log.debug("Delete deployment {}", id);
        }
        return FutureUtil.toGuavaFuture(getRestTemplate().delete(getSuffixedUrl("/{id}"), id));
    }

    @SneakyThrows
    public void delete(String id) {
        asyncDelete(id).get();
    }
}
