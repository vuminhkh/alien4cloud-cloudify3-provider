package alien4cloud.paas.cloudify3.blueprint;

import java.nio.file.Path;

import org.apache.commons.lang3.StringUtils;

import alien4cloud.model.components.PropertyDefinition;
import alien4cloud.paas.cloudify3.configuration.MappingConfiguration;
import alien4cloud.paas.cloudify3.service.PropertyEvaluatorService;
import alien4cloud.paas.cloudify3.service.model.CloudifyDeployment;
import alien4cloud.tosca.normative.ToscaType;

public class ComputeGenerationUtil extends NativeTypeGenerationUtil {

    public ComputeGenerationUtil(MappingConfiguration mappingConfiguration, CloudifyDeployment alienDeployment, Path recipePath,
            PropertyEvaluatorService propertyEvaluatorService) {
        super(mappingConfiguration, alienDeployment, recipePath, propertyEvaluatorService);
    }

    public String getDefault(PropertyDefinition propertyDefinition) {
        if (ToscaType.isSimple(propertyDefinition.getType())) {
            return StringUtils.isNotBlank(propertyDefinition.getDefault()) ? propertyDefinition.getDefault() : "\"\"";
        } else {
            return "{}";
        }
    }

}
