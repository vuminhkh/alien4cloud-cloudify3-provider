package alien4cloud.paas.cloudify3.service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.cloudify3.configuration.CloudConfiguration;
import alien4cloud.paas.cloudify3.configuration.CloudConfigurationHolder;
import alien4cloud.paas.cloudify3.configuration.ICloudConfigurationChangeListener;
import alien4cloud.paas.cloudify3.configuration.MappingConfigurationHolder;
import alien4cloud.paas.cloudify3.model.AbstractCloudifyModel;
import alien4cloud.paas.cloudify3.model.Deployment;
import alien4cloud.paas.cloudify3.model.Execution;
import alien4cloud.paas.cloudify3.model.ExecutionStatus;
import alien4cloud.paas.cloudify3.model.Node;
import alien4cloud.paas.cloudify3.model.NodeInstance;
import alien4cloud.paas.cloudify3.model.NodeInstanceStatus;
import alien4cloud.paas.cloudify3.model.Relationship;
import alien4cloud.paas.cloudify3.model.RelationshipInstance;
import alien4cloud.paas.cloudify3.model.Workflow;
import alien4cloud.paas.cloudify3.restclient.DeploymentClient;
import alien4cloud.paas.cloudify3.restclient.ExecutionClient;
import alien4cloud.paas.cloudify3.restclient.NodeClient;
import alien4cloud.paas.cloudify3.restclient.NodeInstanceClient;
import alien4cloud.paas.cloudify3.util.DateUtil;
import alien4cloud.paas.exception.NotSupportedException;
import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.InstanceInformation;
import alien4cloud.paas.model.InstanceStatus;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.tosca.normative.NormativeRelationshipConstants;
import alien4cloud.tosca.normative.ToscaFunctionConstants;
import alien4cloud.utils.MapUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * Handle all deployment status request
 */
@Component("cloudify-status-service")
@Slf4j
public class StatusService {

    private Map<String, DeploymentStatus> statusCache = Maps.newConcurrentMap();

    @Resource
    private ExecutionClient executionDAO;

    @Resource
    private NodeInstanceClient nodeInstanceDAO;

    @Resource
    private NodeClient nodeDAO;

    @Resource
    private DeploymentClient deploymentDAO;

    @Resource
    private CloudConfigurationHolder cloudConfigurationHolder;

    @Resource
    private MappingConfigurationHolder mappingConfigurationHolder;

    @PostConstruct
    public void postConstruct() {
        cloudConfigurationHolder.registerListener(new ICloudConfigurationChangeListener() {
            @Override
            public void onConfigurationChange(CloudConfiguration newConfiguration) throws Exception {
                init();
            }
        });
    }

    private void init() throws Exception {
        Deployment[] deployments = deploymentDAO.list();
        List<String> deploymentPaaSIds = Lists.transform(Arrays.asList(deployments), new Function<Deployment, String>() {

            @Override
            public String apply(Deployment deployment) {
                return deployment.getId();
            }
        });
        for (String deploymentPaaSId : deploymentPaaSIds) {
            DeploymentStatus deploymentStatus;
            Execution[] executions;
            try {
                executions = executionDAO.list(deploymentPaaSId);
            } catch (Exception exception) {
                statusCache.put(deploymentPaaSId, DeploymentStatus.UNDEPLOYED);
                continue;
            }
            if (executions.length == 0) {
                deploymentStatus = DeploymentStatus.UNDEPLOYED;
            } else {
                deploymentStatus = getStatus(deploymentPaaSId, executions);
            }
            statusCache.put(deploymentPaaSId, deploymentStatus);
        }
    }

    private DeploymentStatus getStatus(String deploymentPaaSId, Execution[] executions) {
        Execution lastExecution = null;
        // Get the last install or uninstall execution, to check for status
        for (Execution execution : executions) {
            if (log.isDebugEnabled()) {
                log.debug("Deployment {} has execution {} created at {} for workflow {} in status {}", deploymentPaaSId, execution.getId(),
                        execution.getCreatedAt(), execution.getWorkflowId(), execution.getStatus());
            }
            // Only consider install/uninstall workflow to check for deployment status
            if (Workflow.INSTALL.equals(execution.getWorkflowId()) || Workflow.DELETE_DEPLOYMENT_ENVIRONMENT.equals(execution.getWorkflowId())) {
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
        } else if (Workflow.DELETE_DEPLOYMENT_ENVIRONMENT.equals(lastExecution.getWorkflowId())) {
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

    public DeploymentStatus getStatus(String deploymentPaaSId) {
        if (!statusCache.containsKey(deploymentPaaSId)) {
            return DeploymentStatus.UNDEPLOYED;
        } else {
            return statusCache.get(deploymentPaaSId);
        }
    }

    public void getStatus(String deploymentPaaSId, IPaaSCallback<DeploymentStatus> callback) {
        callback.onSuccess(getStatus(deploymentPaaSId));
    }

    public void getInstancesInformation(final PaaSTopologyDeploymentContext deploymentContext,
                                        final IPaaSCallback<Map<String, Map<String, InstanceInformation>>> callback) {
        if (!statusCache.containsKey(deploymentContext.getDeploymentPaaSId())) {
            callback.onSuccess(Maps.<String, Map<String, InstanceInformation>>newHashMap());
            return;
        }
        ListenableFuture<NodeInstance[]> instancesFuture = nodeInstanceDAO.asyncList(deploymentContext.getDeploymentPaaSId());
        ListenableFuture<Node[]> nodesFuture = nodeDAO.asyncList(deploymentContext.getDeploymentPaaSId(), null);
        ListenableFuture<List<AbstractCloudifyModel[]>> combinedFutures = Futures.allAsList(instancesFuture, nodesFuture);
        Futures.addCallback(combinedFutures, new FutureCallback<List<AbstractCloudifyModel[]>>() {
            @Override
            public void onSuccess(List<AbstractCloudifyModel[]> nodeAndNodeInstances) {
                NodeInstance[] instances = (NodeInstance[]) nodeAndNodeInstances.get(0);
                Node[] nodes = (Node[]) nodeAndNodeInstances.get(1);
                Map<String, Node> nodeMap = Maps.newHashMap();
                for (Node node : nodes) {
                    nodeMap.put(node.getId(), node);
                }

                Map<String, NodeInstance> nodeInstanceMap = Maps.newHashMap();
                for (NodeInstance instance : instances) {
                    nodeInstanceMap.put(instance.getId(), instance);
                }

                Map<String, Map<String, InstanceInformation>> information = Maps.newHashMap();
                for (NodeInstance instance : instances) {
                    NodeTemplate nodeTemplate = deploymentContext.getDeploymentTopology().getNodeTemplates().get(instance.getNodeId());
                    if (nodeTemplate == null) {
                        // Sometimes we have generated instance that do not exist in alien topology
                        continue;
                    }
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
                    Map<String, String> runtimeProperties = null;
                    try {
                        runtimeProperties = MapUtil.toString(instance.getRuntimeProperties());
                    } catch (JsonProcessingException e) {
                        log.error("Unable to stringify runtime properties", e);
                    }
                    instanceInformation.setRuntimeProperties(runtimeProperties);
                    Node node = nodeMap.get(instance.getNodeId());
                    if (node != null && runtimeProperties != null) {
                        instanceInformation.setAttributes(getAttributes(node, instance, nodeMap, nodeInstanceMap));
                    }
                    nodeInformation.put(instanceId, instanceInformation);
                }
                String floatingIpPrefix = mappingConfigurationHolder.getMappingConfiguration().getGeneratedNodePrefix() + "_floating_ip_";
                for (NodeInstance instance : instances) {
                    if (instance.getId().startsWith(floatingIpPrefix)) {
                        // It's a floating ip then must fill the compute with public ip address
                        String computeNodeId = instance.getNodeId().substring(floatingIpPrefix.length());
                        Map<String, InstanceInformation> computeNodeInformation = information.get(computeNodeId);
                        if (MapUtils.isNotEmpty(computeNodeInformation)) {
                            InstanceInformation firstComputeInstanceFound = computeNodeInformation.values().iterator().next();
                            firstComputeInstanceFound.getAttributes().put("public_ip_address",
                                    String.valueOf(instance.getRuntimeProperties().get("floating_ip_address")));
                        }
                    }
                }
                callback.onSuccess(information);
            }

            @Override
            public void onFailure(Throwable t) {
                if (log.isDebugEnabled()) {
                    log.debug("Problem retrieving instance information for deployment <" + deploymentContext.getDeploymentPaaSId() + "> ");
                }
                callback.onSuccess(Maps.<String, Map<String, InstanceInformation>>newHashMap());
            }
        });
    }

    private Map<String, Map<String, Object>> getAttributesMappingConfiguration(Map<String, Object> properties) {
        Map<String, Map<String, Object>> mappingConfigurations = Maps.newHashMap();
        for (Map.Entry<String, Object> propertyEntry : properties.entrySet()) {
            if (propertyEntry.getKey().startsWith("_a4c_att_")) {
                mappingConfigurations.put(propertyEntry.getKey().substring("_a4c_att_".length()), (Map<String, Object>) propertyEntry.getValue());
            }
        }
        return mappingConfigurations;
    }

    private RelationshipInstance getRelationshipInstance(NodeInstance instance, Relationship relationship) {
        for (RelationshipInstance relationshipInstance : instance.getRelationships()) {
            if (relationshipInstance.getTargetName().equals(relationship.getTargetId())) {
                return relationshipInstance;
            }
        }
        return null;
    }

    private Map<String, String> getAttributes(Node node, NodeInstance instance, Map<String, Node> nodeMap, Map<String, NodeInstance> nodeInstanceMap) {
        Map<String, Object> properties = node.getProperties();
        Map<String, Object> attributes = Maps.newHashMap();
        Map<String, Map<String, Object>> mappingConfigurations = getAttributesMappingConfiguration(properties);
        if (MapUtils.isNotEmpty(mappingConfigurations)) {
            // If mapping configurations found, only take those configured
            // It's native types component
            for (Map.Entry<String, Map<String, Object>> mappingConfiguration : mappingConfigurations.entrySet()) {
                attributes.put(mappingConfiguration.getKey(), getMappedAttributeValue(mappingConfiguration.getKey(), mappingConfigurations, node, instance, nodeMap, nodeInstanceMap));
            }
        } else {
            // If no mapping found --> take everything as if
            attributes = instance.getRuntimeProperties();
        }
        try {
            return MapUtil.toString(attributes);
        } catch (JsonProcessingException e) {
            log.error("Unable to stringify attributes", e);
            return null;
        }
    }

    private Relationship getRelationshipOfType(Node node, String type) {
        for (Relationship relationship : node.getRelationships()) {
            if (relationship.getTypeHierarchy().contains(type)) {
                return relationship;
            }
        }
        return null;
    }

    private Object getAttributeValue(String attributeName, Map<String, Map<String, Object>> mappingConfigurations, Node node, NodeInstance instance, Map<String, Node> nodeMap, Map<String, NodeInstance> nodeInstanceMap) {
        if (mappingConfigurations.containsKey(attributeName)) {
            return getMappedAttributeValue(attributeName, mappingConfigurations, node, instance, nodeMap, nodeInstanceMap);
        } else {
            Object attributeValue = instance.getRuntimeProperties().get(attributeName);
            if (attributeValue == null) {
                Relationship relationship =
                        getRelationshipOfType(node,
                                mappingConfigurationHolder.getMappingConfiguration().getNormativeTypes().get(NormativeRelationshipConstants.HOSTED_ON)
                        );
                // Redirect to the parent
                if (relationship != null) {
                    Node targetNode = nodeMap.get(relationship.getTargetId());
                    RelationshipInstance relationshipInstance = getRelationshipInstance(instance, relationship);
                    NodeInstance targetInstance = nodeInstanceMap.get(relationshipInstance.getTargetId());
                    return getAttributeValue(attributeName, mappingConfigurations, targetNode, targetInstance, nodeMap, nodeInstanceMap);
                }
            } else {
                return attributeValue;
            }
        }
        return null;
    }

    private Object getMappedAttributeValue(String attributeName, Map<String, Map<String, Object>> mappingConfigurations, Node node, NodeInstance instance, Map<String, Node> nodeMap, Map<String, NodeInstance> nodeInstanceMap) {
        Map<String, Object> mappingConfiguration = mappingConfigurations.get(attributeName);
        if (mappingConfiguration == null) {
            throw new NotSupportedException(attributeName + " is not a mapped attribute");
        }
        Object parametersObject = mappingConfiguration.get("parameters");
        if (!(parametersObject instanceof List)) {
            throw new NotSupportedException("Mapping configuration invalid " + mappingConfiguration);
        }
        List<String> parameters = (List<String>) mappingConfiguration.get("parameters");
        if (parameters.isEmpty()) {
            throw new NotSupportedException("Mapping configuration invalid " + mappingConfiguration + ", parameters are empty");
        }
        String function = (String) mappingConfiguration.get("function");
        if (ToscaFunctionConstants.GET_ATTRIBUTE.equals(function)) {
            String entity = parameters.get(0);
            if (ToscaFunctionConstants.SELF.equals(entity)) {
                if (parameters.size() != 2) {
                    throw new NotSupportedException("Mapping configuration invalid " + mappingConfiguration + ", parameters must be SELF + name of the attribute");
                }
                String fromAttribute = parameters.get(1);
                return getAttributeValue(fromAttribute, mappingConfigurations, node, instance, nodeMap, nodeInstanceMap);
            } else if (ToscaFunctionConstants.TARGET.equals(entity)) {
                if (parameters.size() != 3) {
                    throw new NotSupportedException("Mapping configuration invalid " + mappingConfiguration + ", parameters must be TARGET + relationship type + name of the attribute");
                }
                String relationshipType = parameters.get(1);
                Relationship relationship = getRelationshipOfType(node, relationshipType);
                if (relationship != null) {
                    Node targetNode = nodeMap.get(relationship.getTargetId());
                    RelationshipInstance relationshipInstance = getRelationshipInstance(instance, relationship);
                    NodeInstance targetInstance = nodeInstanceMap.get(relationshipInstance.getTargetId());
                    return getAttributeValue(parameters.get(2), mappingConfigurations, targetNode, targetInstance, nodeMap, nodeInstanceMap);
                }
            } else {
                throw new NotSupportedException("TARGET or SELF are the only entities supported for the moment for attribute mapping");
            }
        } else {
            throw new NotSupportedException("get_attribute is the only one supported for the moment for attribute mapping");
        }
        return null;
    }

    public void registerDeploymentEvent(String deploymentPaaSId, DeploymentStatus deploymentStatus) {
        this.statusCache.put(deploymentPaaSId, deploymentStatus);
    }

    public InstanceStatus getInstanceStatusFromState(String state) {
        switch (state) {
            case NodeInstanceStatus.STARTED:
                return InstanceStatus.SUCCESS;
            case NodeInstanceStatus.UNINITIALIZED:
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
