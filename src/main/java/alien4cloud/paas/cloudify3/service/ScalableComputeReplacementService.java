package alien4cloud.paas.cloudify3.service;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import alien4cloud.deployment.DeploymentContextService;
import alien4cloud.model.common.Tag;
import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.components.ComplexPropertyValue;
import alien4cloud.model.components.FunctionPropertyValue;
import alien4cloud.model.components.IValue;
import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.model.components.Interface;
import alien4cloud.model.components.ListPropertyValue;
import alien4cloud.model.components.Operation;
import alien4cloud.model.components.ScalarPropertyValue;
import alien4cloud.model.orchestrators.locations.Location;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.RelationshipTemplate;
import alien4cloud.paas.cloudify3.blueprint.CustomTags;
import alien4cloud.paas.cloudify3.model.Event;
import alien4cloud.paas.cloudify3.model.EventAlienPersistent;
import alien4cloud.paas.cloudify3.model.Node;
import alien4cloud.paas.cloudify3.restclient.NodeClient;
import alien4cloud.paas.function.FunctionEvaluator;
import alien4cloud.paas.model.AbstractMonitorEvent;
import alien4cloud.paas.model.PaaSInstancePersistentResourceMonitorEvent;
import alien4cloud.paas.model.PaaSInstanceStateMonitorEvent;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.paas.plan.TopologyTreeBuilderService;
import alien4cloud.rest.utils.JsonUtil;
import alien4cloud.tosca.normative.NormativeBlockStorageConstants;
import alien4cloud.tosca.normative.NormativeRelationshipConstants;
import alien4cloud.tosca.normative.ToscaFunctionConstants;
import alien4cloud.utils.TagUtil;
import alien4cloud.utils.TypeMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * In charge of modifying the topology in order to manage the "scaling with attached resources" workaround.
 * <p>
 * If the topology contains a Compute wired to a BlockStorage, and if this compute has a maxInstance > 1, then:
 * <ul>
 * <li>replace the compute by a ScalableCompute.
 * <li>remove the wired BlockStorage(s).
 * <li>transfer the BlockStorage properties to the ScalableCompute.
 * <li>change the target of any relationship that targets the BlockStorage to the ScalableCompute.
 * <li>TODO: change any get_attribute that that targets the BlockStorage (even in a concat ?).
 * </ul>
 *
 * This code is deletable and should be removed as soon as CFY is able to manage 1-1 relationships.
 */
@Deprecated
@Component
@Slf4j
public class ScalableComputeReplacementService {

    private static final String DELETABLE_VOLUME_TYPE = "alien.cloudify.openstack.nodes.DeletableVolume";
    private static final String PUBLIC_NETWORK_TYPE = "alien.nodes.openstack.PublicNetwork";
    private static final String DELETABLE_PROPERTY = "deletable";
    private static final String SCALABLE_COMPUTE_TYPE = "alien.nodes.openstack.ScalableCompute";
    private static final String COMPUTE_TYPE = "alien.nodes.openstack.Compute";
    private static final String SCALABLE_COMPUTE_VOLUMES_PROPERTY = "volumes";
    private static final String SCALABLE_COMPUTE_FLOATING_IPS_PROPERTY = "floatingips";
    private static final String SUBSTITUTE_FOR_PROPERTY = "_a4c_substitute_for";
    private static final String USE_EXTERNAL_RESOURCE_PROPERTY = "use_external_resource";
    private static final String RESOURCE_NAME_PROPERTY = "resource_name";

    @Inject
    private TopologyTreeBuilderService topologyTreeBuilderService;
    @Inject
    private NodeClient nodeClient;
    @Inject
    private DeploymentContextService deploymentContextService;

    public PaaSTopologyDeploymentContext transformTopology(PaaSTopologyDeploymentContext deploymentContext) {
        // any type that is modified is cached in this map in order to be reused later while regenerating the deployment ctx
        // actually we known that we have only 1 location
        Location location = deploymentContext.getLocations().entrySet().iterator().next().getValue();
        if (!location.getInfrastructureType().equals("openstack")) {
            // for the moment, only the openstack location support is managed
            return deploymentContext;
        }
        Set<PaaSNodeTemplate> computesToReplaceSet = getComputesToReplaceSet(deploymentContext);
        if (computesToReplaceSet.isEmpty()) {
            // nothing to do concerning this topology
            return deploymentContext;
        }
        List<NodeTemplate> nodesToAdd = Lists.newArrayList();
        for (PaaSNodeTemplate computeNode : deploymentContext.getPaaSTopology().getComputes()) {
            removeComputeResources(deploymentContext, computeNode);
        }
        TypeMap cache = new TypeMap();
        // When there's one scalable compute, we perform the transformation on every nodes or else it'll bug
        for (PaaSNodeTemplate computeNode : deploymentContext.getPaaSTopology().getComputes()) {
            nodesToAdd.addAll(replaceComputeResources(deploymentContext, computeNode, cache));
        }
        for (NodeTemplate fictiveComponent : nodesToAdd) {
            deploymentContext.getDeploymentTopology().getNodeTemplates().put(fictiveComponent.getName(), fictiveComponent);
        }
        // generate the new PaaSTopologyDeploymentContext from the modified topology
        return deploymentContextService.buildTopologyDeploymentContext(deploymentContext.getDeployment(), deploymentContext.getLocations(),
                deploymentContext.getDeploymentTopology(), cache);
    }

    /**
     * Find the compute nodes that need to be replaced.
     */
    private Set<PaaSNodeTemplate> getComputesToReplaceSet(PaaSTopologyDeploymentContext deploymentContext) {
        Set<PaaSNodeTemplate> computesToReplaceSet = Sets.newHashSet();
        List<PaaSNodeTemplate> computes = deploymentContext.getPaaSTopology().getComputes();
        for (PaaSNodeTemplate compute : computes) {
            // we substitute only if the compute is of type alien.nodes.openstack.Compute
            // but not if the type is alien.nodes.openstack.WindowsCompute (that inherits from alien.nodes.openstack.Compute)
            if (compute.getScalingPolicy() != null && compute.getScalingPolicy().getMaxInstances() > 1
                    && compute.getIndexedToscaElement().getElementId().equals(COMPUTE_TYPE)) {
                if (compute.getStorageNodes() != null && !compute.getStorageNodes().isEmpty()) {
                    // the compute has volumes, we have to manage it
                    computesToReplaceSet.add(compute);
                }
                // detect if compute has public network
                if (!getPublicNetworkPaaSNodeTemplate(compute).isEmpty()) {
                    computesToReplaceSet.add(compute);
                }
            }
        }
        return computesToReplaceSet;
    }

    private void removeComputeResources(PaaSTopologyDeploymentContext deploymentContext, PaaSNodeTemplate compute) {
        List<PaaSNodeTemplate> storageNodes = compute.getStorageNodes();
        if (CollectionUtils.isNotEmpty(storageNodes)) {
            for (PaaSNodeTemplate storageNode : storageNodes) {
                removeNode(deploymentContext, compute, storageNode);
            }
        }
        // manage public networks
        Set<PaaSNodeTemplate> publicNetworkPaaSNodeTemplates = getPublicNetworkPaaSNodeTemplate(compute);
        if (!publicNetworkPaaSNodeTemplates.isEmpty()) {
            for (PaaSNodeTemplate publicNetworkPaaSNodeTemplate : publicNetworkPaaSNodeTemplates) {
                removeNode(deploymentContext, compute, publicNetworkPaaSNodeTemplate);
            }
        }
    }

    /**
     * For this particular compute:
     * <ul>
     * <li>substitute the compute using a ScalableCompute
     * <li>remove the resources related to compute (volumes, floating ips).
     * </ul>
     */
    private List<NodeTemplate> replaceComputeResources(PaaSTopologyDeploymentContext deploymentContext, PaaSNodeTemplate compute, TypeMap cache) {
        NodeTemplate computeNodeTemplate = deploymentContext.getDeploymentTopology().getNodeTemplates().get(compute.getId());
        // actually, substituting the compute just means change it's type
        // and replace the related indexedNodeType
        computeNodeTemplate.setType(SCALABLE_COMPUTE_TYPE);
        compute.setIndexedToscaElement(topologyTreeBuilderService.getToscaType(SCALABLE_COMPUTE_TYPE, cache, deploymentContext.getDeploymentTopology()
                .getDependencies(), IndexedNodeType.class));
        if (computeNodeTemplate.getProperties() == null) {
            Map<String, AbstractPropertyValue> properties = Maps.newHashMap();
            computeNodeTemplate.setProperties(properties);
        }
        List<NodeTemplate> nodesToAdd = Lists.newArrayList();
        // manage volumes
        List<PaaSNodeTemplate> storageNodes = compute.getStorageNodes();
        if (CollectionUtils.isNotEmpty(storageNodes)) {
            computeNodeTemplate.getProperties().put(SCALABLE_COMPUTE_VOLUMES_PROPERTY, new ListPropertyValue(Lists.newArrayList()));
            for (PaaSNodeTemplate storageNode : storageNodes) {
                nodesToAdd.addAll(manageStorageNode(deploymentContext, computeNodeTemplate, compute, storageNode));
            }
        }
        // manage public networks
        Set<PaaSNodeTemplate> publicNetworkPaaSNodeTemplates = getPublicNetworkPaaSNodeTemplate(compute);
        if (!publicNetworkPaaSNodeTemplates.isEmpty()) {
            computeNodeTemplate.getProperties().put(SCALABLE_COMPUTE_FLOATING_IPS_PROPERTY, new ListPropertyValue(Lists.newArrayList()));
            for (PaaSNodeTemplate publicNetworkPaaSNodeTemplate : publicNetworkPaaSNodeTemplates) {
                nodesToAdd.addAll(manageNetworkNode(deploymentContext, computeNodeTemplate, compute, publicNetworkPaaSNodeTemplate));
            }
        }
        return nodesToAdd;
    }

    private Set<PaaSNodeTemplate> getPublicNetworkPaaSNodeTemplate(PaaSNodeTemplate compute) {
        Set<PaaSNodeTemplate> result = Sets.newHashSet();
        if (CollectionUtils.isNotEmpty(compute.getNetworkNodes())) {
            for (PaaSNodeTemplate networkPaaSNodeTemplate : compute.getNetworkNodes()) {
                if (PUBLIC_NETWORK_TYPE.equals(networkPaaSNodeTemplate.getNodeTemplate().getType())) {
                    result.add(networkPaaSNodeTemplate);
                }
            }
        }
        return result;
    }

    private void addSubstituteForPropertyValue(NodeTemplate computeNodeTemplate, String substitutedNodeId) {
        AbstractPropertyValue value = computeNodeTemplate.getProperties().get(SUBSTITUTE_FOR_PROPERTY);
        ListPropertyValue valueAsList;
        if (value == null) {
            valueAsList = new ListPropertyValue(Lists.newArrayList());
            computeNodeTemplate.getProperties().put(SUBSTITUTE_FOR_PROPERTY, valueAsList);
        } else {
            valueAsList = (ListPropertyValue) value;
        }
        valueAsList.getValue().add(new ScalarPropertyValue(substitutedNodeId));
    }

    private List<NodeTemplate> manageStorageNode(PaaSTopologyDeploymentContext deploymentContext, NodeTemplate computeNodeTemplate,
            PaaSNodeTemplate computeNode, PaaSNodeTemplate storageNode) {
        // first of all we remove the volume from the topology
        addSubstituteForPropertyValue(computeNodeTemplate, storageNode.getId());
        // transfer the properties of the storage node to the scalable compute node
        ComplexPropertyValue embeddedVolumeProperty = buildAndFeedComplexProperty(computeNodeTemplate, storageNode.getNodeTemplate(),
                SCALABLE_COMPUTE_VOLUMES_PROPERTY);

        // add resource_name
        addProperty(embeddedVolumeProperty.getValue(), RESOURCE_NAME_PROPERTY, new ScalarPropertyValue(storageNode.getId()));

        // add deletable property
        addProperty(embeddedVolumeProperty.getValue(), DELETABLE_PROPERTY,
                new ScalarPropertyValue(Boolean.valueOf(Objects.equals(storageNode.getNodeTemplate().getType(), DELETABLE_VOLUME_TYPE)).toString()));

        // add "use_external_resource" property
        ScalarPropertyValue volumeIdScalar = (ScalarPropertyValue) embeddedVolumeProperty.getValue().get(NormativeBlockStorageConstants.VOLUME_ID);
        String volumeId = FunctionEvaluator.getScalarValue(volumeIdScalar);
        addProperty(embeddedVolumeProperty.getValue(), USE_EXTERNAL_RESOURCE_PROPERTY, new ScalarPropertyValue(Boolean
                .valueOf(StringUtils.isNotBlank(volumeId)).toString()));

        // transfer persistent resources tag if needed
        transferPersistentResourcesTag(computeNode, storageNode, SCALABLE_COMPUTE_VOLUMES_PROPERTY);
        // change all relationships that target the storage to make them target the compute
        return transferRelationships(deploymentContext, computeNode, storageNode, SCALABLE_COMPUTE_VOLUMES_PROPERTY);
    }

    private void transferPersistentResourcesTag(PaaSNodeTemplate computeNode, PaaSNodeTemplate oldNode, String mainPropertyListName) {
        Map<String, String> oldNodePersistentResourceConf = loadPersistentResourceConf(oldNode.getIndexedToscaElement());
        if (MapUtils.isNotEmpty(oldNodePersistentResourceConf)) {
            Map<String, String> computePersistentResourceConf = loadPersistentResourceConf(computeNode.getIndexedToscaElement());
            for (Entry<String, String> entry : oldNodePersistentResourceConf.entrySet()) {
                String persistentResourcePath = formatPersistentResourcePath(entry.getKey(), oldNode, mainPropertyListName);
                computePersistentResourceConf.put(persistentResourcePath, entry.getValue());
            }
            replacePersistentResourceTag(computeNode.getIndexedToscaElement(), computePersistentResourceConf);
        }
    }

    private boolean replacePersistentResourceTag(IndexedNodeType indexedToscaElement, Map<String, String> computePersistentResourceConf) {
        List<Tag> tags = indexedToscaElement.getTags();
        if (tags == null) {
            tags = Lists.newArrayList();
            indexedToscaElement.setTags(tags);
        }
        Tag persistentResourceTag = TagUtil.getTagByName(indexedToscaElement.getTags(), CustomTags.PERSISTENT_RESOURCE_TAG);
        if (persistentResourceTag == null) {
            persistentResourceTag = new Tag();
            persistentResourceTag.setName(CustomTags.PERSISTENT_RESOURCE_TAG);
        }

        try {
            persistentResourceTag.setValue(new ObjectMapper().writeValueAsString(computePersistentResourceConf));
            tags.add(persistentResourceTag);
            return true;
        } catch (JsonProcessingException e) {
            log.error("Failed to write tag <" + CustomTags.PERSISTENT_RESOURCE_TAG + "> value <" + computePersistentResourceConf + "> for tosca element "
                    + indexedToscaElement.getElementId() + ", will be ignored", e);
        }

        return false;
    }

    private String formatPersistentResourcePath(String persistentResourceId, PaaSNodeTemplate oldNode, String mainPropertyListName) {
        // mainPropertyListName.nodeName.persistentResourceId
        return mainPropertyListName + "." + oldNode.getId() + "." + persistentResourceId;
    }

    private Map<String, String> loadPersistentResourceConf(IndexedNodeType nodeType) {
        String persistentTagValue = TagUtil.getTagValue(nodeType.getTags(), CustomTags.PERSISTENT_RESOURCE_TAG);
        Map<String, String> persistentResourceConf = Maps.newHashMap();
        if (StringUtils.isNotBlank(persistentTagValue)) {
            try {
                persistentResourceConf = JsonUtil.toMap(persistentTagValue, String.class, String.class);
            } catch (IOException e) {
                log.error("Failed to load tag <" + CustomTags.PERSISTENT_RESOURCE_TAG + "> for tosca element " + nodeType.getElementId() + ", will be ignored",
                        e);
            }
        }
        return persistentResourceConf;
    }

    private List<NodeTemplate> manageNetworkNode(PaaSTopologyDeploymentContext deploymentContext, NodeTemplate computeNodeTemplate,
            PaaSNodeTemplate computeNode, PaaSNodeTemplate networkNode) {
        addSubstituteForPropertyValue(computeNodeTemplate, networkNode.getId());
        // transfer the properties of the storage node to the scalable compute node
        ComplexPropertyValue embededFloatingProperty = buildAndFeedComplexProperty(computeNodeTemplate, networkNode.getNodeTemplate(),
                SCALABLE_COMPUTE_FLOATING_IPS_PROPERTY);

        // add resource_name
        addProperty(embededFloatingProperty.getValue(), RESOURCE_NAME_PROPERTY, new ScalarPropertyValue(networkNode.getId()));
        // TODO: manage existing floating ip
        embededFloatingProperty.getValue().put(USE_EXTERNAL_RESOURCE_PROPERTY, new ScalarPropertyValue(Boolean.FALSE.toString()));
        // change all relationships that target the network to make them target the compute
        return transferRelationships(deploymentContext, computeNode, networkNode, SCALABLE_COMPUTE_FLOATING_IPS_PROPERTY);
    }

    private ComplexPropertyValue buildAndFeedComplexProperty(NodeTemplate computeNodeTemplate, NodeTemplate removedNodeTemplate, String mainPropertyListName) {
        // bellow the list property for the ScalableCompute
        ListPropertyValue listProperty = (ListPropertyValue) computeNodeTemplate.getProperties().get(mainPropertyListName);
        // build a complex object. for exemple 'alien.data.openstack.EmbededVolumeProperties' object
        ComplexPropertyValue embededProperty = new ComplexPropertyValue();
        // and add it to the list
        listProperty.getValue().add(embededProperty);
        // feed it with all the entries of old node properties
        Map<String, AbstractPropertyValue> oldNodeProperties = removedNodeTemplate.getProperties();
        Map<String, Object> embededPropertyValue = Maps.newHashMap();
        if (oldNodeProperties != null) {
            for (Entry<String, AbstractPropertyValue> e : oldNodeProperties.entrySet()) {
                embededPropertyValue.put(e.getKey(), e.getValue());
            }
        }
        embededProperty.setValue(embededPropertyValue);
        return embededProperty;
    }

    private NodeTemplate createFictiveComponentAndRedirectRelationship(String id, String hostName, RelationshipTemplate redirectedRelationship) {
        NodeTemplate nodeTemplate = new NodeTemplate();
        nodeTemplate.setType("tosca.nodes.SoftwareComponent");
        nodeTemplate.setName(id);
        nodeTemplate.setInterfaces(Maps.<String, Interface> newHashMap());
        RelationshipTemplate hostedOn = new RelationshipTemplate();
        hostedOn.setType(NormativeRelationshipConstants.HOSTED_ON);
        hostedOn.setTarget(hostName);
        hostedOn.setInterfaces(Maps.<String, Interface> newHashMap());
        Map<String, RelationshipTemplate> relationships = Maps.newHashMap();
        relationships.put("_a4c_generated_host", hostedOn);
        redirectedRelationship.setTarget(id);
        nodeTemplate.setRelationships(relationships);
        return nodeTemplate;
    }

    private void removeNode(PaaSTopologyDeploymentContext deploymentContext, PaaSNodeTemplate newTargetNode, PaaSNodeTemplate oldTargetNode) {
        // first of all we remove the old node from the topology
        deploymentContext.getDeploymentTopology().getNodeTemplates().remove(oldTargetNode.getId());
        for (Entry<String, NodeTemplate> nodeTemplateEntry : deploymentContext.getDeploymentTopology().getNodeTemplates().entrySet()) {
            PaaSNodeTemplate paaSNodeTemplate = deploymentContext.getPaaSTopology().getAllNodes().get(nodeTemplateEntry.getKey());
            Iterator<PaaSRelationshipTemplate> paaSRelationshipTemplateIterator = paaSNodeTemplate.getRelationshipTemplates().iterator();
            while (paaSRelationshipTemplateIterator.hasNext()) {
                PaaSRelationshipTemplate paaSRelationshipTemplate = paaSRelationshipTemplateIterator.next();
                if (paaSRelationshipTemplate.getRelationshipTemplate().getTarget().equals(oldTargetNode.getId())
                        && nodeTemplateEntry.getKey().equals(newTargetNode.getId())) {
                    nodeTemplateEntry.getValue().getRelationships().remove(paaSRelationshipTemplate.getId());
                    paaSRelationshipTemplateIterator.remove();
                }
            }
        }
    }

    /**
     * Browse the topology and for each relationship that targets the oldTargetNode, make it targeting the newTargetNode.
     */
    private List<NodeTemplate> transferRelationships(PaaSTopologyDeploymentContext deploymentContext, PaaSNodeTemplate newTargetNode,
            PaaSNodeTemplate oldTargetNode, String mainPropertyListName) {
        List<NodeTemplate> fictiveNodesToAdd = Lists.newArrayList();
        for (Entry<String, NodeTemplate> nodeTemplateEntry : deploymentContext.getDeploymentTopology().getNodeTemplates().entrySet()) {
            PaaSNodeTemplate paaSNodeTemplate = deploymentContext.getPaaSTopology().getAllNodes().get(nodeTemplateEntry.getKey());
            for (PaaSRelationshipTemplate paaSRelationshipTemplate : paaSNodeTemplate.getRelationshipTemplates()) {
                if (paaSRelationshipTemplate.getRelationshipTemplate().getTarget().equals(oldTargetNode.getId())) {
                    Map<String, Interface> interfaces = paaSRelationshipTemplate.getTemplate().getInterfaces();
                    for (Entry<String, Interface> interfaceEntry : interfaces.entrySet()) {
                        for (Entry<String, Operation> operationEntry : interfaceEntry.getValue().getOperations().entrySet()) {
                            if (operationEntry.getValue() != null && operationEntry.getValue().getInputParameters() != null) {
                                for (Entry<String, IValue> inputEntry : operationEntry.getValue().getInputParameters().entrySet()) {
                                    IValue iValue = inputEntry.getValue();
                                    if ((iValue instanceof FunctionPropertyValue)
                                            && ToscaFunctionConstants.GET_ATTRIBUTE.equals(((FunctionPropertyValue) iValue).getFunction())) {
                                        FunctionPropertyValue functionPropertyValue = (FunctionPropertyValue) iValue;
                                        if (ToscaFunctionConstants.TARGET.equals(functionPropertyValue.getTemplateName())) {
                                            // ok we have a get_attribute on a TARGET, we have to change this
                                            String attributeName = functionPropertyValue.getElementNameToFetch();
                                            // we want to transform the function into a one fetching nested properties
                                            transformFunction(functionPropertyValue, mainPropertyListName, oldTargetNode.getId(), attributeName);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    NodeTemplate fictiveComponent = createFictiveComponentAndRedirectRelationship("_a4c_" + oldTargetNode.getId(), newTargetNode.getId(),
                            paaSRelationshipTemplate.getRelationshipTemplate());
                    fictiveNodesToAdd.add(fictiveComponent);
                }
            }
        }
        return fictiveNodesToAdd;
    }

    private void transformFunction(FunctionPropertyValue function, String mainPropertyListName, String nodeName, String attributeName) {
        String att = attributeName;
        if ("device".equals(attributeName)) {
            att = "device_name";
        }
        function.replaceAllParamsExceptTemplateNameWith(mainPropertyListName, nodeName, att);
    }

    private void addProperty(Map<String, Object> map, String propertyName, Object propertyValue) {
        map.put(propertyName, propertyValue);
    }

    /**
     * here we are looking for the _a4c_substitute_for property of the node
     * if it contains something, this means that this node is substituting others
     * we generate 'fake' events for these ghosts nodes
     *
     * @param alienEvents all alien events
     * @param alienEvent the alien event to substitute
     * @param cloudifyEvent original cloudify event
     */
    public void processEventForSubstitutes(final List<AbstractMonitorEvent> alienEvents, AbstractMonitorEvent alienEvent, Event cloudifyEvent) {
        if (alienEvent instanceof PaaSInstanceStateMonitorEvent) {
            PaaSInstanceStateMonitorEvent paaSInstanceStateMonitorEvent = (PaaSInstanceStateMonitorEvent) alienEvent;
            Node[] nodes = nodeClient.list(cloudifyEvent.getContext().getDeploymentId(), paaSInstanceStateMonitorEvent.getNodeTemplateId());
            if (nodes.length > 0) {
                // since we provide the nodeId we are supposed to have only one node
                Node node = nodes[0];
                List substitutePropertyAsList = getSubstituteForPropertyAsList(node);
                if (substitutePropertyAsList != null) {
                    for (Object substitutePropertyItem : substitutePropertyAsList) {
                        PaaSInstanceStateMonitorEvent substituted = new PaaSInstanceStateMonitorEvent();
                        substituted.setDate(paaSInstanceStateMonitorEvent.getDate());
                        substituted.setDeploymentId(paaSInstanceStateMonitorEvent.getDeploymentId());
                        substituted.setInstanceState(paaSInstanceStateMonitorEvent.getInstanceState());
                        substituted.setInstanceStatus(paaSInstanceStateMonitorEvent.getInstanceStatus());
                        // we use the original instance ID
                        substituted.setInstanceId(paaSInstanceStateMonitorEvent.getInstanceId());
                        // but the name of the node that have been substituted
                        substituted.setNodeTemplateId(substitutePropertyItem.toString());
                        alienEvents.add(substituted);
                    }
                }
            }
        }
    }

    public static List getSubstituteForPropertyAsList(Node node) {
        List substitutePropertyAsList = null;
        if (node.getProperties() != null) {
            Object substituteProperty = node.getProperties().get(SUBSTITUTE_FOR_PROPERTY);
            if (substituteProperty != null && substituteProperty instanceof List) {
                substitutePropertyAsList = (List) substituteProperty;
            }
        }
        return substitutePropertyAsList;
    }

    public void processPersistentResourceEvent(EventAlienPersistent eventAlienPersistent, PaaSInstancePersistentResourceMonitorEvent alienEvent,
            Event cloudifyEvent) {
        Node node = getNode(cloudifyEvent.getContext().getDeploymentId(), alienEvent.getNodeTemplateId());
        if (node != null && isFromType(node, SCALABLE_COMPUTE_TYPE)) {
            // the persistentResourcePath fo ex should be of type: volumes.BlockStorage.resourceId
            String persistentResourcePath = eventAlienPersistent.getPersistentResourceId();
            String[] paths = persistentResourcePath.split("\\.");
            if (paths.length >= 2 && StringUtils.startsWithAny(paths[0], SCALABLE_COMPUTE_VOLUMES_PROPERTY, SCALABLE_COMPUTE_FLOATING_IPS_PROPERTY)) {
                // change the id of the related template, so that the resource will be saved in the good one
                // paths[1] is 'BlockStorage', the name of the substituted node
                // TODO: check maybe that it indeed exists in the substituted for the node? (getSubstituteForPropertyAsList)
                alienEvent.setNodeTemplateId(paths[1]);
            }
        }
    }

    private Node getNode(String deploymentId, String nodeTemplateId) {
        Node[] nodes = nodeClient.list(deploymentId, nodeTemplateId);
        if (nodes.length > 0) {
            // since we provide the nodeId we are supposed to have only one node
            return nodes[0];
        }
        return null;
    }

    private boolean isFromType(Node node, String type) {
        return Objects.equals(node.getType(), type) || CollectionUtils.containsAll(node.getTypeHierarchy(), Sets.newHashSet(type));
    }

}
