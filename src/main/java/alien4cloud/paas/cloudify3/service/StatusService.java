package alien4cloud.paas.cloudify3.service;

import java.util.Map;

import org.springframework.stereotype.Component;

import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.InstanceInformation;
import alien4cloud.tosca.container.model.topology.Topology;

/**
 * Handle all deployment status request
 *
 * @author Minh Khang VU
 */
@Component("cloudify-status-service")
public class StatusService {

    public DeploymentStatus getStatus(String deploymentId) {
        return null;
    }

    public DeploymentStatus[] getStatuses(String[] deploymentIds) {
        return new DeploymentStatus[0];
    }

    public Map<String, Map<Integer, InstanceInformation>> getInstancesInformation(String deploymentId, Topology topology) {
        return null;
    }

}
