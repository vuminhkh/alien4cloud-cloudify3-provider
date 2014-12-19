package alien4cloud.paas.cloudify3.service;

import java.util.List;

import org.springframework.stereotype.Component;

import alien4cloud.model.application.DeploymentSetup;
import alien4cloud.paas.cloudify3.service.model.MatchedPaaSNativeComponentTemplate;
import alien4cloud.paas.model.PaaSNodeTemplate;

/**
 * Handle volume matching
 * 
 * @author Minh Khang VU
 */
@Component("cloudify-volume-matcher-service")
public class VolumeMatcherService {

    public List<MatchedPaaSNativeComponentTemplate> match(List<PaaSNodeTemplate> volumes, DeploymentSetup deploymentSetup) {
        // TODO implement this
        return null;
    }
}
