package alien4cloud.paas.cloudify3.blueprint;

import java.nio.file.Path;

import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.paas.cloudify3.configuration.MappingConfiguration;
import alien4cloud.paas.cloudify3.service.PropertyEvaluatorService;
import alien4cloud.paas.cloudify3.service.model.CloudifyDeployment;

public abstract class NativeTypeGenerationUtil extends AbstractGenerationUtil {

    public static final String MAPPED_TO_KEY = "_a4c_mapped_to";

    public NativeTypeGenerationUtil(MappingConfiguration mappingConfiguration, CloudifyDeployment alienDeployment, Path recipePath,
            PropertyEvaluatorService propertyEvaluatorService) {
        super(mappingConfiguration, alienDeployment, recipePath, propertyEvaluatorService);
    }

    public String mapToCloudifyType(IndexedNodeType toscaNodeType) {
        return getNativePropertyValue(toscaNodeType, MAPPED_TO_KEY);
    }

    public String getCloudifyNativeType(IndexedNodeType toscaNodeType) {
        return getNativePropertyValue(toscaNodeType, mappingConfiguration.getNativeTypePropertyName());
    }

    public String getNativePropertyValue(IndexedNodeType toscaNodeType, String property) {
        return toscaNodeType.getProperties().get(property).getDefault();
    }
}
