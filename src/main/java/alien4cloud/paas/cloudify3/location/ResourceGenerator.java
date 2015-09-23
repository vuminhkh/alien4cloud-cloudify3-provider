package alien4cloud.paas.cloudify3.location;

import java.util.List;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import alien4cloud.model.orchestrators.locations.LocationResourceTemplate;
import alien4cloud.orchestrators.locations.services.LocationResourceGeneratorService;
import alien4cloud.orchestrators.locations.services.LocationResourceGeneratorService.ComputeContext;
import alien4cloud.orchestrators.locations.services.LocationResourceGeneratorService.ImageFlavorContext;
import alien4cloud.orchestrators.plugin.ILocationResourceAccessor;

@Component
@Slf4j
public class ResourceGenerator {

    private static final String IMAGE_ID_PROP = "image";
    private static final String FLAVOR_ID_PROP = "flavor";
    private static final String DEFAULT_RESOURCE_NAME_PREFIX = "GeneratedCompute";

    @Inject
    private LocationResourceGeneratorService resourceGeneratorService;

    public List<LocationResourceTemplate> generateComputes(String computeType, String imageType, String flavorType, ILocationResourceAccessor resourceAccessor) {
        ImageFlavorContext imageContext = resourceGeneratorService.buildContext(imageType, "id", resourceAccessor);
        ImageFlavorContext flavorContext = resourceGeneratorService.buildContext(flavorType, "id", resourceAccessor);
        boolean canProceed = true;
        if (CollectionUtils.isEmpty(imageContext.getTemplates())) {
            log.warn("At least one configured image resource is required for the auto-configuration");
            canProceed = false;
        }
        if (CollectionUtils.isEmpty(flavorContext.getTemplates())) {
            log.warn("At least one configured flavor resource is required for the auto-configuration");
            canProceed = false;
        }
        if (!canProceed) {
            log.warn("Skipping auto configuration");
            return null;
        }
        ComputeContext computeContext = resourceGeneratorService.buildComputeContext(computeType, DEFAULT_RESOURCE_NAME_PREFIX,
                IMAGE_ID_PROP, FLAVOR_ID_PROP, resourceAccessor);

        return resourceGeneratorService.generateComputeFromImageAndFlavor(imageContext, flavorContext, computeContext, resourceAccessor);
    }
}
