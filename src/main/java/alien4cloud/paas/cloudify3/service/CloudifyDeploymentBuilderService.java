package alien4cloud.paas.cloudify3.service;

import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import alien4cloud.model.components.IndexedModelUtils;
import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.model.components.IndexedRelationshipType;
import alien4cloud.paas.cloudify3.configuration.CloudConfigurationHolder;
import alien4cloud.paas.cloudify3.service.model.CloudifyDeployment;
import alien4cloud.paas.cloudify3.service.model.MatchedPaaSNativeComponentTemplate;
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

    @Resource(name = "cloudify-configuration-holder")
    private CloudConfigurationHolder cloudConfigurationHolder;

    public CloudifyDeployment buildCloudifyDeployment(PaaSTopologyDeploymentContext deploymentContext) {

        List<MatchedPaaSNativeComponentTemplate> matchedComputes = computeTemplateMatcherService.match(deploymentContext.getPaaSTopology().getComputes(),
                deploymentContext.getDeploymentSetup().getCloudResourcesMapping());
        List<MatchedPaaSNativeComponentTemplate> matchedNetworks = networkMatcherService.match(deploymentContext.getPaaSTopology().getNetworks(),
                deploymentContext.getDeploymentSetup().getNetworkMapping());

        Map<String, IndexedNodeType> nonNativesTypesMap = Maps.newHashMap();
        Map<String, IndexedRelationshipType> nonNativesRelationshipsTypesMap = Maps.newHashMap();
        for (PaaSNodeTemplate nonNative : deploymentContext.getPaaSTopology().getNonNatives()) {
            nonNativesTypesMap.put(nonNative.getIndexedNodeType().getElementId(), nonNative.getIndexedNodeType());
            List<PaaSRelationshipTemplate> relationshipTemplates = nonNative.getRelationshipTemplates();
            for (PaaSRelationshipTemplate relationshipTemplate : relationshipTemplates) {
                if (!NormativeRelationshipConstants.DEPENDS_ON.equals(relationshipTemplate.getIndexedRelationshipType().getElementId())
                        && !NormativeRelationshipConstants.HOSTED_ON.equals(relationshipTemplate.getIndexedRelationshipType().getElementId()))
                    nonNativesRelationshipsTypesMap.put(relationshipTemplate.getIndexedRelationshipType().getElementId(),
                            relationshipTemplate.getIndexedRelationshipType());
            }
        }

        List<MatchedPaaSNativeComponentTemplate> matchedInternalNetworks = Lists.newArrayList();
        List<MatchedPaaSNativeComponentTemplate> matchedExternalNetworks = Lists.newArrayList();

        for (MatchedPaaSNativeComponentTemplate matchedNetwork : matchedNetworks) {
            if (cloudConfigurationHolder.getConfiguration().getNetworkTemplates().get(matchedNetwork.getPaaSResourceId()).getIsExternal()) {
                matchedExternalNetworks.add(matchedNetwork);
            } else {
                matchedInternalNetworks.add(matchedNetwork);
            }
        }
        CloudifyDeployment deployment = new CloudifyDeployment(deploymentContext.getDeploymentId(), deploymentContext.getRecipeId(), matchedComputes,
                matchedInternalNetworks, matchedExternalNetworks, deploymentContext.getPaaSTopology().getNonNatives(),
                IndexedModelUtils.orderByDerivedFromHierarchy(nonNativesTypesMap),
                IndexedModelUtils.orderByDerivedFromHierarchy(nonNativesRelationshipsTypesMap));
        return deployment;
    }
}
