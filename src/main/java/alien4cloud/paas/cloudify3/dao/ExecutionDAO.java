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

import alien4cloud.paas.cloudify3.model.Execution;

import com.google.common.collect.Maps;

@Component
@Slf4j
public class ExecutionDAO extends AbstractDAO {

    public static final String EXECUTIONS_PATH = "/executions";

    @Override
    protected String getPath() {
        return EXECUTIONS_PATH;
    }

    public ListenableFuture<ResponseEntity<Execution[]>> asyncList(String deploymentId) {
        log.info("List execution");
        if (deploymentId != null && deploymentId.length() > 0) {
            return getRestTemplate().getForEntity(getBaseUrl("deployment_id"), Execution[].class, deploymentId);
        } else {
            return getRestTemplate().getForEntity(getBaseUrl(), Execution[].class);
        }
    }

    @SneakyThrows
    public Execution[] list(String deploymentId) {
        return asyncList(deploymentId).get().getBody();
    }

    public ListenableFuture<ResponseEntity<Execution>> asyncStart(String deploymentId, String workflowId, Map<String, Object> parameters,
            boolean allowCustomParameters, boolean force) {
        log.info("Start execution of workflow {} for deployment {}", workflowId, deploymentId);
        Map<String, Object> request = Maps.newHashMap();
        request.put("deployment_id", deploymentId);
        request.put("workflow_id", workflowId);
        request.put("parameters", parameters);
        request.put("allow_custom_parameters", String.valueOf(allowCustomParameters));
        request.put("force", String.valueOf(force));
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        return getRestTemplate().exchange(getBaseUrl(), HttpMethod.POST, new HttpEntity<>(request, headers), Execution.class);
    }

    @SneakyThrows
    public Execution start(String deploymentId, String workflowId, Map<String, Object> parameters, boolean allowCustomParameters, boolean force) {
        return asyncStart(deploymentId, workflowId, parameters, allowCustomParameters, force).get().getBody();
    }

    public ListenableFuture<ResponseEntity<Execution>> asyncCancel(String id, boolean force) {
        log.info("Cancel execution {}", id);
        Map<String, Object> request = Maps.newHashMap();
        if (force) {
            request.put("action", "force-cancel");
        } else {
            request.put("action", "cancel");
        }
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        return getRestTemplate().exchange(getSuffixedUrl("/{id}"), HttpMethod.POST, new HttpEntity<>(request, headers), Execution.class, id);
    }

    @SneakyThrows
    public Execution cancel(String id, boolean force) {
        return asyncCancel(id, force).get().getBody();
    }

    public ListenableFuture<ResponseEntity<Execution>> asyncRead(String id) {
        log.info("Read execution {}", id);
        return getRestTemplate().getForEntity(getSuffixedUrl("/{id}"), Execution.class, id);
    }

    @SneakyThrows
    public Execution read(String id) {
        return asyncRead(id).get().getBody();
    }
}
