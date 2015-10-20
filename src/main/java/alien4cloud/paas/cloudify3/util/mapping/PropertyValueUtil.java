package alien4cloud.paas.cloudify3.util.mapping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

import alien4cloud.model.components.*;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Simple utility that deep
 */
@Slf4j
public final class PropertyValueUtil {
    /**
     * Map properties from tosca to cloudify properties.
     *
     * @param propMappings
     *            The property mappings
     * @param properties
     *            The properties to map.
     */
    public void mapProperties(Map<String, PropertyMapping> propMappings, Map<String, AbstractPropertyValue> properties) {
        if (propMappings == null || propMappings.isEmpty()) {
            // do not change prop map
            return;
        }

        Map<String, AbstractPropertyValue> mappedProperties = Maps.newHashMap();

        for (Map.Entry<String, AbstractPropertyValue> property : properties.entrySet()) {
            PropertyMapping mapping = propMappings.get(property.getKey());

            if (mapping == null || mapping.getSourcePaths().size() == 0) {
                // if the property is not mapped, just keep it as is.
                PropertyValue mappedProperty = PropertyValueUtil.merge((PropertyValue) property.getValue(),
                        (PropertyValue) mappedProperties.get(property.getKey()));
                mappedProperties.put(property.getKey(), mappedProperty);
            } else {
                // if the property is mapped then apply the mapping.
                for (int i = 0; i < mapping.getSourcePaths().size(); i++) {
                    String sourcePath = mapping.getSourcePaths().get(i);
                    TargetMapping targetMapping = mapping.getTargetMapping().get(i);

                    if (targetMapping.getProperty() == null) {
                        continue; // skip this property
                    }

                    Object sourceValue = extractValue((PropertyValue) property, sourcePath);
                    // if there is a specified unit, convert the value to the expected unit.
                    if (targetMapping.getUnit() != null) {
                        // need the property type to IComparablePropertyType
                        sourceValue = convertValue((String) sourceValue, targetMapping.getUnit());
                    }

                    PropertyValue targetProperty = (PropertyValue) mappedProperties.get(targetMapping.getProperty());
                    // BeanWrapper rootBw = new BeanWrapperImpl(t);
                    if (targetMapping.getPath() == null) {
                        // set the property with the value
                        PropertyValue mappedProperty = PropertyValueUtil.merge(propertyValueFromObject(sourceValue),
                                targetProperty);
                        mappedProperties.put(targetMapping.getProperty(), mappedProperty);
                    } else {
                        // extract the value
                        Object targetValue = sourceValue;
                        if (targetProperty != null) {
                            targetValue = extractValue(targetProperty, targetMapping.getPath());
                            merge(sourceValue, targetValue);
                        } else {
                            targetProperty = propertyValueFromObject(new HashMap<>());
                        }
                        // set the value
                        BeanWrapper rootBw = new BeanWrapperImpl(targetProperty.getValue());
                        rootBw.setPropertyValue(targetMapping.getPath(), targetValue);
                    }
                }
            }
        }
    }

    private String convertValue(String sourceValue, String unit) {
        return sourceValue;
    }

    private Object extractValue(PropertyValue property, String path) {
        if (path == null) {
            return property.getValue();
        }
        BeanWrapper target = new BeanWrapperImpl(property.getValue());
        return target.getPropertyValue(path);
    }

    /**
     * Return a property value based on the type of an object.
     * 
     * @param object
     *            The object to wrap into a PropertyValue.
     * @return A property value that wraps the given object.
     */
    private PropertyValue propertyValueFromObject(Object object) {
        if (object instanceof Map) {
            return new ComplexPropertyValue((Map<String, Object>) object);
        }
        if (object instanceof List) {
            return new ListPropertyValue((List<Object>) object);
        }
        return new ScalarPropertyValue((String) object);
    }

    /**
     * Merge a source property value into a target property. If target is not null, the source is merged directly in the target object and the target object is
     * returned.
     * 
     * @param source
     *            The source property value.
     * @param target
     *            The target property value.
     * @return The merged object.
     */
    public static PropertyValue merge(PropertyValue source, PropertyValue target) {
        if (source == null) {
            return target;
        }
        if (target == null) {
            return deepClone(source);
        }
        // perform the property merge.
        Object clonedSource = deepClone(source.getValue());
        // merge the clonedSource into the target value
        merge(clonedSource, target.getValue());
        return target;
    }

    private static void merge(Object sourcePropertyValueObject, Object targetPropertyValueObject) {
        // TODO do the merge

    }

    /**
     * Simple utility that deep clones an object made of Map, List and String.
     * 
     * @param propertyValue
     *            The property value to clone.
     */
    public static PropertyValue deepClone(PropertyValue propertyValue) {
        if (propertyValue instanceof ListPropertyValue) {
            return new ListPropertyValue(deepClone(((ListPropertyValue) propertyValue).getValue()));
        } else if (propertyValue instanceof ComplexPropertyValue) {
            return new ComplexPropertyValue(deepClone(((ComplexPropertyValue) propertyValue).getValue()));
        }
        // must be a scalar property value
        return new ScalarPropertyValue(((ScalarPropertyValue) propertyValue).getValue());
    }

    /**
     * Deep clone an object that compose a property value (every element is a String, a Map or a List).
     * 
     * @param propertyValueObject
     *            The object to clone.
     * @return A clone of the property value object.
     */
    private static <T> T deepClone(T propertyValueObject) {
        T clone;
        if (propertyValueObject instanceof Map) {
            Map map = Maps.newHashMap();
            Map<String, ?> propertyValueObjectMap = (Map) propertyValueObject;
            for (Map.Entry<String, ?> entry : propertyValueObjectMap.entrySet()) {
                // key is a string in property values structure
                map.put(entry.getKey(), deepClone(entry.getValue()));
            }
            clone = (T) map;
        } else if (propertyValueObject instanceof List) {
            List list = Lists.newArrayList();
            for (Object element : ((List) propertyValueObject)) {
                list.add(deepClone(element));
            }
            clone = (T) list;
        } else if (propertyValueObject instanceof String) {
            clone = propertyValueObject;
        } else {
            log.warn("Property deep clone is just making a simple copy for type {} and value {}. Expecting a String for these situations.", propertyValueObject
                    .getClass().getName(), propertyValueObject);
            clone = propertyValueObject;
        }

        return clone;
    }
}