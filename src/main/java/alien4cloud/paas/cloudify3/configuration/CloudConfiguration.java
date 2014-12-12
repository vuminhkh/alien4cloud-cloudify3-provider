package alien4cloud.paas.cloudify3.configuration;

import java.util.Map;

import com.google.common.collect.Maps;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@SuppressWarnings("PMD.UnusedPrivateField")
public class CloudConfiguration {

    /**
     * Cloudify 3 Rest API URL
     */
    private String url = "http://11.0.0.7:8100";

    /**
     * The mapping for compute template id --> template configuration (image + flavor)
     */
    private Map<String, CloudifyComputeTemplate> computeTemplates = Maps.newHashMap();
}
