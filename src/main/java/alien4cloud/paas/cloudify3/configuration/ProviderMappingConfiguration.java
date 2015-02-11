package alien4cloud.paas.cloudify3.configuration;

import java.util.Map;
import java.util.Set;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProviderMappingConfiguration {

    private Set<String> imports;

    private ProviderNativeTypes nativeTypes;

    private Map<String, Map<String, String>> attributes;

    @Getter
    @Setter
    public static class ProviderNativeTypes {
        private String computeType;
        private String networkType;
        private String subnetType;
        private String volumeType;
        private String floatingIpType;
        private String floatingIpRelationshipType;
        private String volumeAttachRelationshipType;
    }
}
