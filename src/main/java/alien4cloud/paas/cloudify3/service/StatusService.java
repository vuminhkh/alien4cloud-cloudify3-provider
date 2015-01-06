package alien4cloud.paas.cloudify3.service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.Topology;
import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.cloudify3.dao.DeploymentDAO;
import alien4cloud.paas.cloudify3.dao.ExecutionDAO;
import alien4cloud.paas.cloudify3.dao.NodeInstanceDAO;
import alien4cloud.paas.cloudify3.model.Deployment;
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

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Handle all deployment status request
 *
 * @author Minh Khang VU
 */
@Component("cloudify-status-service")
@Slf4j
public class StatusService {

    private Map<String, DeploymentStatus> statusCache = Maps.newConcurrentMap();

    @Resource
    private ExecutionDAO executionDAO;

    @Resource
    private NodeInstanceDAO nodeInstanceDAO;

    @Resource
    private DeploymentDAO deploymentDAO;

    public void init() throws Exception {
        Deployment[] deployments = deploymentDAO.list();
        List<String> deploymentIds = Lists.transform(Arrays.asList(deployments), new Function<Deployment, String>() {

            @Override
            public String apply(Deployment deployment) {
                return deployment.getId();
            }
        });
        for (String deploymentId : deploymentIds) {
            DeploymentStatus deploymentStatus;
            Execution[] executions;
            try {
                executions = executionDAO.list(deploymentId);
            } catch (Exception exception) {
                statusCache.put(deploymentId, DeploymentStatus.UNDEPLOYED);
                continue;
            }
            if (executions.length == 0) {
                deploymentStatus = DeploymentStatus.UNDEPLOYED;
            } else {
                deploymentStatus = getStatus(deploymentId, executions);
            }
            statusCache.put(deploymentId, deploymentStatus);
        }
    }

    private DeploymentStatus getStatus(String deploymentId, Execution[] executions) {
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

    private DeploymentStatus getStatusFromCache(String deploymentId) {
        if (!statusCache.containsKey(deploymentId)) {
            return DeploymentStatus.UNDEPLOYED;
        } else {
            return statusCache.get(deploymentId);
        }
    }

    public void getStatus(String deploymentId, IPaaSCallback<DeploymentStatus> callback) {
        callback.onSuccess(getStatusFromCache(deploymentId));
    }

    public void getStatuses(String[] deploymentIds, IPaaSCallback<DeploymentStatus[]> callback) {
        List<DeploymentStatus> deploymentStatuses = Lists.newArrayList();
        for (String deploymentId : deploymentIds) {
            deploymentStatuses.add(getStatusFromCache(deploymentId));
        }
        callback.onSuccess(deploymentStatuses.toArray(new DeploymentStatus[deploymentStatuses.size()]));
    }

    public void getInstancesInformation(final String deploymentId, final Topology topology,
            final IPaaSCallback<Map<String, Map<String, InstanceInformation>>> callback) {
        if (!statusCache.containsKey(deploymentId)) {
            callback.onSuccess(Maps.<String, Map<String, InstanceInformation>> newHashMap());
            return;
        }
        ListenableFuture<NodeInstance[]> instancesFuture = nodeInstanceDAO.asyncList(deploymentId);
        Futures.addCallback(instancesFuture, new FutureCallback<NodeInstance[]>() {
            @Override
            public void onSuccess(NodeInstance[] instances) {
                Map<String, Map<String, InstanceInformation>> information = Maps.newHashMap();
                for (NodeInstance instance : instances) {
                    NodeTemplate nodeTemplate = topology.getNodeTemplates().get(instance.getNodeId());
                    Map<String, InstanceInformation> nodeInformation = information.get(instance.getNodeId());
                    if (nodeInformation == null) {
                        nodeInformation = Maps.newHashMap();
                        information.put(instance.getNodeId(), nodeInformation);
                    }
                    String instanceId = instance.getId();
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
                callback.onSuccess(information);
            }

            @Override
            public void onFailure(Throwable t) {
                if (log.isDebugEnabled()) {
                    log.debug("Problem retrieving instance information for deployment <" + deploymentId + "> ");
                }
                callback.onSuccess(Maps.<String, Map<String, InstanceInformation>> newHashMap());
            }
        });
    }

    public void registerDeploymentEvent(String deploymentId, DeploymentStatus deploymentStatus) {
        this.statusCache.put(deploymentId, deploymentStatus);
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
