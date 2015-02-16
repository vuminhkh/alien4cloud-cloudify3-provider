package alien4cloud.paas.cloudify3.service;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import alien4cloud.model.cloud.CloudImage;
import alien4cloud.model.cloud.CloudImageFlavor;
import alien4cloud.model.cloud.ComputeTemplate;
import alien4cloud.paas.cloudify3.error.BadConfigurationException;
import alien4cloud.paas.cloudify3.service.model.MatchedPaaSComputeTemplate;
import alien4cloud.paas.cloudify3.service.model.PaaSComputeTemplate;
import alien4cloud.paas.model.PaaSNodeTemplate;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Handle compute template mapping
 *
 * @author Minh Khang VU
 */
@Component("cloudify-compute-template-matcher-service")
@Slf4j
public class ComputeTemplateMatcherService {

    private Map<String, String> imageMapping = Maps.newHashMap();

    private Map<String, String> flavorMapping = Maps.newHashMap();

    public synchronized void configure(Map<CloudImage, String> imageMapping, Map<CloudImageFlavor, String> flavorMapping) {
        if (imageMapping != null) {
            for (Map.Entry<CloudImage, String> imageMappingEntry : imageMapping.entrySet()) {
                this.imageMapping.put(imageMappingEntry.getKey().getId(), imageMappingEntry.getValue());
            }
        }
        if (flavorMapping != null) {
            for (Map.Entry<CloudImageFlavor, String> flavorMappingEntry : flavorMapping.entrySet()) {
                this.flavorMapping.put(flavorMappingEntry.getKey().getId(), flavorMappingEntry.getValue());
            }
        }
    }

    public synchronized String getPaaSImageId(String alienImageId) {
        return this.imageMapping.get(alienImageId);
    }

    public synchronized String getPaaSFlavorId(String alienFlavorId) {
        return this.flavorMapping.get(alienFlavorId);
    }

    public List<MatchedPaaSComputeTemplate> match(List<PaaSNodeTemplate> resources, Map<String, ComputeTemplate> deploymentSetup) {
        if (deploymentSetup == null) {
            String error = "Deployment setup for compute template is null";
            log.error(error);
            throw new BadConfigurationException(error);
        }
        List<MatchedPaaSComputeTemplate> matchedPaaSComputeTemplates = Lists.newArrayList();
        for (PaaSNodeTemplate resource : resources) {
            ComputeTemplate template = deploymentSetup.get(resource.getId());
            if (template == null) {
                String error = "Unmatched compute template for <" + resource.getId() + ">";
                log.error(error);
                throw new BadConfigurationException(error);
            }
            String paaSImageId = getPaaSImageId(template.getCloudImageId());
            String paaSFlavorId = getPaaSFlavorId(template.getCloudImageFlavorId());
            MatchedPaaSComputeTemplate paaSComputeTemplate = new MatchedPaaSComputeTemplate(resource, new PaaSComputeTemplate(paaSImageId, paaSFlavorId));
            matchedPaaSComputeTemplates.add(paaSComputeTemplate);
        }
        return matchedPaaSComputeTemplates;
    }
}
