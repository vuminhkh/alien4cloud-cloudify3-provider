package alien4cloud.paas.cloudify3.blueprint;

import java.nio.file.Path;

import alien4cloud.model.components.PropertyDefinition;
import alien4cloud.paas.cloudify3.configuration.MappingConfiguration;
import alien4cloud.paas.cloudify3.configuration.ProviderMappingConfiguration;
import alien4cloud.paas.cloudify3.service.PropertyEvaluatorService;
import alien4cloud.paas.cloudify3.service.model.CloudifyDeployment;
import alien4cloud.tosca.normative.ToscaType;

public class ComputeGenerationUtil extends NativeTypeGenerationUtil {

    public ComputeGenerationUtil(MappingConfiguration mappingConfiguration, ProviderMappingConfiguration providerMappingConfiguration,
                                 CloudifyDeployment alienDeployment, Path recipePath, PropertyEvaluatorService propertyEvaluatorService) {
        super(mappingConfiguration, providerMappingConfiguration, alienDeployment, recipePath, propertyEvaluatorService);
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
            return propertyDefinition.getDefault() != null ? propertyDefinition.getDefault() : "\"\"";
        } else {
            return "{}";
        }
    }
}
