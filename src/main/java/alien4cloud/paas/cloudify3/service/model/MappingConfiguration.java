package alien4cloud.paas.cloudify3.service.model;

import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MappingConfiguration {

    private String dslVersion;

    private String generatedTypePrefix;

    private Map<String, String> normativeTypes;

}
