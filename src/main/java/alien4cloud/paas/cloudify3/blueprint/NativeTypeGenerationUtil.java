package alien4cloud.paas.cloudify3.blueprint;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import alien4cloud.model.common.Tag;
import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.components.FunctionPropertyValue;
import alien4cloud.model.components.IValue;
import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.model.components.PropertyValue;
import alien4cloud.model.components.ScalarPropertyValue;
import alien4cloud.paas.cloudify3.configuration.MappingConfiguration;
import alien4cloud.paas.cloudify3.error.BadConfigurationException;
import alien4cloud.paas.cloudify3.service.PropertyEvaluatorService;
import alien4cloud.paas.cloudify3.service.model.CloudifyDeployment;
import alien4cloud.paas.cloudify3.util.mapping.PropertiesMappingUtil;
import alien4cloud.paas.cloudify3.util.mapping.PropertyMapping;
import alien4cloud.paas.cloudify3.util.mapping.PropertyValueUtil;
import alien4cloud.paas.exception.NotSupportedException;
import alien4cloud.utils.TagUtil;

import com.google.common.collect.Maps;

public class NativeTypeGenerationUtil extends AbstractGenerationUtil {

    public static final String MAPPED_TO_KEY = "_a4c_c3_derived_from";

    public NativeTypeGenerationUtil(MappingConfiguration mappingConfiguration, CloudifyDeployment alienDeployment, Path recipePath,
            PropertyEvaluatorService propertyEvaluatorService) {
        super(mappingConfiguration, alienDeployment, recipePath, propertyEvaluatorService);
    }

    /**
     * Check if a property has been defined with a non null and not empty value.
     *
     * @param properties The map of properties in which to look.
     * @param property The name of the property.
     * @return True if a value has been defined, false if not.
     */
    public boolean hasPropertyValue(Map<String, AbstractPropertyValue> properties, String property) {
        if (properties == null) {
            return false;
        }
        AbstractPropertyValue propertyValue = properties.get(property);
        if (propertyValue == null) {
            return false;
        }
        if (propertyValue instanceof ScalarPropertyValue) {
            String value = ((ScalarPropertyValue) propertyValue).getValue();
            if (value == null || value.isEmpty()) {
                return false;
            }
            // there is a non-null and not empty property value.
            return true;
        }
        return false;
    }

    /**
     * Utility method used by velocity generator in order to find the cloudify type from a cloudify tosca type.
     *
     * @param toscaNodeType The tosca node type.
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
            if (propertyEntry.getValue() != null) {
                buffer.append("\n").append(indent(indentLevel)).append(propertyEntry.getKey()).append(": ")
                        .append(formatPropertyValue(indentLevel + 1, propertyEntry.getValue()));
            }
        }
        return buffer.toString();
    }

    /**
     * Apply properties mapping and then format properties for cloudify blueprint.
     *
     * @param indentLevel The indentation level for the properties.
     * @param properties The properties values map.
     * @param propMappings The mapping configuration to map values.
     * @return The formatted properties string to insert in the blueprint.
     */
    public String formatProperties(int indentLevel, Map<String, AbstractPropertyValue> properties, Map<String, PropertyMapping> propMappings) {
        Map<String, AbstractPropertyValue> mappedProperties = PropertyValueUtil.mapProperties(propMappings, properties);
        return formatProperties(indentLevel, mappedProperties);
    }

    private String formatPropertyValue(int indentLevel, AbstractPropertyValue propertyValue) {
        return formatPropertyValue(true, indentLevel, propertyValue);
    }

    private String formatPropertyValue(boolean appendLf, int indentLevel, AbstractPropertyValue propertyValue) {
        if (propertyValue instanceof PropertyValue) {
            return formatValue(appendLf, indentLevel, ((PropertyValue) propertyValue).getValue());
        } else {
            throw new NotSupportedException("Do not support other types than PropertyValue");
        }
    }

    private String formatValue(int indentLevel, Object value) {
        return formatValue(true, indentLevel, value);
    }

    private String formatValue(boolean appendLf, int indentLevel, Object value) {
        if (value instanceof String) {
            return (String) value;
        } else if (value instanceof Map) {
            return formatMapValue(appendLf, indentLevel, (Map<String, Object>) value);
        } else if (value instanceof Object[]) {
            return formatListValue(indentLevel, Arrays.asList((Object[]) value));
        } else if (value instanceof List) {
            return formatListValue(indentLevel, (List<Object>) value);
        } else if (value instanceof PropertyValue) {
            return formatPropertyValue(appendLf, indentLevel, (PropertyValue) value);
        } else {
            throw new NotSupportedException("Do not support other types than string map and list");
        }
    }

    private String formatMapValue(boolean appendFirstLf, int indentLevel, Map<String, Object> value) {
        StringBuilder buffer = new StringBuilder();
        boolean isFirst = true;
        for (Map.Entry<String, Object> valueEntry : value.entrySet()) {
            if (valueEntry.getValue() != null) {
                if (!isFirst || appendFirstLf) {
                    buffer.append("\n").append(indent(indentLevel));
                }
                buffer.append(valueEntry.getKey()).append(": ").append(formatValue(indentLevel + 1, valueEntry.getValue()));
                if (isFirst) {
                    isFirst = false;
                }
            }
        }
        return buffer.toString();
    }

    private String formatListValue(int indentLevel, List<Object> value) {
        StringBuilder buffer = new StringBuilder();
        for (Object element : value) {
            if (element != null) {
                buffer.append("\n").append(indent(indentLevel)).append("- ").append(formatValue(false, indentLevel + 1, element));
            }
        }
        return buffer.toString();
    }

    public Map<String, PropertyMapping> loadPropertyMapping(IndexedNodeType type, String tagName) {
        return PropertiesMappingUtil.loadPropertyMapping(tagName, type);
    }

    public Map<String, FunctionPropertyValue> getAttributesMapping(Map<String, IValue> attributes) {
        Map<String, FunctionPropertyValue> functions = Maps.newHashMap();
        for (Map.Entry<String, IValue> attributeEntry : attributes.entrySet()) {
            if (attributeEntry.getValue() instanceof FunctionPropertyValue) {
                functions.put(attributeEntry.getKey(), (FunctionPropertyValue) attributeEntry.getValue());
            }
        }
        return functions;
    }

    /**
     * Get the value of the _a4c_persistent_resource_id tag.
     *
     * @param tags The list of tags in which to search.
     * @return The value of the _a4c_persistent_resource_id tag or null if the tag is not present in the list.
     */
    public String getPersistentResourceId(List<Tag> tags) {
        return TagUtil.getTagValue(tags, "_a4c_persistent_resource_id");
    }

}
