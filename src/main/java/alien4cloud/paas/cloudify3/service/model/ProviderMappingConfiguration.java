package alien4cloud.paas.cloudify3.service.model;

import java.util.Map;
import java.util.Set;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProviderMappingConfiguration {

    private Set<String> imports;

    private String dslVersion;

    private String generatedTypePrefix;

    private ProviderNativeTypes nativeTypes;

    private Map<String, String> normativeTypes;

    @Getter
    @Setter
    public static class ProviderNativeTypes {
        private String computeType;
        private String networkType;
        private String blockStorageType;
    }
}
