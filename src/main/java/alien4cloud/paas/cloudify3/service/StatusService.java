package alien4cloud.paas.cloudify3.service;

import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import alien4cloud.paas.cloudify3.dao.ExecutionDAO;
import alien4cloud.paas.cloudify3.dao.NodeInstanceDAO;
import alien4cloud.paas.cloudify3.model.Execution;
import alien4cloud.paas.cloudify3.model.ExecutionStatus;
import alien4cloud.paas.cloudify3.model.NodeInstance;
import alien4cloud.paas.cloudify3.model.NodeInstanceStatus;
import alien4cloud.paas.cloudify3.model.Workflow;
import alien4cloud.paas.cloudify3.util.DateUtil;
import alien4cloud.paas.cloudify3.util.MapUtil;
import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.InstanceInformation;
import alien4cloud.paas.model.InstanceStatus;
import alien4cloud.tosca.container.model.topology.NodeTemplate;
import alien4cloud.tosca.container.model.topology.Topology;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Handle all deployment status request
 *
 * @author Minh Khang VU
 */
@Component("cloudify-status-service")
@Slf4j
public class StatusService {

    @Resource
    private ExecutionDAO executionDAO;

    @Resource
    private NodeInstanceDAO nodeInstanceDAO;

    public DeploymentStatus getStatus(String deploymentId) {
        List<Execution> executions;
        try {
            executions = Lists.newArrayList(executionDAO.list(deploymentId));
        } catch (Exception exception) {
            return DeploymentStatus.UNDEPLOYED;
        }
        if (executions.size() == 0) {
            return DeploymentStatus.UNDEPLOYED;
        }
        Execution lastExecution = null;
        // Get the last install or uninstall execution, to check for status
        for (Execution execution : executions) {
            if (log.isDebugEnabled()) {
                log.debug("Deployment {} has execution {} created at {} for workflow {} in status {}", deploymentId, execution.getId(),
                        execution.getCreatedAt(), execution.getWorkflowId(), execution.getStatus());
            }
            // Only consider install/uninstall workflow to check for deployment status
            if (Workflow.INSTALL.equals(execution.getWorkflowId()) || Workflow.UNINSTALL.equals(execution.getWorkflowId())) {
                if (lastExecution == null) {
                    lastExecution = execution;
                } else if (DateUtil.compare(execution.getCreatedAt(), lastExecution.getCreatedAt()) > 0) {
                    lastExecution = execution;
                }
            }
        }
        // No install and uninstall yet it must be deployment in progress
        if (lastExecution == null) {
            return DeploymentStatus.DEPLOYMENT_IN_PROGRESS;
        } else if (Workflow.INSTALL.equals(lastExecution.getWorkflowId())) {
            if (ExecutionStatus.isTerminatedSuccessfully(lastExecution.getStatus())) {
                return DeploymentStatus.DEPLOYED;
            } else if (ExecutionStatus.isTerminatedWithFailure(lastExecution.getStatus())) {
                return DeploymentStatus.FAILURE;
            } else if (!ExecutionStatus.isTerminated(lastExecution.getStatus())) {
                return DeploymentStatus.DEPLOYMENT_IN_PROGRESS;
            } else {
                return DeploymentStatus.UNKNOWN;
            }
        } else if (Workflow.UNINSTALL.equals(lastExecution.getWorkflowId())) {
            if (ExecutionStatus.isTerminatedSuccessfully(lastExecution.getStatus())) {
                return DeploymentStatus.UNDEPLOYED;
            } else if (ExecutionStatus.isTerminatedWithFailure(lastExecution.getStatus())) {
                return DeploymentStatus.FAILURE;
            } else if (!ExecutionStatus.isTerminated(lastExecution.getStatus())) {
                return DeploymentStatus.UNDEPLOYMENT_IN_PROGRESS;
            } else {
                return DeploymentStatus.UNKNOWN;
            }
        } else {
            // It will never be able to reach here
            return DeploymentStatus.UNKNOWN;
        }
    }

    public DeploymentStatus[] getStatuses(String[] deploymentIds) {
        List<DeploymentStatus> deploymentStatuses = Lists.newArrayList();
        for (String deploymentId : deploymentIds) {
            deploymentStatuses.add(getStatus(deploymentId));
        }
        return deploymentStatuses.toArray(new DeploymentStatus[deploymentStatuses.size()]);
    }

    public Map<String, Map<String, InstanceInformation>> getInstancesInformation(String deploymentId, Topology topology) {
        NodeInstance[] instances = nodeInstanceDAO.list(deploymentId);
        Map<String, Map<String, InstanceInformation>> information = Maps.newHashMap();
        for (NodeInstance instance : instances) {
            NodeTemplate nodeTemplate = topology.getNodeTemplates().get(instance.getNodeId());
            Map<String, InstanceInformation> nodeInformation = information.get(instance.getNodeId());
            if (nodeInformation == null) {
                nodeInformation = Maps.newHashMap();
                information.put(instance.getNodeId(), nodeInformation);
            }
            String instanceId = instance.getNodeId();
            InstanceInformation instanceInformation = new InstanceInformation();
            instanceInformation.setState(instance.getState());
            InstanceStatus instanceStatus = getInstanceStatusFromState(instance.getState());
            if (instanceStatus == null) {
                continue;
            } else {
                instanceInformation.setInstanceStatus(instanceStatus);
            }
            instanceInformation.setRuntimeProperties(MapUtil.toString(instance.getRuntimeProperties()));
            instanceInformation.setProperties(nodeTemplate.getProperties());
            instanceInformation.setAttributes(nodeTemplate.getAttributes());
            nodeInformation.put(instanceId, instanceInformation);
        }
        return information;
    }

    public InstanceStatus getInstanceStatusFromState(String state) {
        switch (state) {
        case NodeInstanceStatus.STARTED:
            return InstanceStatus.SUCCESS;
        case NodeInstanceStatus.STOPPING:
        case NodeInstanceStatus.STOPPED:
        case NodeInstanceStatus.STARTING:
        case NodeInstanceStatus.CONFIGURING:
        case NodeInstanceStatus.CONFIGURED:
        case NodeInstanceStatus.CREATING:
        case NodeInstanceStatus.CREATED:
        case NodeInstanceStatus.DELETING:
            return InstanceStatus.PROCESSING;
        case NodeInstanceStatus.DELETED:
            return null;
        default:
            return InstanceStatus.FAILURE;
        }
    }

}
