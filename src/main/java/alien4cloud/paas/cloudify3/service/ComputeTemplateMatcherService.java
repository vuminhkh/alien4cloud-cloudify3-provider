package alien4cloud.paas.cloudify3.service;

import org.springframework.stereotype.Component;

import alien4cloud.model.cloud.ComputeTemplate;

/**
 * Handle compute template mapping
 *
 * @author Minh Khang VU
 */
@Component("cloudify-compute-template-matcher-service")
public class ComputeTemplateMatcherService extends AbstractResourceMatcherService<ComputeTemplate> {

}
