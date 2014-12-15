package alien4cloud.paas.cloudify3.service;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import alien4cloud.model.application.DeploymentSetup;
import alien4cloud.model.cloud.CloudResourceMatcherConfig;
import alien4cloud.model.cloud.ComputeTemplate;
import alien4cloud.paas.cloudify3.error.BadConfigurationException;
import alien4cloud.paas.cloudify3.service.model.MatchedPaaSComputeTemplate;
import alien4cloud.paas.model.PaaSNodeTemplate;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Handle compute template mapping, it's a reverse mapping to
 *
 * @author Minh Khang VU
 */
@Component
@Slf4j
public class ComputeTemplateMatcherService {

    private Map<ComputeTemplate, String> alienTemplateToCloudifyTemplateMapping = Maps.newHashMap();

    /**
     * Match a cloudify template based on the compute node.
     *
     * @param computeNode The compute node.
     * @return The template that matches the given compute node.
     */
    public synchronized String getTemplateId(ComputeTemplate computeNode) {
        return alienTemplateToCloudifyTemplateMapping.get(computeNode);
    }

    public synchronized void configure(CloudResourceMatcherConfig config) {
        alienTemplateToCloudifyTemplateMapping = config.getComputeTemplateMapping();
    }

    /**
     * Match a list of computes to extract information about template id
     * 
     * @param computes the actual computes to match
     * @param deploymentSetup the deployment setup
     * @return the list of matched compute
     */
    public List<MatchedPaaSComputeTemplate> match(List<PaaSNodeTemplate> computes, DeploymentSetup deploymentSetup) {
        List<MatchedPaaSComputeTemplate> matchedComputes = Lists.newArrayList();
        for (PaaSNodeTemplate compute : computes) {
            // Try to get the compute template this compute is matched to
            ComputeTemplate template = deploymentSetup.getCloudResourcesMapping().get(compute.getId());
            if (template == null) {
                String error = "Unmatched compute for <" + compute.getId() + ">";
                log.error(error);
                throw new BadConfigurationException(error);
            }
            String templateId = getTemplateId(template);
            if (templateId == null) {
                String error = "The compute node <" + compute.getId() + "> is matched to the template <" + template
                        + ">, but the PaaS provider has no match for this template, actual matching <" + alienTemplateToCloudifyTemplateMapping + ">";
                log.error(error);
                throw new BadConfigurationException(error);
            }
            matchedComputes.add(new MatchedPaaSComputeTemplate(compute, templateId));
        }
        return matchedComputes;
    }
}
