package alien4cloud.paas.cloudify3.util.mapping;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Component;

import alien4cloud.model.components.IndexedInheritableToscaElement;
import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.model.components.IndexedToscaElement;
import alien4cloud.model.components.PropertyDefinition;
import alien4cloud.paas.wf.WorkflowsBuilderService.TopologyContext;
import alien4cloud.tosca.normative.ToscaType;
import alien4cloud.utils.TagUtil;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;

/**
 * Perform mapping of properties
 */
@Slf4j
@Component
public class PropertiesMappingUtil {
    private static final String PROP_MAPPING_TAG_KEY = "_a4c_c3_prop_map";
    private static ObjectMapper mapper = new ObjectMapper();

    /**
     * Load the property mappings defined in tags
     *
     * @param nodeTypes
     *            The list of node types for which to extract property mappings.
     * @param topologyContext
     * @return A map <nodeType, <toscaPath, cloudifyPath>>>
     */
    public static Map<String, Map<String, IPropertyMapping>> loadPropertyMappings(List<IndexedNodeType> nodeTypes, TopologyContext topologyContext) {
        TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {
        };

        // <nodeType, <toscaPath, cloudifyPath>>>
        Map<String, Map<String, IPropertyMapping>> propertyMappingsByTypes = Maps.newLinkedHashMap();
        for (IndexedNodeType nodeType : nodeTypes) {
            deeplyLoadPropertyMapping(PROP_MAPPING_TAG_KEY, propertyMappingsByTypes, nodeType, topologyContext);
        }
        return propertyMappingsByTypes;
    }

    /**
     * deeply load properties mappings for a type. This includes the mappings for the types, and eventually his properties dataTypes
     *
     * @param propertyMappingsByTypes
     * @param inheritableToscaElement
     * @param topologyContext
     */
    private static void deeplyLoadPropertyMapping(String fromTag, Map<String, Map<String, IPropertyMapping>> propertyMappingsByTypes,
            IndexedInheritableToscaElement inheritableToscaElement, TopologyContext topologyContext) {
        // do not proceed if mapping already exists
        if (inheritableToscaElement == null || propertyMappingsByTypes.containsKey(inheritableToscaElement.getElementId())) {
            return;
        }

        Map<String, IPropertyMapping> mappings = loadPropertyMapping(fromTag, inheritableToscaElement);
        if (MapUtils.isNotEmpty(mappings)) {
            propertyMappingsByTypes.put(inheritableToscaElement.getElementId(), mappings);
        }

        loadPropertiesDataTypesMapping(fromTag, propertyMappingsByTypes, inheritableToscaElement, topologyContext);
    }

    /**
     * A property Data type can also have properties mapping definition. Load it, and add a marker on the related property
     *
     * @param fromTag
     *
     * @param propertyMappingsByTypes
     * @param inheritableToscaElement
     * @param topologyContext
     */
    private static void loadPropertiesDataTypesMapping(String fromTag, Map<String, Map<String, IPropertyMapping>> propertyMappingsByTypes,
            IndexedInheritableToscaElement inheritableToscaElement, TopologyContext topologyContext) {
        if (MapUtils.isEmpty(inheritableToscaElement.getProperties())) {
            return;
        }
        for (Entry<String, PropertyDefinition> definitionEntry : inheritableToscaElement.getProperties().entrySet()) {
            // build the marker for the property
            ComplexPropertyMapping mapping = buildPropertyMapping(definitionEntry.getValue());

            if (mapping != null) {
                IndexedInheritableToscaElement dataType = (IndexedInheritableToscaElement) topologyContext.findElement(IndexedToscaElement.class,
                        mapping.getType());
                // if dataType found in repository, then try to load its mapping
                if (dataType != null) {
                    deeplyLoadPropertyMapping(fromTag, propertyMappingsByTypes, dataType, topologyContext);
                    // only register the marker if there was mapping added for the data type
                    if (propertyMappingsByTypes.containsKey(dataType.getElementId())) {
                        Map<String, IPropertyMapping> typeMappings = propertyMappingsByTypes.get(inheritableToscaElement.getElementId());
                        if (typeMappings == null) {
                            typeMappings = Maps.newLinkedHashMap();
                            propertyMappingsByTypes.put(inheritableToscaElement.getElementId(), typeMappings);
                        }
                        typeMappings.put(definitionEntry.getKey(), mapping);
                    }
                }
            }
        }
    }

    private static ComplexPropertyMapping buildPropertyMapping(PropertyDefinition definition) {

        if (ToscaType.isSimple(definition.getType())) {
            return null;
        }

        switch (definition.getType()) {
        case ToscaType.LIST:
        case ToscaType.MAP:
            return new ComplexPropertyMapping(definition.getEntrySchema().getType(), ToscaType.LIST.equalsIgnoreCase(definition.getType()));
        default:
            return new ComplexPropertyMapping(definition.getType(), false);
        }
    }

    public static Map<String, IPropertyMapping> loadPropertyMapping(String fromTagName, IndexedInheritableToscaElement toscaElement) {
        TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {
        };
        String mappingStr = TagUtil.getTagValue(toscaElement.getTags(), fromTagName);
        if (mappingStr == null) {
            return Maps.newLinkedHashMap();
        }
        try {
            Map<String, Object> mappingsDef = mapper.readValue(mappingStr, typeRef);
            return fromFullPathMap(mappingsDef, toscaElement);
        } catch (IOException e) {
            log.error("Failed to load property mapping for tosca element " + toscaElement.getElementId() + ", will be ignored", e);
            return Maps.newLinkedHashMap();
        }
    }

    private static Map<String, IPropertyMapping> fromFullPathMap(Map<String, Object> parsedMappings, IndexedInheritableToscaElement toscaElement) {
        Map<String, IPropertyMapping> propertyMappings = Maps.newLinkedHashMap();

        for (Map.Entry<String, Object> parsedMapping : parsedMappings.entrySet()) {
            String[] key = asPropAndSubPath(parsedMapping.getKey());
            PropertyMapping propertyMapping = (PropertyMapping) propertyMappings.get(key[0]);
            if (propertyMapping == null) {
                propertyMapping = new PropertyMapping();
            }
            SourceMapping sourceMapping = new SourceMapping(key[1], toscaElement.getProperties().get(key[0]));
            TargetMapping targetMapping = new TargetMapping();
            if (parsedMapping.getValue() != null) {
                String mappingString;
                if (parsedMapping.getValue() instanceof String) {
                    mappingString = (String) parsedMapping.getValue();
                } else {
                    Map<String, String> complexMapping = (Map<String, String>) parsedMapping.getValue();
                    mappingString = complexMapping.get("path");
                    targetMapping.setUnit(complexMapping.get("unit"));
                    if (complexMapping.containsKey("ceil")) {
                        targetMapping.setCeil(true);
                    }
                }
                String[] splitMappingString = asPropAndSubPath(mappingString);
                targetMapping.setProperty(splitMappingString[0]);
                targetMapping.setPath(splitMappingString[1]);
                targetMapping.setPropertyDefinition(toscaElement.getProperties().get(targetMapping.getProperty()));
            }
            PropertySubMapping propertySubMapping = new PropertySubMapping(sourceMapping, targetMapping);
            propertyMapping.getSubMappings().add(propertySubMapping);
            propertyMappings.put(key[0], propertyMapping);
        }

        return propertyMappings;
    }

    /**
     * Extract the property name and sub-path from a string formatted as propname.subpath
     *
     * @param fullPath
     *            The string formatted as propname.subpath.
     * @return An array that contains [propname, subpath] where subpath may be null in case there is no subpath.
     */
    private static String[] asPropAndSubPath(String fullPath) {
        int index = fullPath.indexOf(".");
        if (index < 1 || index == fullPath.length()) {
            return new String[] { fullPath, null };
        }
        return new String[] { fullPath.substring(0, index), fullPath.substring(index + 1) };
    }
}