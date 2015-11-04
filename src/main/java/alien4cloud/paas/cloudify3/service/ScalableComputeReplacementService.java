package alien4cloud.paas.cloudify3.service;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections4.CollectionUtils;
import org.elasticsearch.common.collect.Lists;
import org.elasticsearch.common.collect.Maps;
import org.elasticsearch.common.collect.Sets;
import org.springframework.stereotype.Component;

import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.components.ComplexPropertyValue;
import alien4cloud.model.components.FunctionPropertyValue;
import alien4cloud.model.components.IValue;
import alien4cloud.model.components.IndexedRelationshipType;
import alien4cloud.model.components.Interface;
import alien4cloud.model.components.ListPropertyValue;
import alien4cloud.model.components.Operation;
import alien4cloud.model.components.ScalarPropertyValue;
import alien4cloud.model.deployment.Deployment;
import alien4cloud.model.deployment.DeploymentTopology;
import alien4cloud.model.orchestrators.locations.Location;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;
import alien4cloud.paas.model.PaaSTopology;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.paas.plan.TopologyTreeBuilderService;
import alien4cloud.tosca.normative.ToscaFunctionConstants;
import alien4cloud.utils.TypeMap;

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

    @Inject
    private TopologyTreeBuilderService topologyTreeBuilderService;

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
        computeNodeTemplate.setType(SCALABLE_COMPUTE_TYPE);
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

    private void manageStorageNode(PaaSTopologyDeploymentContext deploymentContext, NodeTemplate computeNodeTemplate, PaaSNodeTemplate computeNode,
            PaaSNodeTemplate storageNode, int indexInList, TypeMap cache) {
        // first of all we remove the volume from the topology
        NodeTemplate storageNodeTemplate = deploymentContext.getDeploymentTopology().getNodeTemplates().remove(storageNode.getId());
        // transfert the properties of the storage node to the scalable compute node
        ComplexPropertyValue embededVolumeProperty = buildAndFeedComplexProperty(computeNodeTemplate, storageNodeTemplate, SCALABLE_COMPUTE_VOLUMES_PROPERTY);
        // add deletable property
        if (storageNodeTemplate.getType().equals(DELETABLE_VOLUME_TYPE)) {
            embededVolumeProperty.getValue().put(DELETABLE_PROPERTY, new ScalarPropertyValue(Boolean.TRUE.toString()));
        } else {
            embededVolumeProperty.getValue().put(DELETABLE_PROPERTY, new ScalarPropertyValue(Boolean.FALSE.toString()));
        }
        // change all relationships that target the storage to make them target the compute
        transfertNodeTargetRelationships(deploymentContext, computeNode, storageNode, SCALABLE_COMPUTE_VOLUMES_PROPERTY, indexInList, cache);
    }

    private void manageNetworkNode(PaaSTopologyDeploymentContext deploymentContext, NodeTemplate computeNodeTemplate, PaaSNodeTemplate computeNode,
            PaaSNodeTemplate networkNode, int indexInList, TypeMap cache) {
        // first of all we remove the volume from the topology
        NodeTemplate networkNodeTemplate = deploymentContext.getDeploymentTopology().getNodeTemplates().remove(networkNode.getId());
        // transfert the properties of the network node to the scalable compute node
        buildAndFeedComplexProperty(computeNodeTemplate, networkNodeTemplate, SCALABLE_COMPUTE_FIPS_PROPERTY);
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
            // if (nodeTemplateEntry.getKey().equals(newTargetNode.getId())) {
            // // this node is not concerned
            // continue;
            // }
            PaaSNodeTemplate paaSNodeTemplate = deploymentContext.getPaaSTopology().getAllNodes().get(nodeTemplateEntry.getKey());
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
                        // TODO: here we also should explore the relationship's type interface in order to adapt get_attribute TARGET
                        IndexedRelationshipType relationshipType = paaSRelationshipTemplate.getIndexedToscaElement();
                        boolean typeAsChasnged = false;
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
                                                // TODO: for the moment, we prefix the attribute this way : volumes_0_device
                                                functionPropertyValue.setElementNameToFetch(mainPropertyListName + "_" + indexInList + "_" + attributeName);
                                                typeAsChasnged = true;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (typeAsChasnged) {
                            // the type has been modified, we want to force it's reuse later
                            cache.put(relationshipType.getElementId(), relationshipType);
                        }
                    }
                }
            }
        }
    }

}
