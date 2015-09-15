package alien4cloud.paas.cloudify3.service;

import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import alien4cloud.model.components.IndexedModelUtils;
import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.paas.cloudify3.configuration.MappingConfigurationHolder;
import alien4cloud.paas.cloudify3.service.model.CloudifyDeployment;
import alien4cloud.paas.cloudify3.service.model.IMatchedPaaSTemplate;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;

import com.google.common.collect.Maps;

@Component("cloudify-deployment-builder-service")
@Slf4j
public class CloudifyDeploymentBuilderService {

    @Resource(name = "cloudify-network-matcher-service")
    private NetworkMatcherService networkMatcherService;

    @Resource(name = "cloudify-storage-matcher-service")
    private StorageTemplateMatcherService storageMatcherService;

    @Resource
    private MappingConfigurationHolder mappingConfigurationHolder;

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
        // TODO Generate the deployment here
        // TODO Perform matching of resources etc ...
        return null;
    }

}
