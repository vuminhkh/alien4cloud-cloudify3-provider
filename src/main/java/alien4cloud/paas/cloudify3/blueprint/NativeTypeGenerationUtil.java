package alien4cloud.paas.cloudify3.blueprint;

import java.nio.file.Path;

import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.paas.cloudify3.configuration.MappingConfiguration;
import alien4cloud.paas.cloudify3.error.BadConfigurationException;
import alien4cloud.paas.cloudify3.service.PropertyEvaluatorService;
import alien4cloud.paas.cloudify3.service.model.CloudifyDeployment;
import alien4cloud.utils.TagUtil;

public abstract class NativeTypeGenerationUtil extends AbstractGenerationUtil {
    public static final String MAPPED_TO_KEY = "_a4c_c3_derived_from";

    public NativeTypeGenerationUtil(MappingConfiguration mappingConfiguration, CloudifyDeployment alienDeployment, Path recipePath,
            PropertyEvaluatorService propertyEvaluatorService) {
        super(mappingConfiguration, alienDeployment, recipePath, propertyEvaluatorService);
    }

    /**
     * Utility method used by velocity generator in order to find the cloudify type from a cloudify tosca type.
     * 
     * @param toscaNodeType
     *            The tosca node type.
     * @return The matching cloudify's type.
     */
    public String mapToCloudifyType(IndexedNodeType toscaNodeType) {
        String cloudifyType = TagUtil.getTagValue(toscaNodeType.getTags(), MAPPED_TO_KEY);
        if (cloudifyType == null) {
            throw new BadConfigurationException("In the type " + toscaNodeType.getElementId() + " the tag " + MAPPED_TO_KEY
                    + " is mandatory in order to know which cloudify native type to map to");
        }
        return cloudifyType;
    }

    public String getNativePropertyValue(IndexedNodeType toscaNodeType, String property) {
        return toscaNodeType.getProperties().get(property).getDefault();
    }
}
