package alien4cloud.paas.cloudify3.blueprint;

import java.nio.file.Path;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import alien4cloud.model.components.FunctionPropertyValue;
import alien4cloud.model.components.IValue;
import alien4cloud.model.components.PropertyDefinition;
import alien4cloud.paas.cloudify3.configuration.MappingConfiguration;
import alien4cloud.paas.cloudify3.service.PropertyEvaluatorService;
import alien4cloud.paas.cloudify3.service.model.CloudifyDeployment;
import alien4cloud.tosca.normative.ToscaType;

import com.google.common.collect.Maps;

public class ComputeGenerationUtil extends NativeTypeGenerationUtil {

    public ComputeGenerationUtil(MappingConfiguration mappingConfiguration, CloudifyDeployment alienDeployment, Path recipePath,
            PropertyEvaluatorService propertyEvaluatorService) {
        super(mappingConfiguration, alienDeployment, recipePath, propertyEvaluatorService);
    }

    public String formatTextWithIndentation(int spaceNumber, String text) {
        String[] lines = text.split("\n");
        StringBuilder formattedTextBuffer = new StringBuilder();
        StringBuilder indentationBuffer = new StringBuilder();
        for (int i = 0; i < spaceNumber; i++) {
            indentationBuffer.append(" ");
        }
        String indentation = indentationBuffer.toString();
        for (String line : lines) {
            formattedTextBuffer.append(indentation).append(line).append("\n");
        }
        return formattedTextBuffer.toString();
    }

    public String getDefault(PropertyDefinition propertyDefinition) {
        if (ToscaType.isSimple(propertyDefinition.getType())) {
            return StringUtils.isNotBlank(propertyDefinition.getDefault()) ? propertyDefinition.getDefault() : "\"\"";
        } else {
            return "{}";
        }
    }

}
