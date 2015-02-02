package alien4cloud.paas.cloudify3.service;

import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import alien4cloud.model.cloud.NetworkTemplate;
import alien4cloud.model.cloud.StorageTemplate;
import alien4cloud.model.components.IndexedModelUtils;
import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.model.components.IndexedRelationshipType;
import alien4cloud.paas.cloudify3.service.model.CloudifyDeployment;
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
        CloudifyDeployment deployment = new CloudifyDeployment(deploymentContext.getDeploymentId(), deploymentContext.getRecipeId(), matchedComputes,
                matchedInternalNetworks, matchedExternalNetworks, matchedStorages, deploymentContext.getPaaSTopology().getNonNatives(),
                IndexedModelUtils.orderByDerivedFromHierarchy(nonNativesTypesMap),
                IndexedModelUtils.orderByDerivedFromHierarchy(nonNativesRelationshipsTypesMap), deploymentContext.getPaaSTopology().getAllNodes());
        return deployment;
    }
}
