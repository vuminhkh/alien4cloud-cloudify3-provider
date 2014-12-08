package alien4cloud.paas.cloudify3.dao;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFuture;

import alien4cloud.paas.cloudify3.model.Node;

@Slf4j
@Component
public class NodeDAO extends AbstractDAO {

    public static final String NODES_PATH = "deployments";

    @Override
    protected String getPath() {
        return NODES_PATH;
    }

    public ListenableFuture<ResponseEntity<Node[]>> asyncList(String deploymentId, String nodeId) {
        log.info("List nodes for deployment {}", deploymentId);
        return getRestTemplate().getForEntity(getBaseUrl("deployment_id", "node_id"), Node[].class, deploymentId, nodeId);
    }

    @SneakyThrows
    public Node[] list(String deploymentId, String nodeId) {
        return asyncList(deploymentId, nodeId).get().getBody();
    }
}
