package alien4cloud.paas.cloudify3.matcher;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.google.common.collect.Maps;

import alien4cloud.model.cloud.CloudResourceMatcherConfig;
import alien4cloud.model.cloud.ComputeTemplate;

/**
 * Handle compute template mapping, it's a reverse mapping to
 *
 * @author Minh Khang VU
 */
@Component
public class ComputeTemplateMatcher {

    private Map<ComputeTemplate, String> alienTemplateToCloudifyTemplateMapping = Maps.newHashMap();

    /**
     * Match a cloudify template based on the compute node.
     *
     * @param computeNode The compute node.
     * @return The template that matches the given compute node.
     */
    public synchronized String getTemplateId(ComputeTemplate computeNode) {
        return alienTemplateToCloudifyTemplateMapping.get(computeNode);
    }

    public synchronized void configure(CloudResourceMatcherConfig config) {
        alienTemplateToCloudifyTemplateMapping = config.getComputeTemplateMapping();
    }
}
