package alien4cloud.paas.cloudify3.service.model;

import java.util.Map;
import java.util.Set;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProviderMappingConfiguration {

    private Set<String> imports;

    private ProviderNativeTypes nativeTypes;

    @Getter
    @Setter
    public static class ProviderNativeTypes {
        private String computeType;
        private String networkType;
        private String subnetType;
        private String blockStorageType;
        private String floatingIpType;
        private String floatingIpRelationshipType;
    }
}
