package alien4cloud.paas.cloudify3.service;

import java.io.IOException;
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

import alien4cloud.model.common.Tag;
import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.components.ComplexPropertyValue;
import alien4cloud.model.components.FunctionPropertyValue;
import alien4cloud.model.components.IValue;
import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.model.components.IndexedRelationshipType;
import alien4cloud.model.components.Interface;
import alien4cloud.model.components.ListPropertyValue;
import alien4cloud.model.components.Operation;
import alien4cloud.model.components.ScalarPropertyValue;
import alien4cloud.model.deployment.Deployment;
import alien4cloud.model.deployment.DeploymentTopology;
import alien4cloud.model.orchestrators.locations.Location;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.RelationshipTemplate;
import alien4cloud.paas.cloudify3.blueprint.CustomTags;
import alien4cloud.paas.cloudify3.model.Event;
import alien4cloud.paas.cloudify3.model.Node;
import alien4cloud.paas.cloudify3.restclient.NodeClient;
import alien4cloud.paas.function.FunctionEvaluator;
import alien4cloud.paas.model.AbstractMonitorEvent;
import alien4cloud.paas.model.PaaSInstanceStateMonitorEvent;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;
import alien4cloud.paas.model.PaaSTopology;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.paas.plan.TopologyTreeBuilderService;
import alien4cloud.rest.utils.JsonUtil;
import alien4cloud.tosca.ToscaUtils;
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
 * <li>transfert the BlockStorage properties to the ScalableCompute.
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
    private static final String SCALABLE_COMPUTE_VOLUMES_PROPERTY = "volumes";
    private static final String SCALABLE_COMPUTE_FIPS_PROPERTY = "floatingips";
    private static final String SUBSTITUE_FOR_PROPERTY = "_a4c_substitute_for";
    private static final String USE_EXTERNAL_RESOURCE_PROPERTY = "use_external_resource";
    private static final String RESOURCE_NAME_PROPERTY = "resource_name";

    @Inject
    private TopologyTreeBuilderService topologyTreeBuilderService;
    @Inject
    private NodeClient nodeClient;

    public PaaSTopologyDeploymentContext transformTopology(PaaSTopologyDeploymentContext deploymentContext) {
        // any type that is modified is cached in this map in order to be reused later while regenerating the deployment ctx
        TypeMap cache = new TypeMap();
        Set<PaaSNodeTemplate> computesToReplaceSet = getComputesToReplaceSet(deploymentContext);
        if (computesToReplaceSet.isEmpty()) {
            // nothing to do concerning this topology
            return deploymentContext;
        }
        for (PaaSNodeTemplate computeNode : computesToReplaceSet) {
            replaceComputeAndRemoveResources(deploymentContext, computeNode, cache);
        }
        // generate the new PaaSTopologyDeploymentContext from the modified topology
        PaaSTopologyDeploymentContext newDeploymentContext = buildTopologyDeploymentContext(deploymentContext.getDeployment(),
                deploymentContext.getLocations(), deploymentContext.getDeploymentTopology(), cache);

        return newDeploymentContext;
    }

    /**
     * This method has been duplicated from ...
     * <p>
     * alien4cloud.deployment.DeploymentContextService.buildTopologyDeploymentContext(Deployment, Map<String, Location>, DeploymentTopology)
     * <p>
     * ... in order to call the topologyTreeBuilderService with our own TypeMap cache.
     */
    @Deprecated
    private PaaSTopologyDeploymentContext buildTopologyDeploymentContext(Deployment deployment, Map<String, Location> locations, DeploymentTopology topology,
            TypeMap cache) {
        PaaSTopologyDeploymentContext topologyDeploymentContext = new PaaSTopologyDeploymentContext();
        topologyDeploymentContext.setLocations(locations);
        topologyDeploymentContext.setDeployment(deployment);
        PaaSTopology paaSTopology = topologyTreeBuilderService.buildPaaSTopology(topology, cache);
        topologyDeploymentContext.setPaaSTopology(paaSTopology);
        topologyDeploymentContext.setDeploymentTopology(topology);
        topologyDeploymentContext.setDeployment(deployment);
        return topologyDeploymentContext;
    }

    /**
     * Find the compute nodes that need to be replaced.
     */
    private Set<PaaSNodeTemplate> getComputesToReplaceSet(PaaSTopologyDeploymentContext deploymentContext) {
        Set<PaaSNodeTemplate> computesToReplaceSet = Sets.newHashSet();
        List<PaaSNodeTemplate> computes = deploymentContext.getPaaSTopology().getComputes();
        for (PaaSNodeTemplate compute : computes) {
            if (compute.getScalingPolicy() != null && compute.getScalingPolicy().getMaxInstances() > 1) {
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

    /**
     * For this particular compute:
     * <ul>
     * <li>substitute the compute using a ScalableCompute
     * <li>remove the resources related to compute (volumes, floating ips).
     * </ul>
     */
    private void replaceComputeAndRemoveResources(PaaSTopologyDeploymentContext deploymentContext, PaaSNodeTemplate compute, TypeMap cache) {
        NodeTemplate computeNodeTemplate = deploymentContext.getDeploymentTopology().getNodeTemplates().get(compute.getId());
        // actually, substituting the compute just means change it's type
        // and replace the related indexedNodeType
        computeNodeTemplate.setType(SCALABLE_COMPUTE_TYPE);
        compute.setIndexedToscaElement(topologyTreeBuilderService.getToscaType(SCALABLE_COMPUTE_TYPE, cache, deploymentContext.getDeploymentTopology()
                .getDependencies(), IndexedNodeType.class));
        List<PaaSNodeTemplate> storageNodes = compute.getStorageNodes();
        if (computeNodeTemplate.getProperties() == null) {
            Map<String, AbstractPropertyValue> properties = Maps.newHashMap();
            computeNodeTemplate.setProperties(properties);
        }
        // manage volumes
        if (CollectionUtils.isNotEmpty(storageNodes)) {
            computeNodeTemplate.getProperties().put(SCALABLE_COMPUTE_VOLUMES_PROPERTY, new ListPropertyValue(Lists.newArrayList()));
            int volumeIdx = 0;
            for (PaaSNodeTemplate storageNode : storageNodes) {
                manageStorageNode(deploymentContext, computeNodeTemplate, compute, storageNode, volumeIdx++, cache);
            }
        }
        // manage public networks
        Set<PaaSNodeTemplate> publicNetworkPaaSNodeTemplates = getPublicNetworkPaaSNodeTemplate(compute);
        int floatingIpIdx = 0;
        if (!publicNetworkPaaSNodeTemplates.isEmpty()) {
            computeNodeTemplate.getProperties().put(SCALABLE_COMPUTE_FIPS_PROPERTY, new ListPropertyValue(Lists.newArrayList()));
            for (PaaSNodeTemplate publicNetworkPaaSNodeTemplate : publicNetworkPaaSNodeTemplates) {
                manageNetworkNode(deploymentContext, computeNodeTemplate, compute, publicNetworkPaaSNodeTemplate, floatingIpIdx++, cache);
            }
        }
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

    private void addSubsituteForPropertyValue(NodeTemplate computeNodeTemplate, String substitutedNodeId) {
        AbstractPropertyValue value = computeNodeTemplate.getProperties().get(SUBSTITUE_FOR_PROPERTY);
        ListPropertyValue valueAsList = null;
        if (value == null) {
            valueAsList = new ListPropertyValue(Lists.newArrayList());
            computeNodeTemplate.getProperties().put(SUBSTITUE_FOR_PROPERTY, valueAsList);
        } else {
            valueAsList = (ListPropertyValue) value;
        }
        valueAsList.getValue().add(new ScalarPropertyValue(substitutedNodeId));
    }

    private void manageStorageNode(PaaSTopologyDeploymentContext deploymentContext, NodeTemplate computeNodeTemplate, PaaSNodeTemplate computeNode,
            PaaSNodeTemplate storageNode, int indexInList, TypeMap cache) {
        // first of all we remove the volume from the topology
        NodeTemplate storageNodeTemplate = deploymentContext.getDeploymentTopology().getNodeTemplates().remove(storageNode.getId());
        addSubsituteForPropertyValue(computeNodeTemplate, storageNode.getId());
        // transfert the properties of the storage node to the scalable compute node
        ComplexPropertyValue embededVolumeProperty = buildAndFeedComplexProperty(computeNodeTemplate, storageNodeTemplate, SCALABLE_COMPUTE_VOLUMES_PROPERTY);

        // add resource_name
        addProperty(embededVolumeProperty.getValue(), RESOURCE_NAME_PROPERTY, new ScalarPropertyValue(storageNode.getId()));

        // add deletable property
        addProperty(embededVolumeProperty.getValue(), DELETABLE_PROPERTY,
                new ScalarPropertyValue(Boolean.valueOf(Objects.equals(storageNodeTemplate.getType(), DELETABLE_VOLUME_TYPE)).toString()));

        // add "use_external_resource" property
        ScalarPropertyValue volumeIdScalar = (ScalarPropertyValue) embededVolumeProperty.getValue().get(NormativeBlockStorageConstants.VOLUME_ID);
        String volumeId = FunctionEvaluator.getScalarValue(volumeIdScalar);
        addProperty(embededVolumeProperty.getValue(), USE_EXTERNAL_RESOURCE_PROPERTY, new ScalarPropertyValue(Boolean.valueOf(StringUtils.isNotBlank(volumeId))
                .toString()));

        // change all relationships that target the storage to make them target the compute
        transfertNodeTargetRelationships(deploymentContext, computeNode, storageNode, SCALABLE_COMPUTE_VOLUMES_PROPERTY, indexInList, cache);

        // transfert persistent resources tag if needed
        transfertPersistentResourcesTag(deploymentContext, computeNode, storageNode, SCALABLE_COMPUTE_VOLUMES_PROPERTY);
    }

    private void transfertPersistentResourcesTag(PaaSTopologyDeploymentContext deploymentContext, PaaSNodeTemplate computeNode, PaaSNodeTemplate oldNode,
            String mainPropertyListName) {
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
        Tag persistentResouceTag = TagUtil.getTagByName(indexedToscaElement.getTags(), CustomTags.PERSISTENT_RESOURCE_TAG);
        if (persistentResouceTag == null) {
            persistentResouceTag = new Tag();
            persistentResouceTag.setName(CustomTags.PERSISTENT_RESOURCE_TAG);
        }

        try {
            persistentResouceTag.setValue(new ObjectMapper().writeValueAsString(computePersistentResourceConf));
            tags.add(persistentResouceTag);
            return true;
        } catch (JsonProcessingException e) {
            log.error("Failed to write tag <" + CustomTags.PERSISTENT_RESOURCE_TAG + "> value <" + computePersistentResourceConf + "> for tosca element "
                    + indexedToscaElement.getElementId() + ", will be ignored", e);
        }

        return false;
    }

    private String formatPersistentResourcePath(String persistentResourceId, PaaSNodeTemplate oldNode, String mainPropertyListName) {
        // mainPropertyListName.nodeName.persistentResourceId
        StringBuilder builder = new StringBuilder(mainPropertyListName);
        builder.append(".").append(oldNode.getId()).append(".").append(persistentResourceId);
        return builder.toString();
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

    private void manageNetworkNode(PaaSTopologyDeploymentContext deploymentContext, NodeTemplate computeNodeTemplate, PaaSNodeTemplate computeNode,
            PaaSNodeTemplate networkNode, int indexInList, TypeMap cache) {
        // first of all we remove the volume from the topology
        NodeTemplate networkNodeTemplate = deploymentContext.getDeploymentTopology().getNodeTemplates().remove(networkNode.getId());
        addSubsituteForPropertyValue(computeNodeTemplate, networkNode.getId());
        // transfert the properties of the storage node to the scalable compute node
        ComplexPropertyValue embededFloatingProperty = buildAndFeedComplexProperty(computeNodeTemplate, networkNodeTemplate, SCALABLE_COMPUTE_FIPS_PROPERTY);

        // add resource_name
        addProperty(embededFloatingProperty.getValue(), RESOURCE_NAME_PROPERTY, new ScalarPropertyValue(networkNode.getId()));
        // TODO: manage existing floating ip
        embededFloatingProperty.getValue().put(USE_EXTERNAL_RESOURCE_PROPERTY, new ScalarPropertyValue(Boolean.FALSE.toString()));
        // change all relationships that target the network to make them target the compute
        transfertNodeTargetRelationships(deploymentContext, computeNode, networkNode, SCALABLE_COMPUTE_FIPS_PROPERTY, indexInList, cache);
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

    /**
     * Browse the topology and for each relationship that targets the oldTargetNode, make it targeting the newTargetNode.
     */
    private void transfertNodeTargetRelationships(PaaSTopologyDeploymentContext deploymentContext, PaaSNodeTemplate newTargetNode,
            PaaSNodeTemplate oldTargetNode, String mainPropertyListName, int indexInList, TypeMap cache) {
        for (Entry<String, NodeTemplate> nodeTemplateEntry : deploymentContext.getDeploymentTopology().getNodeTemplates().entrySet()) {
            PaaSNodeTemplate paaSNodeTemplate = deploymentContext.getPaaSTopology().getAllNodes().get(nodeTemplateEntry.getKey());
            Map<String, IndexedRelationshipType> relationshipRetargeted = Maps.newHashMap();
            boolean hasHostedOn = false;
            for (PaaSRelationshipTemplate paaSRelationshipTemplate : paaSNodeTemplate.getRelationshipTemplates()) {
                if (paaSRelationshipTemplate.getRelationshipTemplate().getTarget().equals(oldTargetNode.getId())) {
                    if (nodeTemplateEntry.getKey().equals(newTargetNode.getId())) {
                        // the target is the removed node and the src is the modified compute
                        // just remove the relation
                        nodeTemplateEntry.getValue().getRelationships().remove(paaSRelationshipTemplate.getId());
                    } else {
                        // this relationship targets the initial storage node
                        // let's make it target the scalable compute node
                        paaSRelationshipTemplate.getRelationshipTemplate().setTarget(newTargetNode.getId());
                        IndexedRelationshipType relationshipType = paaSRelationshipTemplate.getIndexedToscaElement();
                        // just remember the fact that this relationhip is retargeted
                        relationshipRetargeted.put(paaSRelationshipTemplate.getId(), relationshipType);
                        boolean typeAsChanged = false;
                        // here we also explore the relationship's type interface in order to adapt get_attribute TARGET
                        Map<String, Interface> interfaces = relationshipType.getInterfaces();
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
                                                typeAsChanged = true;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (typeAsChanged) {
                            // the type has been modified, we want to force it's reuse later
                            cache.put(relationshipType.getElementId(), relationshipType);
                        }
                    }
                } else if (paaSRelationshipTemplate.getRelationshipTemplate().getTarget().equals(newTargetNode.getId())
                        && ToscaUtils.isFromType(NormativeRelationshipConstants.HOSTED_ON, paaSRelationshipTemplate.getIndexedToscaElement())) {
                    // a hostedOn relationship wires the current node with the substituing node
                    hasHostedOn = true;
                }
            }
            // now remove relation ships between the node and the substituing node that are not the retargeted one
            if (!relationshipRetargeted.isEmpty()) {
                Set<String> keysToRemove = Sets.newHashSet();
                for (Entry<String, RelationshipTemplate> relationshipEntry : paaSNodeTemplate.getNodeTemplate().getRelationships().entrySet()) {
                    if (!relationshipRetargeted.containsKey(relationshipEntry.getKey())
                            && relationshipEntry.getValue().getTarget().equals(newTargetNode.getId())) {
                        keysToRemove.add(relationshipEntry.getKey());
                    }
                }
                for (String keyToRemove : keysToRemove) {
                    paaSNodeTemplate.getNodeTemplate().getRelationships().remove(keyToRemove);
                }
                if (hasHostedOn) {
                    // one hostedOn relation has been removed, we artificially make one of the relation derived from hostedOn
                    IndexedRelationshipType relationshipType = relationshipRetargeted.entrySet().iterator().next().getValue();
                    List<String> derivedFrom = Lists.newArrayList();
                    derivedFrom.add(NormativeRelationshipConstants.HOSTED_ON);
                    relationshipType.setDerivedFrom(derivedFrom);
                    // ensure the modified type will be used later
                    cache.put(relationshipType.getElementId(), relationshipType);
                }
            }

        }
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
     * @param alienEvents
     * @param alienEvent
     * @param cloudifyEvent
     * @param eventService TODO
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
            Object substituteProperty = node.getProperties().get(SUBSTITUE_FOR_PROPERTY);
            if (substituteProperty != null && substituteProperty instanceof List) {
                substitutePropertyAsList = (List) substituteProperty;
            }
        }
        return substitutePropertyAsList;
    }

}
