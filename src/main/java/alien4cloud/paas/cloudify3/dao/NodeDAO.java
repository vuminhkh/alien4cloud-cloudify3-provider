package alien4cloud.paas.cloudify3.dao;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import alien4cloud.paas.cloudify3.model.Node;
import alien4cloud.paas.cloudify3.util.FutureUtil;

import com.google.common.util.concurrent.ListenableFuture;

@Slf4j
@Component
public class NodeDAO extends AbstractDAO {

    public static final String NODES_PATH = "/nodes";

    @Override
    protected String getPath() {
        return NODES_PATH;
    }

    public ListenableFuture<Node[]> asyncList(String deploymentId, String nodeId) {
        log.info("List nodes for deployment {}", deploymentId);
        if (deploymentId == null || deploymentId.isEmpty()) {
            throw new IllegalArgumentException("Deployment id must not be null or empty");
        }
        if (nodeId != null) {
            return FutureUtil.unwrapRestResponse(getRestTemplate().getForEntity(getBaseUrl("deployment_id", "node_id"), Node[].class, deploymentId, nodeId));
        } else {
            return FutureUtil.unwrapRestResponse(getRestTemplate().getForEntity(getBaseUrl("deployment_id"), Node[].class, deploymentId));
        }
    }

    @SneakyThrows
    public Node[] list(String deploymentId, String nodeId) {
        return asyncList(deploymentId, nodeId).get();
    }
}
