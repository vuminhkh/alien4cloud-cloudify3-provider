package alien4cloud.paas.cloudify3.blueprint;

import java.nio.file.Path;
import java.util.List;

import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.paas.cloudify3.configuration.MappingConfiguration;
import alien4cloud.paas.cloudify3.configuration.ProviderMappingConfiguration;
import alien4cloud.paas.cloudify3.service.PropertyEvaluatorService;
import alien4cloud.paas.cloudify3.service.model.CloudifyDeployment;
import alien4cloud.paas.cloudify3.service.model.NativeType;

public abstract class NativeTypeGenerationUtil extends AbstractGenerationUtil {

    public NativeTypeGenerationUtil(MappingConfiguration mappingConfiguration, ProviderMappingConfiguration providerMappingConfiguration,
            CloudifyDeployment alienDeployment, Path recipePath, PropertyEvaluatorService propertyEvaluatorService) {
        super(mappingConfiguration, providerMappingConfiguration, alienDeployment, recipePath, propertyEvaluatorService);
    }

    protected String getMappedNativeType(IndexedNodeType type, String alienBaseType, String providerBaseType, List<IndexedNodeType> allDeploymentNativeTypes,
            String defaultType) {
        String nativeDerivedFrom = getMappedNativeDerivedFromType(type, alienBaseType, providerBaseType, allDeploymentNativeTypes);
        // If the native derive from is the provider base type, it means we should get the given default type
        if (providerBaseType.equals(nativeDerivedFrom)) {
            return defaultType;
        } else {
            return type.getElementId();
        }
    }

    protected String getMappedNativeDerivedFromType(IndexedNodeType typeToMap, String alienBaseType, String providerBaseType,
            List<IndexedNodeType> allDeploymentNativeTypes) {
        if (alienBaseType.equals(typeToMap.getElementId())) {
            return providerBaseType;
        }
        List<String> derivedFroms = typeToMap.getDerivedFrom();
        for (String derivedFrom : derivedFroms) {
            if (alienBaseType.equals(derivedFrom)) {
                return providerBaseType;
            }
            IndexedNodeType mostSuitableType = getTypeFromName(derivedFrom, allDeploymentNativeTypes);
            if (mostSuitableType != null) {
                return mostSuitableType.getElementId();
            }
        }
        return typeToMap.getElementId();
    }
}
