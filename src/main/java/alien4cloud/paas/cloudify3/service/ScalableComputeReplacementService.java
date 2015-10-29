package alien4cloud.paas.cloudify3.service;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import org.elasticsearch.common.collect.Lists;
import org.elasticsearch.common.collect.Maps;
import org.elasticsearch.common.collect.Sets;
import org.springframework.stereotype.Component;

import alien4cloud.deployment.DeploymentContextService;
import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.components.ComplexPropertyValue;
import alien4cloud.model.components.FunctionPropertyValue;
import alien4cloud.model.components.IValue;
import alien4cloud.model.components.Interface;
import alien4cloud.model.components.ListPropertyValue;
import alien4cloud.model.components.Operation;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;

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

    private static final String SCALABLE_COMPUTE_TYPE = "alien.nodes.openstack.ScalableCompute";

    private static final String SCALABLE_COMPUTE_VOLUMES_PROPERTY = "volumes";

    @Inject
    private DeploymentContextService deploymentContextService;

    public PaaSTopologyDeploymentContext transformTopology(PaaSTopologyDeploymentContext deploymentContext) {
        Set<PaaSNodeTemplate> computesToReplaceSet = getComputesToReplaceSet(deploymentContext);
        if (computesToReplaceSet.isEmpty()) {
            // nothing to do concerning this topology
            return deploymentContext;
        }
        for (PaaSNodeTemplate computeNode : computesToReplaceSet) {
            replaceComputeAndRemoveResources(deploymentContext, computeNode);
        }
        // generate the new PaaSTopologyDeploymentContext from the modified topology
        PaaSTopologyDeploymentContext newDeploymentContext = deploymentContextService.buildTopologyDeploymentContext(deploymentContext.getDeployment(),
                deploymentContext.getLocations(),
                deploymentContext.getDeploymentTopology());

        return newDeploymentContext;
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
                // TODO: check if compute has public network
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
    private void replaceComputeAndRemoveResources(PaaSTopologyDeploymentContext deploymentContext, PaaSNodeTemplate compute) {
        NodeTemplate computeNodeTemplate = deploymentContext.getDeploymentTopology().getNodeTemplates().get(compute.getId());
        // actually, substituting the compute just means change it's type
        computeNodeTemplate.setType(SCALABLE_COMPUTE_TYPE);
        List<PaaSNodeTemplate> storageNodes = compute.getStorageNodes();
        if (computeNodeTemplate.getProperties() == null) {
            Map<String, AbstractPropertyValue> properties = Maps.newHashMap();
            computeNodeTemplate.setProperties(properties);
        }
        if (storageNodes != null && !storageNodes.isEmpty()) {
            computeNodeTemplate.getProperties().put(SCALABLE_COMPUTE_VOLUMES_PROPERTY, new ListPropertyValue(Lists.newArrayList()));
            for (PaaSNodeTemplate storageNode : storageNodes) {
                manageStorageNode(deploymentContext, computeNodeTemplate, compute, storageNode);
            }
        }
        // TODO: manage public networks
    }

    private void manageStorageNode(PaaSTopologyDeploymentContext deploymentContext, NodeTemplate computeNodeTemplate, PaaSNodeTemplate computeNode,
            PaaSNodeTemplate storageNode) {
        // first of all we remove the volume from the topology
        NodeTemplate storageNodeTemplate = deploymentContext.getDeploymentTopology().getNodeTemplates().remove(storageNode.getId());
        // transfert the properties of the storage node to the scalable compute node
        manageStorageNodeProperties(computeNodeTemplate, storageNodeTemplate);
        // change all relationships that target the storage to make them target the compute
        transfertNodeTargetRelationships(deploymentContext, computeNode, storageNode);
    }

    private void manageStorageNodeProperties(NodeTemplate computeNodeTemplate, NodeTemplate storageNodeTemplate) {
        // bellow the list that lists volumes for the ScalableCompute
        ListPropertyValue volumesProperty = (ListPropertyValue) computeNodeTemplate.getProperties().get(SCALABLE_COMPUTE_VOLUMES_PROPERTY);
        // build a 'alien.data.openstack.EmbededVolumeProperties' object
        ComplexPropertyValue embededVolumeProperty = new ComplexPropertyValue();
        // and add it to the list
        volumesProperty.getValue().add(embededVolumeProperty);
        // feed it with all the entries of storageNodeTemplate properties
        Map<String, AbstractPropertyValue> volumeProperties = storageNodeTemplate.getProperties();
        Map<String, Object> embededVolumePropertyValue = Maps.newHashMap();
        if (volumeProperties != null) {
            for (Entry<String, AbstractPropertyValue> e : volumeProperties.entrySet()) {
                embededVolumePropertyValue.put(e.getKey(), e.getValue());
            }
        }
        embededVolumeProperty.setValue(embededVolumePropertyValue);
    }

    /**
     * Browse the topology and for each relationship that targets the oldTargetNode, make it targeting the newTargetNode.
     */
    private void transfertNodeTargetRelationships(PaaSTopologyDeploymentContext deploymentContext, PaaSNodeTemplate newTargetNode,
            PaaSNodeTemplate oldTargetNode) {
        for (Entry<String, NodeTemplate> nodeTemplateEntry : deploymentContext.getDeploymentTopology().getNodeTemplates().entrySet()) {
            if (nodeTemplateEntry.getKey().equals(newTargetNode.getId())) {
                // this node is not concerned
                continue;
            }
            PaaSNodeTemplate paaSNodeTemplate = deploymentContext.getPaaSTopology().getAllNodes().get(nodeTemplateEntry.getKey());
            for (PaaSRelationshipTemplate paaSRelationshipTemplate : paaSNodeTemplate.getRelationshipTemplates()) {
                if (paaSRelationshipTemplate.getRelationshipTemplate().getTarget().equals(oldTargetNode.getId())) {
                    // this relationship targets the initial storage node
                    // let's make it target the scalable compute node
                    paaSRelationshipTemplate.getRelationshipTemplate().setTarget(newTargetNode.getId());
                    // TODO: here we also should explore the relationship's type interface in order to adapt get_attribute TARGET
                    Map<String, Interface> interfaces = paaSRelationshipTemplate.getIndexedToscaElement().getInterfaces();
                    for (Entry<String, Interface> interfaceEntry : interfaces.entrySet()) {
                        for (Entry<String, Operation> operationEntry : interfaceEntry.getValue().getOperations().entrySet()) {
                            for (Entry<String, IValue> inputEntry : operationEntry.getValue().getInputParameters().entrySet()) {
                                IValue iValue = inputEntry.getValue();
                                if (iValue instanceof FunctionPropertyValue) {
                                    FunctionPropertyValue functionPropertyValue = (FunctionPropertyValue) iValue;
                                    // TODO: test if it's a get_attribute or a concat
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}

