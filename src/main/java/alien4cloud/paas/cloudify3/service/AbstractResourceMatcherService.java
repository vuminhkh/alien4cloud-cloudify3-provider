package alien4cloud.paas.cloudify3.service;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import alien4cloud.paas.cloudify3.error.BadConfigurationException;
import alien4cloud.paas.cloudify3.service.model.MatchedPaaSNativeComponentTemplate;
import alien4cloud.paas.model.PaaSNodeTemplate;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@Slf4j
public class AbstractResourceMatcherService<T> {

    private Map<T, String> alienTemplateToCloudifyTemplateMapping = Maps.newHashMap();

    /**
     * Match a cloudify template based on the resource node.
     *
     * @param computeNode The resource node.
     * @return The template that matches the given resource node.
     */
    public synchronized String getTemplateId(T computeNode) {
        return alienTemplateToCloudifyTemplateMapping.get(computeNode);
    }

    public synchronized void configure(Map<T, String> config) {
        alienTemplateToCloudifyTemplateMapping = config;
    }

    /**
     * Match a list of resources to extract information about template id
     *
     * @param resources the actual resources to match
     * @param deploymentSetup the deployment setup
     * @return the list of matched resource
     */
    public List<MatchedPaaSNativeComponentTemplate> match(List<PaaSNodeTemplate> resources, Map<String, T> deploymentSetup) {
        List<MatchedPaaSNativeComponentTemplate> matchedResources = Lists.newArrayList();
        for (PaaSNodeTemplate resource : resources) {
            // Try to get the resource template this resource is matched to
            T template = deploymentSetup.get(resource.getId());
            if (template == null) {
                String error = "Unmatched resource for <" + resource.getId() + ">";
                log.error(error);
                throw new BadConfigurationException(error);
            }
            String templateId = getTemplateId(template);
            if (templateId == null) {
                String error = "The resource node <" + resource.getId() + "> is matched to the template <" + template
                        + ">, but the PaaS provider has no match for this template, actual matching <" + alienTemplateToCloudifyTemplateMapping + ">";
                log.error(error);
                throw new BadConfigurationException(error);
            }
            matchedResources.add(new MatchedPaaSNativeComponentTemplate(resource, templateId));
        }
        return matchedResources;
    }
}
