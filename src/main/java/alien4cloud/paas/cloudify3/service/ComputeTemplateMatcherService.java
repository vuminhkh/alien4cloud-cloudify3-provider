package alien4cloud.paas.cloudify3.service;

import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.springframework.stereotype.Component;

import alien4cloud.model.cloud.CloudResourceMatcherConfig;
import alien4cloud.model.cloud.ComputeTemplate;
import alien4cloud.model.cloud.MatchedCloudImage;
import alien4cloud.model.cloud.MatchedCloudImageFlavor;
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
        List<MatchedCloudImage> images = cloudResourceMatcherConfig.getMatchedImages();
        List<MatchedCloudImageFlavor> flavors = cloudResourceMatcherConfig.getMatchedFlavors();
        Map<CloudifyComputeTemplate, String> paaSComputeTemplates = cloudConfigurationHolder.getConfiguration().getReverseComputeTemplatesMapping();
        Map<ComputeTemplate, String> templates = Maps.newHashMap();
        for (MatchedCloudImage matchedCloudImage : images) {
            for (MatchedCloudImageFlavor matchedCloudImageFlavor : flavors) {
                String generatedPaaSResourceId = paaSComputeTemplates.get(new CloudifyComputeTemplate(matchedCloudImage.getPaaSResourceId(),
                        matchedCloudImageFlavor.getPaaSResourceId()));
                if (generatedPaaSResourceId != null) {
                    templates.put(new ComputeTemplate(matchedCloudImage.getResource().getId(), matchedCloudImageFlavor.getResource().getId()),
                            generatedPaaSResourceId);
                }
            }
        }
        return templates;
    }
}
