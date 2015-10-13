package alien4cloud.paas.cloudify3.blueprint;

import java.nio.file.Path;

import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.paas.cloudify3.configuration.MappingConfiguration;
import alien4cloud.paas.cloudify3.configuration.ProviderMappingConfiguration;
import alien4cloud.paas.cloudify3.service.PropertyEvaluatorService;
import alien4cloud.paas.cloudify3.service.model.CloudifyDeployment;

public class NetworkGenerationUtil extends NativeTypeGenerationUtil {

    public static final String FLOATING_IP_RELATIONSHIP_MAPPED_TO_KEY = "_a4c_floating_ip_relationship_mapped_to";

    public NetworkGenerationUtil(MappingConfiguration mappingConfiguration, ProviderMappingConfiguration providerMappingConfiguration,
            CloudifyDeployment alienDeployment, Path recipePath, PropertyEvaluatorService propertyEvaluatorService) {
        super(mappingConfiguration, providerMappingConfiguration, alienDeployment, recipePath, propertyEvaluatorService);
    }

    public String getFloatingIPNativeRelationshipType(IndexedNodeType toscaType) {
        return getNativePropertyValue(toscaType, FLOATING_IP_RELATIONSHIP_MAPPED_TO_KEY);
    }
}
