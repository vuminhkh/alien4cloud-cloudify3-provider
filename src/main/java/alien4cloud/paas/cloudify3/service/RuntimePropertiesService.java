package alien4cloud.paas.cloudify3.service;

import java.util.Map;

import javax.annotation.Resource;

import org.springframework.stereotype.Component;

import alien4cloud.paas.cloudify3.dao.NodeInstanceDAO;
import alien4cloud.paas.cloudify3.model.NodeInstance;

import com.google.common.base.Function;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * This service can be used to retrieve runtime properties of running instances from a deployment
 *
 * @author Minh Khang VU
 */
@Component
public class RuntimePropertiesService {

    @Resource
    private NodeInstanceDAO nodeInstanceDAO;

    public ListenableFuture<Map<String, Object>> evaluate(String deploymentId, final String nodeName, final String attributeName) {
        ListenableFuture<NodeInstance[]> futureNodeInstance = nodeInstanceDAO.asyncList(deploymentId);
        Function<NodeInstance[], Map<String, Object>> adapter = new Function<NodeInstance[], Map<String, Object>>() {
            @Override
            public Map<String, Object> apply(NodeInstance[] nodeInstances) {
                Map<String, Object> evalResult = Maps.newHashMap();
                for (NodeInstance nodeInstance : nodeInstances) {
                    if (nodeInstance.getNodeId().equals(nodeName)) {
                        evalResult.put(nodeInstance.getId(), nodeInstance.getRuntimeProperties().get(attributeName));
                    }
                }
                return evalResult;
            }
        };
        return Futures.transform(futureNodeInstance, adapter);
    }
}
