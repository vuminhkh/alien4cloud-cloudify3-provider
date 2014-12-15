package alien4cloud.paas.cloudify3.configuration;

import java.util.Map;

import lombok.Getter;
import lombok.Setter;

import com.google.common.collect.Maps;

@Getter
@Setter
@SuppressWarnings("PMD.UnusedPrivateField")
public class CloudConfiguration {

    /**
     * Cloudify 3 Rest API URL
     */
    private String url = "http://129.185.67.85:8100";

    /**
     * The mapping for compute template id --> template configuration (image + flavor)
     */
    private Map<String, CloudifyComputeTemplate> computeTemplates = Maps.newHashMap();
}
