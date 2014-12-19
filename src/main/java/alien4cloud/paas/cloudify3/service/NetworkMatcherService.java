package alien4cloud.paas.cloudify3.service;

import java.util.List;

import org.springframework.stereotype.Component;

import alien4cloud.model.application.DeploymentSetup;
import alien4cloud.paas.cloudify3.service.model.MatchedPaaSNativeComponentTemplate;
import alien4cloud.paas.model.PaaSNodeTemplate;

/**
 * Handle network matching
 * 
 * @author Minh Khang VU
 */
@Component("cloudify-network-matcher-service")
public class NetworkMatcherService {

    public List<MatchedPaaSNativeComponentTemplate> match(List<PaaSNodeTemplate> networks, DeploymentSetup deploymentSetup) {
        // TODO implement this
        return null;
    }
}
