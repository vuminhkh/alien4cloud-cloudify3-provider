package alien4cloud.paas.cloudify3.service;

import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import alien4cloud.paas.cloudify3.util.mapping.PropertiesMappingUtil;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Component;

import alien4cloud.exception.InvalidArgumentException;
import alien4cloud.model.components.DeploymentArtifact;
import alien4cloud.model.components.IndexedModelUtils;
import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.model.components.IndexedRelationshipType;
import alien4cloud.paas.cloudify3.error.SingleLocationRequiredException;
import alien4cloud.paas.cloudify3.service.model.CloudifyDeployment;
import alien4cloud.paas.cloudify3.service.model.Relationship;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.paas.wf.WorkflowsBuilderService;
import alien4cloud.paas.wf.WorkflowsBuilderService.TopologyContext;
import alien4cloud.tosca.ToscaUtils;
import alien4cloud.tosca.normative.NormativeRelationshipConstants;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@Component("cloudify-deployment-builder-service")
@Slf4j
public class CloudifyDeploymentBuilderService {

    @Inject
    private WorkflowsBuilderService workflowBuilderService;

    /**
     * Build the Cloudify deployment from the deployment context. Cloudify deployment has data pre-parsed so that blueprint generation is easier.
     *
     * @param deploymentContext
     *            the deployment context
     * @return the cloudify deployment
     */
    public CloudifyDeployment buildCloudifyDeployment(PaaSTopologyDeploymentContext deploymentContext) {
        CloudifyDeployment cloudifyDeployment = new CloudifyDeployment();

        processNetworks(cloudifyDeployment, deploymentContext);
        processNonNativeTypes(cloudifyDeployment, deploymentContext);
        processDeploymentArtifacts(cloudifyDeployment, deploymentContext);

        List<IndexedNodeType> nativeTypes = getTypesOrderedByDerivedFromHierarchy(deploymentContext.getPaaSTopology().getComputes());
        nativeTypes.addAll(getTypesOrderedByDerivedFromHierarchy(deploymentContext.getPaaSTopology().getNetworks()));
        nativeTypes.addAll(getTypesOrderedByDerivedFromHierarchy(deploymentContext.getPaaSTopology().getVolumes()));
        Map<String, IndexedNodeType> nativeTypesDerivedFrom = getDerivedFromTypesMap(deploymentContext.getPaaSTopology().getComputes());
        nativeTypesDerivedFrom.putAll(getDerivedFromTypesMap(deploymentContext.getPaaSTopology().getVolumes()));
        nativeTypesDerivedFrom.putAll(getDerivedFromTypesMap(deploymentContext.getPaaSTopology().getNetworks()));

        cloudifyDeployment.setDeploymentPaaSId(deploymentContext.getDeploymentPaaSId());
        cloudifyDeployment.setDeploymentId(deploymentContext.getDeploymentId());
        cloudifyDeployment.setLocationType(getLocationType(deploymentContext));
        cloudifyDeployment.setComputes(deploymentContext.getPaaSTopology().getComputes());
        cloudifyDeployment.setVolumes(deploymentContext.getPaaSTopology().getVolumes());
        cloudifyDeployment.setNonNatives(deploymentContext.getPaaSTopology().getNonNatives());
        cloudifyDeployment.setNativeTypes(nativeTypes);
        cloudifyDeployment.setNativeTypesHierarchy(nativeTypesDerivedFrom);

        cloudifyDeployment.setAllNodes(deploymentContext.getPaaSTopology().getAllNodes());
        cloudifyDeployment.setProviderDeploymentProperties(deploymentContext.getDeploymentTopology().getProviderDeploymentProperties());
        cloudifyDeployment.setWorkflows(deploymentContext.getDeploymentTopology().getWorkflows());

        // load the mappings for the native types.
        TopologyContext topologyContext = workflowBuilderService.buildTopologyContext(deploymentContext.getDeploymentTopology());
        cloudifyDeployment.setPropertyMappings(PropertiesMappingUtil.loadPropertyMappings(cloudifyDeployment.getNativeTypes(), topologyContext));

        return cloudifyDeployment;
    }

    private List<IndexedNodeType> getTypesOrderedByDerivedFromHierarchy(List<PaaSNodeTemplate> nodes) {
        Map<String, IndexedNodeType> nodeTypeMap = Maps.newHashMap();
        for (PaaSNodeTemplate node : nodes) {
            nodeTypeMap.put(node.getIndexedToscaElement().getElementId(), node.getIndexedToscaElement());
        }
        return IndexedModelUtils.orderByDerivedFromHierarchy(nodeTypeMap);
    }

    private Map<String, IndexedNodeType> getDerivedFromTypesMap(List<PaaSNodeTemplate> nodes) {
        Map<String, IndexedNodeType> derivedFromTypesMap = Maps.newHashMap();
        for (PaaSNodeTemplate node : nodes) {
            List<IndexedNodeType> derivedFroms = node.getDerivedFroms();
            for (IndexedNodeType derivedFrom : derivedFroms) {
                derivedFromTypesMap.put(derivedFrom.getElementId(), derivedFrom);
            }
        }
        return derivedFromTypesMap;
    }

    /**
     * Get the location of the deployment from the context.
     */
    private String getLocationType(PaaSTopologyDeploymentContext deploymentContext) {
        if (MapUtils.isEmpty(deploymentContext.getLocations()) || deploymentContext.getLocations().size() != 1) {
            throw new SingleLocationRequiredException();
        }
        return deploymentContext.getLocations().values().iterator().next().getInfrastructureType();
    }

    /**
     * Map the networks from the topology to either public or private network.
     * Cloudify 3 plugin indeed maps the public network to floating ips while private network are mapped to network and subnets.
     *
     * @param cloudifyDeployment
     *            The cloudify deployment context with private and public networks mapped.
     * @param deploymentContext
     *            The deployment context from alien 4 cloud.
     */
    private void processNetworks(CloudifyDeployment cloudifyDeployment, PaaSTopologyDeploymentContext deploymentContext) {
        List<PaaSNodeTemplate> allNetworks = deploymentContext.getPaaSTopology().getNetworks();
        List<PaaSNodeTemplate> publicNetworks = Lists.newArrayList();
        List<PaaSNodeTemplate> privateNetworks = Lists.newArrayList();
        for (PaaSNodeTemplate network : allNetworks) {
            if (ToscaUtils.isFromType("alien.nodes.PublicNetwork", network.getIndexedToscaElement())) {
                publicNetworks.add(network);
            } else if (ToscaUtils.isFromType("alien.nodes.PrivateNetwork", network.getIndexedToscaElement())) {
                privateNetworks.add(network);
            } else {
                throw new InvalidArgumentException(
                        "The type " + network.getTemplate().getType() + " must extends alien.nodes.PublicNetwork or alien.nodes.PrivateNetwork");
            }
        }

        cloudifyDeployment.setExternalNetworks(publicNetworks);
        cloudifyDeployment.setInternalNetworks(privateNetworks);
    }

    /**
     * Extract the types of all types that are not provided by cloudify (non-native types) for both nodes and relationships.
     * Types have to be generated in the blueprint in correct order (based on derived from hierarchy).
     *
     * @param cloudifyDeployment
     *            The cloudify deployment context with private and public networks mapped.
     * @param deploymentContext
     *            The deployment context from alien 4 cloud.
     */
    private void processNonNativeTypes(CloudifyDeployment cloudifyDeployment, PaaSTopologyDeploymentContext deploymentContext) {
        Map<String, IndexedNodeType> nonNativesTypesMap = Maps.newHashMap();
        Map<String, IndexedRelationshipType> nonNativesRelationshipsTypesMap = Maps.newHashMap();
        for (PaaSNodeTemplate nonNative : deploymentContext.getPaaSTopology().getNonNatives()) {
            nonNativesTypesMap.put(nonNative.getIndexedToscaElement().getElementId(), nonNative.getIndexedToscaElement());
            List<PaaSRelationshipTemplate> relationshipTemplates = nonNative.getRelationshipTemplates();
            for (PaaSRelationshipTemplate relationshipTemplate : relationshipTemplates) {
                if (!NormativeRelationshipConstants.DEPENDS_ON.equals(relationshipTemplate.getIndexedToscaElement().getElementId())
                        && !NormativeRelationshipConstants.HOSTED_ON.equals(relationshipTemplate.getIndexedToscaElement().getElementId())) {
                    nonNativesRelationshipsTypesMap.put(relationshipTemplate.getIndexedToscaElement().getElementId(),
                            relationshipTemplate.getIndexedToscaElement());
                }
            }
        }

        cloudifyDeployment.setNonNativesTypes(IndexedModelUtils.orderByDerivedFromHierarchy(nonNativesTypesMap));
        cloudifyDeployment.setNonNativesRelationshipTypes(IndexedModelUtils.orderByDerivedFromHierarchy(nonNativesRelationshipsTypesMap));
    }

    private void processDeploymentArtifacts(CloudifyDeployment cloudifyDeployment, PaaSTopologyDeploymentContext deploymentContext) {
        Map<String, Map<String, DeploymentArtifact>> allArtifacts = Maps.newHashMap();
        Map<Relationship, Map<String, DeploymentArtifact>> allRelationshipArtifacts = Maps.newHashMap();
        for (Map.Entry<String, PaaSNodeTemplate> nodeEntry : deploymentContext.getPaaSTopology().getAllNodes().entrySet()) {
            PaaSNodeTemplate node = nodeEntry.getValue();
            // add the node artifacts
            putArtifacts(allArtifacts, node.getId(), node.getIndexedToscaElement().getArtifacts());
            // add the relationships artifacts
            addRelationshipsArtifacts(allRelationshipArtifacts, nodeEntry.getValue().getRelationshipTemplates());
        }

        cloudifyDeployment.setAllDeploymentArtifacts(allArtifacts);
        cloudifyDeployment.setAllRelationshipDeploymentArtifacts(allRelationshipArtifacts);
    }

    private void addRelationshipsArtifacts(Map<Relationship, Map<String, DeploymentArtifact>> allRelationshipArtifacts,
            List<PaaSRelationshipTemplate> relationships) {
        if (relationships == null || relationships.isEmpty()) {
            return;
        }

        for (PaaSRelationshipTemplate relationship : relationships) {
            Map<String, DeploymentArtifact> artifacts = relationship.getIndexedToscaElement().getArtifacts();

            putArtifacts(allRelationshipArtifacts,
                    new Relationship(relationship.getId(), relationship.getSource(), relationship.getRelationshipTemplate().getTarget()), artifacts);
        }
    }

    private <T> void putArtifacts(Map<T, Map<String, DeploymentArtifact>> targetMap, T key, Map<String, DeploymentArtifact> artifacts) {
        if (artifacts != null && !artifacts.isEmpty()) {
            targetMap.put(key, artifacts);
        }
    }
}
