package alien4cloud.paas.cloudify3.dao;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFuture;

import alien4cloud.paas.cloudify3.model.NodeInstance;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class NodeInstanceDAO extends AbstractDAO {

    public static final String NODE_INSTANCES_PATH = "/node-instances";

    @Override
    protected String getPath() {
        return NODE_INSTANCES_PATH;
    }

    public ListenableFuture<ResponseEntity<NodeInstance[]>> asyncList(String deploymentId) {
        log.info("List node instances for deployment {}", deploymentId);
        return getRestTemplate().getForEntity(getBaseUrl("deployment_id"), NodeInstance[].class, deploymentId);
    }

    @SneakyThrows
    public NodeInstance[] list(String deploymentId) {
        return asyncList(deploymentId).get().getBody();
    }

    public ListenableFuture<ResponseEntity<NodeInstance>> asyncRead(String id) {
        log.info("Read NodeInstance {}", id);
        return getRestTemplate().getForEntity(getSuffixedUrl("/{id}"), NodeInstance.class, id);
    }

    @SneakyThrows
    public NodeInstance read(String id) {
        return asyncRead(id).get().getBody();
    }
}
