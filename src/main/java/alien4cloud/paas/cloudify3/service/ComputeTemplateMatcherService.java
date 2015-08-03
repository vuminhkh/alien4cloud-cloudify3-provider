package alien4cloud.paas.cloudify3.service;

import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import alien4cloud.model.cloud.AvailabilityZone;
import alien4cloud.model.cloud.CloudImage;
import alien4cloud.model.cloud.CloudImageFlavor;
import alien4cloud.model.cloud.ComputeTemplate;
import alien4cloud.paas.cloudify3.configuration.CloudConfigurationHolder;
import alien4cloud.paas.cloudify3.configuration.ComputeTemplateConfiguration;
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

    private Map<String, String> availabilityZoneMapping = Maps.newHashMap();

    @Resource
    private CloudConfigurationHolder cloudConfigurationHolder;

    public synchronized void configure(Map<CloudImage, String> imageMapping, Map<CloudImageFlavor, String> flavorMapping,
            Map<AvailabilityZone, String> availabilityZoneMapping) {
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
        if (availabilityZoneMapping != null) {
            for (Map.Entry<AvailabilityZone, String> availabilityZoneMappingEntry : availabilityZoneMapping.entrySet()) {
                this.availabilityZoneMapping.put(availabilityZoneMappingEntry.getKey().getId(), availabilityZoneMappingEntry.getValue());
            }
        }
    }

    public synchronized String getPaaSImageId(String alienImageId) {
        return this.imageMapping.get(alienImageId);
    }

    public synchronized String getPaaSFlavorId(String alienFlavorId) {
        return this.flavorMapping.get(alienFlavorId);
    }

    public List<MatchedPaaSComputeTemplate> match(List<PaaSNodeTemplate> resources, Map<String, ComputeTemplate> computeSetup,
            Map<String, AvailabilityZone> availabilityZoneSetup) {
        if (computeSetup == null) {
            String error = "Deployment setup for compute template is null";
            log.error(error);
            throw new BadConfigurationException(error);
        }
        List<MatchedPaaSComputeTemplate> matchedPaaSComputeTemplates = Lists.newArrayList();
        for (PaaSNodeTemplate resource : resources) {
            ComputeTemplate template = computeSetup.get(resource.getId());
            if (template == null) {
                String error = "Unmatched compute template for <" + resource.getId() + ">";
                log.error(error);
                throw new BadConfigurationException(error);
            }
            String paaSImageId = getPaaSImageId(template.getCloudImageId());
            String paaSFlavorId = getPaaSFlavorId(template.getCloudImageFlavorId());
            // TODO it's ugly, for byon only while waiting for paaS provider refactoring
            String description = template.getDescription();
            AvailabilityZone availabilityZone = availabilityZoneSetup.get(resource.getId());
            String paaSAvailabilityZoneId = null;
            if (availabilityZone != null) {
                paaSAvailabilityZoneId = availabilityZoneMapping.get(availabilityZone.getId());
            }
            MatchedPaaSComputeTemplate paaSComputeTemplate = new MatchedPaaSComputeTemplate(resource, new PaaSComputeTemplate(paaSImageId, paaSFlavorId,
                    paaSAvailabilityZoneId, getUserData(paaSImageId, paaSFlavorId, description)));
            matchedPaaSComputeTemplates.add(paaSComputeTemplate);
        }
        return matchedPaaSComputeTemplates;
    }

    private Map<String, String> getUserData(String image, String flavor, String desc) {
        ComputeTemplateConfiguration[] templateConfigurations = cloudConfigurationHolder.getConfiguration().getTemplateConfigurations();
        if (templateConfigurations == null) {
            return null;
        }
        for (ComputeTemplateConfiguration templateConfiguration : templateConfigurations) {
            if (templateConfiguration.getFlavorId() == null || templateConfiguration.getImageId() == null) {
                continue;
            } else if (templateConfiguration.getFlavorId().equals(flavor) && templateConfiguration.getImageId().equals(image)) {
                if (templateConfiguration.getDescription() == null) {
                    if (desc == null) {
                        return templateConfiguration.getUserData();
                    }
                } else {
                    if (desc != null && templateConfiguration.getDescription().equals(desc)) {
                        return templateConfiguration.getUserData();
                    }
                }
            }
        }
        return null;
    }
}
