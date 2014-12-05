package alien4cloud.paas.cloudify3.dao;

import java.util.Map;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import org.apache.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.concurrent.ListenableFuture;

import alien4cloud.paas.cloudify3.model.Deployment;

import com.google.common.collect.Maps;

@Component
@Slf4j
public class DeploymentDAO extends AbstractDAO {

    public static final String DEPLOYMENTS_PATH = "deployments";

    @Override
    protected String getPath() {
        return DEPLOYMENTS_PATH;
    }

    public ListenableFuture<ResponseEntity<Deployment[]>> asyncList() {
        log.info("List deployment");
        return getRestTemplate().getForEntity(getBaseUrl(), Deployment[].class);
    }

    @SneakyThrows
    public Deployment[] list() {
        return asyncList().get().getBody();
    }

    public ListenableFuture<ResponseEntity<Deployment>> asyncCreate(String id, String blueprintId, Map<String, Object> inputs) {
        log.info("Create deployment {} for blueprint {}", id, blueprintId);
        Map<String, Object> request = Maps.newHashMap();
        request.put("blueprint_id", blueprintId);
        request.put("inputs", inputs);
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        return getRestTemplate().exchange(getSuffixedUrl("/{id}"), HttpMethod.PUT, new HttpEntity<>(request, headers), Deployment.class, id);
    }

    @SneakyThrows
    public Deployment create(String id, String blueprintId, Map<String, Object> inputs) {
        return asyncCreate(id, blueprintId, inputs).get().getBody();
    }

    public ListenableFuture<ResponseEntity<Deployment>> asyncRead(String id) {
        log.info("Read deployment {}", id);
        return getRestTemplate().getForEntity(getSuffixedUrl("/{id}"), Deployment.class, id);
    }

    @SneakyThrows
    public Deployment read(String id) {
        return asyncRead(id).get().getBody();
    }

    public ListenableFuture<?> asyncDelete(String id) {
        log.info("Delete deployment {}", id);
        return getRestTemplate().delete(getSuffixedUrl("/{id}"), id);
    }

    @SneakyThrows
    public void delete(String id) {
        asyncDelete(id).get();
    }
}
