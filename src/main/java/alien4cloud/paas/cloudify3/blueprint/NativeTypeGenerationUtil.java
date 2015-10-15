package alien4cloud.paas.cloudify3.blueprint;

import java.nio.file.Path;

import alien4cloud.model.common.Tag;
import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.paas.cloudify3.configuration.MappingConfiguration;
import alien4cloud.paas.cloudify3.error.BadConfigurationException;
import alien4cloud.paas.cloudify3.service.PropertyEvaluatorService;
import alien4cloud.paas.cloudify3.service.model.CloudifyDeployment;

public abstract class NativeTypeGenerationUtil extends AbstractGenerationUtil {
    public static final String MAPPED_TO_KEY = "_a4c_c3_derived_from";

    public NativeTypeGenerationUtil(MappingConfiguration mappingConfiguration, CloudifyDeployment alienDeployment, Path recipePath,
            PropertyEvaluatorService propertyEvaluatorService) {
        super(mappingConfiguration, alienDeployment, recipePath, propertyEvaluatorService);
    }

    public String mapToCloudifyType(IndexedNodeType toscaNodeType) {
        for (Tag tag : toscaNodeType.getTags()) {
            if (tag.getName().equals(MAPPED_TO_KEY)) {
                return tag.getValue();
            }
        }
        throw new BadConfigurationException("In the type " + toscaNodeType.getElementId() + " the tag " + MAPPED_TO_KEY
                + " is mandatory in order to know which cloudify native type to map to");
    }

    public String getNativePropertyValue(IndexedNodeType toscaNodeType, String property) {
        return toscaNodeType.getProperties().get(property).getDefault();
    }
}
