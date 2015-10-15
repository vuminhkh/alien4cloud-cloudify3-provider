package alien4cloud.paas.cloudify3.blueprint;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.components.PropertyValue;
import alien4cloud.paas.cloudify3.configuration.MappingConfiguration;
import alien4cloud.paas.cloudify3.service.PropertyEvaluatorService;
import alien4cloud.paas.cloudify3.service.model.CloudifyDeployment;
import alien4cloud.paas.exception.NotSupportedException;

public class CommonGenerationUtil extends AbstractGenerationUtil {

    public CommonGenerationUtil(MappingConfiguration mappingConfiguration, CloudifyDeployment alienDeployment, Path recipePath,
            PropertyEvaluatorService propertyEvaluatorService) {
        super(mappingConfiguration, alienDeployment, recipePath, propertyEvaluatorService);
    }

    public String indent(int indentLevel) {
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < indentLevel; i++) {
            buffer.append("  ");
        }
        return buffer.toString();
    }

    public String formatProperties(int indentLevel, Map<String, AbstractPropertyValue> properties) {
        StringBuilder buffer = new StringBuilder();
        for (Map.Entry<String, AbstractPropertyValue> propertyEntry : properties.entrySet()) {
            if (propertyEntry.getValue() != null && !propertyEntry.getKey().startsWith("_a4c_")) {
                buffer.append("\n").append(indent(indentLevel)).append(propertyEntry.getKey()).append(": ")
                        .append(formatPropertyValue(indentLevel + 1, propertyEntry.getValue()));
            }
        }
        return buffer.toString();
    }

    private String formatPropertyValue(int indentLevel, AbstractPropertyValue propertyValue) {
        if (propertyValue instanceof PropertyValue) {
            return formatValue(indentLevel, ((PropertyValue) propertyValue).getValue());
        } else {
            throw new NotSupportedException("Do not support other types than PropertyValue");
        }
    }

    private String formatValue(int indentLevel, Object value) {
        if (value instanceof String) {
            return (String) value;
        } else if (value instanceof Map) {
            return formatMapValue(indentLevel, (Map<String, Object>) value);
        } else if (value instanceof Object[]) {
            return formatListValue(indentLevel, Arrays.asList((Object[]) value));
        } else if (value instanceof List) {
            return formatListValue(indentLevel, (List<Object>) value);
        } else {
            throw new NotSupportedException("Do not support other types than string map and list");
        }
    }

    private String formatMapValue(int indentLevel, Map<String, Object> value) {
        StringBuilder buffer = new StringBuilder();
        for (Map.Entry<String, Object> valueEntry : value.entrySet()) {
            if (valueEntry.getValue() != null) {
                buffer.append("\n").append(indent(indentLevel)).append(valueEntry.getKey()).append(": ")
                        .append(formatValue(indentLevel + 1, valueEntry.getValue()));
            }
        }
        return buffer.toString();
    }

    private String formatListValue(int indentLevel, List<Object> value) {
        StringBuilder buffer = new StringBuilder();
        for (Object element : value) {
            if (element != null) {
                buffer.append("\n").append(indent(indentLevel)).append("- ").append(formatValue(indentLevel + 1, element));
            }
        }
        return buffer.toString();
    }
}
