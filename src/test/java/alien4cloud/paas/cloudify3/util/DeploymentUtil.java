package alien4cloud.paas.cloudify3.util;

import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import alien4cloud.model.application.DeploymentSetup;
import alien4cloud.model.components.IndexedModelUtils;
import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.model.components.IndexedRelationshipType;
import alien4cloud.model.topology.Topology;
import alien4cloud.paas.cloudify3.service.ComputeTemplateMatcherService;
import alien4cloud.paas.cloudify3.service.model.CloudifyDeployment;
import alien4cloud.paas.cloudify3.service.model.MatchedPaaSNativeComponentTemplate;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;
import alien4cloud.paas.model.PaaSTopology;
import alien4cloud.paas.plan.TopologyTreeBuilderService;
import alien4cloud.tosca.normative.NormativeRelationshipConstants;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@Component
@Slf4j
public class DeploymentUtil {

    @Resource
    private ComputeTemplateMatcherService computeTemplateMatcherService;

    @Resource
    private TopologyTreeBuilderService topologyTreeBuilderService;

    public CloudifyDeployment buildAlienDeployment(String deploymentId, String recipeId, Topology topology, DeploymentSetup deploymentSetup) {
        CloudifyDeployment alienDeployment = new CloudifyDeployment();
        PaaSTopology paaSTopology = topologyTreeBuilderService.buildPaaSTopology(topology);
        List<MatchedPaaSNativeComponentTemplate> matchedComputes = Lists.newArrayList();
        if (deploymentSetup != null) {
            for (PaaSNodeTemplate compute : paaSTopology.getComputes()) {
                String templateId = computeTemplateMatcherService.getTemplateId(deploymentSetup.getCloudResourcesMapping().get(compute.getId()));
                matchedComputes.add(new MatchedPaaSNativeComponentTemplate(compute, templateId));
            }
        }
        alienDeployment.setComputes(matchedComputes);
        alienDeployment.setNonNatives(paaSTopology.getNonNatives());
        Map<String, IndexedNodeType> nonNativesTypesMap = Maps.newHashMap();

        Map<String, IndexedRelationshipType> nonNativesRelationshipsTypesMap = Maps.newHashMap();
        for (PaaSNodeTemplate nonNative : paaSTopology.getNonNatives()) {
            nonNativesTypesMap.put(nonNative.getIndexedNodeType().getElementId(), nonNative.getIndexedNodeType());
            List<PaaSRelationshipTemplate> relationshipTemplates = nonNative.getRelationshipTemplates();
            for (PaaSRelationshipTemplate relationshipTemplate : relationshipTemplates) {
                if (!NormativeRelationshipConstants.DEPENDS_ON.equals(relationshipTemplate.getIndexedRelationshipType().getElementId())
                        && !NormativeRelationshipConstants.HOSTED_ON.equals(relationshipTemplate.getIndexedRelationshipType().getElementId()))
                    nonNativesRelationshipsTypesMap.put(relationshipTemplate.getIndexedRelationshipType().getElementId(),
                            relationshipTemplate.getIndexedRelationshipType());
            }
        }
        alienDeployment.setNonNativesTypes(IndexedModelUtils.orderByDerivedFromHierarchy(nonNativesTypesMap));
        alienDeployment.setNonNativesRelationshipTypes(IndexedModelUtils.orderByDerivedFromHierarchy(nonNativesRelationshipsTypesMap));

        alienDeployment.setDeploymentId(deploymentId);
        alienDeployment.setRecipeId(recipeId);
        return alienDeployment;
    }
}
