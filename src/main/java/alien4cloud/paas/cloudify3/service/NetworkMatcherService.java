package alien4cloud.paas.cloudify3.service;

import org.springframework.stereotype.Component;

import alien4cloud.model.cloud.NetworkTemplate;

/**
 * Handle network matching
 * 
 * @author Minh Khang VU
 */
@Component("cloudify-network-matcher-service")
public class NetworkMatcherService extends AbstractResourceMatcherService<NetworkTemplate> {

}
