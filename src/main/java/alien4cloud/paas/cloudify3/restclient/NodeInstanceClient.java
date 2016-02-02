package alien4cloud.paas.cloudify3.restclient;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import alien4cloud.paas.cloudify3.model.NodeInstance;
import alien4cloud.paas.cloudify3.util.FutureUtil;

import com.google.common.util.concurrent.ListenableFuture;

@Slf4j
@Component
public class NodeInstanceClient extends AbstractClient {

    public static final String NODE_INSTANCES_PATH = "/node-instances";

    @Override
    protected String getPath() {
        return NODE_INSTANCES_PATH;
    }

    public ListenableFuture<NodeInstance[]> asyncList(String deploymentId) {
        if (log.isDebugEnabled()) {
            log.debug("List node instances for deployment {}", deploymentId);
        }
        return FutureUtil.unwrapRestResponse(getForEntity(getBaseUrl("deployment_id"), NodeInstance[].class, deploymentId));
    }

    @SneakyThrows
    public NodeInstance[] list(String deploymentId) {
        return asyncList(deploymentId).get();
    }

    public ListenableFuture<NodeInstance> asyncRead(String id) {
        if (log.isDebugEnabled()) {
            log.debug("Read node instance {}", id);
        }
        return FutureUtil.unwrapRestResponse(getForEntity(getSuffixedUrl("/{id}"), NodeInstance.class, id));
    }

    @SneakyThrows
    public NodeInstance read(String id) {
        return asyncRead(id).get();
    }
}
