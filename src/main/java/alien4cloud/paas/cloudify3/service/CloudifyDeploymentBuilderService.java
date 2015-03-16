package alien4cloud.paas.cloudify3.service;

import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import alien4cloud.model.cloud.NetworkTemplate;
import alien4cloud.model.cloud.StorageTemplate;
import alien4cloud.model.components.DeploymentArtifact;
import alien4cloud.model.components.IndexedModelUtils;
import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.model.components.IndexedRelationshipType;
import alien4cloud.paas.cloudify3.service.model.CloudifyDeployment;
import alien4cloud.paas.cloudify3.service.model.IMatchedPaaSTemplate;
import alien4cloud.paas.cloudify3.service.model.MatchedPaaSComputeTemplate;
import alien4cloud.paas.cloudify3.service.model.MatchedPaaSTemplate;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.tosca.normative.NormativeRelationshipConstants;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@Component("cloudify-deployment-builder-service")
@Slf4j
public class CloudifyDeploymentBuilderService {

    @Resource(name = "cloudify-compute-template-matcher-service")
    private ComputeTemplateMatcherService computeTemplateMatcherService;

    @Resource(name = "cloudify-network-matcher-service")
    private NetworkMatcherService networkMatcherService;

    @Resource(name = "cloudify-storage-matcher-service")
    private StorageTemplateMatcherService storageMatcherService;

    private <T extends IMatchedPaaSTemplate> Map<String, T> buildTemplateMap(List<T> matchedPaaSTemplates) {
        Map<String, T> mapping = Maps.newHashMap();
        for (T matchedPaaSTemplate : matchedPaaSTemplates) {
            mapping.put(matchedPaaSTemplate.getPaaSNodeTemplate().getId(), matchedPaaSTemplate);
        }
        return mapping;
    }

    private List<IndexedNodeType> getTypesOrderedByDerivedFromHierarchy(List<PaaSNodeTemplate> nodes) {
        Map<String, IndexedNodeType> nodeTypeMap = Maps.newHashMap();
        for (PaaSNodeTemplate node : nodes) {
            nodeTypeMap.put(node.getIndexedToscaElement().getElementId(), node.getIndexedToscaElement());
        }
        return IndexedModelUtils.orderByDerivedFromHierarchy(nodeTypeMap);
    }

    public CloudifyDeployment buildCloudifyDeployment(PaaSTopologyDeploymentContext deploymentContext) {

        List<MatchedPaaSComputeTemplate> matchedComputes = computeTemplateMatcherService.match(deploymentContext.getPaaSTopology().getComputes(),
                deploymentContext.getDeploymentSetup().getCloudResourcesMapping());
        List<MatchedPaaSTemplate<NetworkTemplate>> matchedNetworks = networkMatcherService.match(deploymentContext.getPaaSTopology().getNetworks(),
                deploymentContext.getDeploymentSetup().getNetworkMapping());
        List<MatchedPaaSTemplate<StorageTemplate>> matchedStorages = storageMatcherService.match(deploymentContext.getPaaSTopology().getVolumes(),
                deploymentContext.getDeploymentSetup().getStorageMapping());

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

        List<MatchedPaaSTemplate<NetworkTemplate>> matchedInternalNetworks = Lists.newArrayList();
        List<MatchedPaaSTemplate<NetworkTemplate>> matchedExternalNetworks = Lists.newArrayList();

        for (MatchedPaaSTemplate<NetworkTemplate> matchedNetwork : matchedNetworks) {
            if (matchedNetwork.getPaaSResourceTemplate().getIsExternal()) {
                matchedExternalNetworks.add(matchedNetwork);
            } else {
                matchedInternalNetworks.add(matchedNetwork);
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
        CloudifyDeployment deployment = new CloudifyDeployment(deploymentContext.getDeploymentId(), deploymentContext.getRecipeId(), matchedComputes,
                matchedInternalNetworks, matchedExternalNetworks, matchedStorages, buildTemplateMap(matchedComputes),
                buildTemplateMap(matchedInternalNetworks), buildTemplateMap(matchedExternalNetworks), buildTemplateMap(matchedStorages), deploymentContext
                        .getPaaSTopology().getNonNatives(), IndexedModelUtils.orderByDerivedFromHierarchy(nonNativesTypesMap),
                IndexedModelUtils.orderByDerivedFromHierarchy(nonNativesRelationshipsTypesMap), getTypesOrderedByDerivedFromHierarchy(deploymentContext
                        .getPaaSTopology().getComputes()), getTypesOrderedByDerivedFromHierarchy(deploymentContext.getPaaSTopology().getNetworks()),
                getTypesOrderedByDerivedFromHierarchy(deploymentContext.getPaaSTopology().getVolumes()), deploymentContext.getPaaSTopology().getAllNodes(),
                allArtifacts);
        return deployment;
    }
}
