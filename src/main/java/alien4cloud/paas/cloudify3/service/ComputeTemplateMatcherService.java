package alien4cloud.paas.cloudify3.service;

import java.util.Map;

import javax.annotation.Resource;

import org.springframework.stereotype.Component;

import alien4cloud.model.cloud.CloudImage;
import alien4cloud.model.cloud.CloudImageFlavor;
import alien4cloud.model.cloud.CloudResourceMatcherConfig;
import alien4cloud.model.cloud.ComputeTemplate;
import alien4cloud.paas.cloudify3.configuration.CloudConfigurationHolder;
import alien4cloud.paas.cloudify3.configuration.CloudifyComputeTemplate;

import com.google.common.collect.Maps;

/**
 * Handle compute template mapping
 *
 * @author Minh Khang VU
 */
@Component("cloudify-compute-template-matcher-service")
public class ComputeTemplateMatcherService extends AbstractResourceMatcherService<ComputeTemplate> {

    @Resource(name = "cloudify-configuration-holder")
    private CloudConfigurationHolder cloudConfigurationHolder;

    public Map<ComputeTemplate, String> getComputeTemplateMapping(CloudResourceMatcherConfig cloudResourceMatcherConfig) {
        Map<CloudImage, String> imageMapping = cloudResourceMatcherConfig.getImageMapping();
        Map<CloudImageFlavor, String> flavorsMapping = cloudResourceMatcherConfig.getFlavorMapping();
        Map<CloudifyComputeTemplate, String> paaSComputeTemplates = cloudConfigurationHolder.getConfiguration().getReverseComputeTemplatesMapping();
        Map<ComputeTemplate, String> templates = Maps.newHashMap();
        for (Map.Entry<CloudImage, String> imageMappingEntry : imageMapping.entrySet()) {
            for (Map.Entry<CloudImageFlavor, String> flavorMappingEntry : flavorsMapping.entrySet()) {
                String generatedPaaSResourceId = paaSComputeTemplates.get(new CloudifyComputeTemplate(imageMappingEntry.getValue(),
                        flavorMappingEntry.getValue()));
                if (generatedPaaSResourceId != null) {
                    templates.put(new ComputeTemplate(imageMappingEntry.getKey().getId(), flavorMappingEntry.getKey().getId()),
                            generatedPaaSResourceId);
                }
            }
        }
        return templates;
    }
}
