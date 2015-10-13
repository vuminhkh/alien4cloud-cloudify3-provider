package alien4cloud.paas.cloudify3.service;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import alien4cloud.exception.InvalidArgumentException;
import alien4cloud.model.components.DeploymentArtifact;
import alien4cloud.model.components.IndexedModelUtils;
import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.model.components.IndexedRelationshipType;
import alien4cloud.paas.cloudify3.service.model.CloudifyDeployment;
import alien4cloud.paas.cloudify3.service.model.Relationship;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.tosca.ToscaUtils;
import alien4cloud.tosca.normative.NormativeRelationshipConstants;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@Component("cloudify-deployment-builder-service")
@Slf4j
public class CloudifyDeploymentBuilderService {

    private List<IndexedNodeType> getTypesOrderedByDerivedFromHierarchy(List<PaaSNodeTemplate> nodes) {
        Map<String, IndexedNodeType> nodeTypeMap = Maps.newHashMap();
        for (PaaSNodeTemplate node : nodes) {
            nodeTypeMap.put(node.getIndexedToscaElement().getElementId(), node.getIndexedToscaElement());
        }
        return IndexedModelUtils.orderByDerivedFromHierarchy(nodeTypeMap);
    }

    private Map<String, PaaSNodeTemplate> buildTemplateMap(List<PaaSNodeTemplate> templateList) {
        Map<String, PaaSNodeTemplate> computeMap = Maps.newHashMap();
        for (PaaSNodeTemplate nodeTemplate : templateList) {
            computeMap.put(nodeTemplate.getId(), nodeTemplate);
        }
        return computeMap;
    }

    public CloudifyDeployment buildCloudifyDeployment(PaaSTopologyDeploymentContext deploymentContext) {

        Map<String, PaaSNodeTemplate> computesMap = buildTemplateMap(deploymentContext.getPaaSTopology().getComputes());
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
        Map<String, PaaSNodeTemplate> publicNetworksMap = buildTemplateMap(publicNetworks);
        Map<String, PaaSNodeTemplate> privateNetworksMap = buildTemplateMap(privateNetworks);

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
        Map<String, Map<String, DeploymentArtifact>> allArtifacts = Maps.newHashMap();
        for (Map.Entry<String, PaaSNodeTemplate> nodeEntry : deploymentContext.getPaaSTopology().getAllNodes().entrySet()) {
            PaaSNodeTemplate node = nodeEntry.getValue();
            Map<String, DeploymentArtifact> artifacts = node.getIndexedToscaElement().getArtifacts();
            if (artifacts != null && !artifacts.isEmpty()) {
                allArtifacts.put(node.getId(), artifacts);
            }
        }

        Map<Relationship, Map<String, DeploymentArtifact>> allRelationshipArtifacts = Maps.newHashMap();
        for (Map.Entry<String, PaaSNodeTemplate> nodeEntry : deploymentContext.getPaaSTopology().getAllNodes().entrySet()) {
            List<PaaSRelationshipTemplate> relationships = nodeEntry.getValue().getRelationshipTemplates();
            if (relationships != null && !relationships.isEmpty()) {
                for (PaaSRelationshipTemplate relationship : relationships) {
                    Map<String, DeploymentArtifact> artifacts = relationship.getIndexedToscaElement().getArtifacts();
                    if (artifacts != null && !artifacts.isEmpty()) {
                        allRelationshipArtifacts.put(
                                new Relationship(relationship.getId(), relationship.getSource(), relationship.getRelationshipTemplate().getTarget()),
                                artifacts);
                    }
                }
            }
        }
        List<IndexedNodeType> nativeTypes = getTypesOrderedByDerivedFromHierarchy(deploymentContext.getPaaSTopology().getComputes());
        nativeTypes.addAll(getTypesOrderedByDerivedFromHierarchy(deploymentContext.getPaaSTopology().getNetworks()));
        nativeTypes.addAll(getTypesOrderedByDerivedFromHierarchy(deploymentContext.getPaaSTopology().getVolumes()));
        CloudifyDeployment deployment = new CloudifyDeployment(deploymentContext.getDeploymentPaaSId(), deploymentContext.getDeploymentId(),
                deploymentContext.getPaaSTopology().getComputes(), computesMap, publicNetworks, publicNetworksMap, privateNetworks, privateNetworksMap,
                deploymentContext.getPaaSTopology().getNonNatives(), IndexedModelUtils.orderByDerivedFromHierarchy(nonNativesTypesMap),
                IndexedModelUtils.orderByDerivedFromHierarchy(nonNativesRelationshipsTypesMap), nativeTypes, deploymentContext.getPaaSTopology().getAllNodes(),
                allArtifacts, allRelationshipArtifacts, deploymentContext.getDeploymentTopology().getProviderDeploymentProperties(),
                deploymentContext.getDeploymentTopology().getWorkflows());
        return deployment;
    }
}
