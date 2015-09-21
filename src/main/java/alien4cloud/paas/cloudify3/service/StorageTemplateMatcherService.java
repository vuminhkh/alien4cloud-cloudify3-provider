package alien4cloud.paas.cloudify3.service;

import org.springframework.stereotype.Component;

import alien4cloud.paas.cloudify3.model.StorageTemplate;

/**
 * Handle block storage matching
 */
@Component("cloudify-storage-matcher-service")
public class StorageTemplateMatcherService extends AbstractResourceMatcherService<StorageTemplate> {

}
