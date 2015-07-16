package alien4cloud.paas.cloudify3.configuration;

import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import alien4cloud.paas.model.PaaSComputeTemplate;

@Getter
@Setter
public class ComputeTemplateConfiguration extends PaaSComputeTemplate {

    private Map<String, String> userData;
}
