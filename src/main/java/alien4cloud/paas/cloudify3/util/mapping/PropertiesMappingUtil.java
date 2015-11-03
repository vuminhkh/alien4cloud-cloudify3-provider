package alien4cloud.paas.cloudify3.util.mapping;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Component;

import alien4cloud.model.components.IndexedNodeType;
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
     * @return A map <nodeType, <toscaPath, cloudifyPath>>>
     */
    public static Map<String, Map<String, IPropertyMapping>> loadPropertyMappings(List<IndexedNodeType> nodeTypes) {
        TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {
        };

        // <nodeType, <toscaPath, cloudifyPath>>>
        Map<String, Map<String, PropertyMapping>> propertyMappingsByTypes = Maps.newHashMap();
        for (IndexedNodeType nodeType : nodeTypes) {
            Map<String, PropertyMapping> mappings = loadPropertyMapping(PROP_MAPPING_TAG_KEY, nodeType);
            if (MapUtils.isNotEmpty(mappings)) {
                propertyMappingsByTypes.put(nodeType.getElementId(), mappings);
            }
        }
        return propertyMappingsByTypes;
    }

    public static Map<String, PropertyMapping> loadPropertyMapping(String fromTagName, IndexedNodeType nodeType) {
        TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {
        };
        String mappingStr = TagUtil.getTagValue(nodeType.getTags(), fromTagName);
        if (mappingStr == null) {
            return Maps.newHashMap();
        }
        try {
            Map<String, Object> mappingsDef = mapper.readValue(mappingStr, typeRef);
            return fromFullPathMap(mappingsDef, nodeType);
        } catch (IOException e) {
            log.error("Failed to load property mapping, will be ignored", e);
            return Maps.newHashMap();
        }
    }

    private static Map<String, PropertyMapping> fromFullPathMap(Map<String, Object> parsedMappings, IndexedNodeType nodeType) {
        Map<String, PropertyMapping> propertyMappings = Maps.newHashMap();

        for (Map.Entry<String, Object> parsedMapping : parsedMappings.entrySet()) {
            String[] key = asPropAndSubPath(parsedMapping.getKey());
            PropertyMapping propertyMapping = propertyMappings.get(key[0]);
            if (propertyMapping == null) {
                propertyMapping = new PropertyMapping();
            }
            SourceMapping sourceMapping = new SourceMapping(key[1], nodeType.getProperties().get(key[0]));
            TargetMapping targetMapping = new TargetMapping();
            if (parsedMapping.getValue() != null) {
                String mappingString;
                if (parsedMapping.getValue() instanceof String) {
                    mappingString = (String) parsedMapping.getValue();
                } else {
                    mappingString = ((Map<String, String>) parsedMapping.getValue()).get("path");
                    targetMapping.setUnit(((Map<String, String>) parsedMapping.getValue()).get("unit"));
                }
                String[] splitMappingString = asPropAndSubPath(mappingString);
                targetMapping.setProperty(splitMappingString[0]);
                targetMapping.setPath(splitMappingString[1]);
                targetMapping.setPropertyDefinition(nodeType.getProperties().get(targetMapping.getProperty()));
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