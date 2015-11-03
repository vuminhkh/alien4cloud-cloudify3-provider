package alien4cloud.paas.cloudify3.util.mapping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.elasticsearch.common.collect.Lists;
import org.elasticsearch.common.collect.Maps;
import org.junit.Test;

import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.components.ComplexPropertyValue;
import alien4cloud.model.components.ListPropertyValue;
import alien4cloud.model.components.ScalarPropertyValue;

public class PropertyValueUtilTest {

    /**
     * Idempotent empty mapping.
     */
    @Test
    public void testEmptyMapping() {
        Map<String, Map<String, IPropertyMapping>> propertyMappings = Maps.newHashMap();

        Map<String, AbstractPropertyValue> properties = Maps.newHashMap();
        properties.put("prop1", new ScalarPropertyValue("value1"));

        Map<String, AbstractPropertyValue> result = PropertyValueUtil.mapProperties(propertyMappings, "ScalableCompute", properties);

        assertEquals(1, result.size());
        assertTrue(result.containsKey("prop1"));
        assertEquals("value1", ((ScalarPropertyValue) result.get("prop1")).getValue());
    }

    /**
     * Tests a simple mapping that map 'prop1' to 'mapped_prop1'.
     */
    @Test
    public void testSimpleMapping() {
        // a simple mapping that map 'prop1' to 'mapped_prop1'
        Map<String, Map<String, IPropertyMapping>> propertyMappings = Maps.newHashMap();
        Map<String, IPropertyMapping> scalableComputePropertyMappings = Maps.newHashMap();
        propertyMappings.put("ScalableCompute", scalableComputePropertyMappings);
        PropertyMapping simplePropertyMapping = new PropertyMapping();
        scalableComputePropertyMappings.put("prop1", simplePropertyMapping);
        PropertySubMapping simpleSubMapping = new PropertySubMapping();
        simpleSubMapping.setSourceMapping(new SourceMapping());
        simpleSubMapping.setTargetMapping(new TargetMapping());
        simpleSubMapping.getTargetMapping().setProperty("mapped_prop1");
        simplePropertyMapping.setSubMappings(Lists.newArrayList(simpleSubMapping));

        Map<String, AbstractPropertyValue> properties = Maps.newHashMap();
        properties.put("prop1", new ScalarPropertyValue("value1"));

        Map<String, AbstractPropertyValue> result = PropertyValueUtil.mapProperties(propertyMappings, "ScalableCompute", properties);

        assertEquals(1, result.size());
        assertTrue(result.containsKey("mapped_prop1"));
        assertEquals("value1", ((ScalarPropertyValue) result.get("mapped_prop1")).getValue());
    }

    /**
     * Tests a simple mapping that map 'prop1' to 'mapped.prop1'.
     */
    @Test
    public void testSimpleMappingWithPath() {
        Map<String, Map<String, IPropertyMapping>> propertyMappings = Maps.newHashMap();
        Map<String, IPropertyMapping> scalableComputePropertyMappings = Maps.newHashMap();
        propertyMappings.put("ScalableCompute", scalableComputePropertyMappings);
        PropertyMapping simplePropertyMapping = new PropertyMapping();
        scalableComputePropertyMappings.put("prop1", simplePropertyMapping);
        PropertySubMapping simpleSubMapping = new PropertySubMapping();
        simpleSubMapping.setSourceMapping(new SourceMapping());
        simpleSubMapping.setTargetMapping(new TargetMapping());
        simpleSubMapping.getTargetMapping().setProperty("mapped");
        simpleSubMapping.getTargetMapping().setPath("prop1");
        simplePropertyMapping.setSubMappings(Lists.newArrayList(simpleSubMapping));

        Map<String, AbstractPropertyValue> properties = Maps.newHashMap();
        properties.put("prop1", new ScalarPropertyValue("value1"));

        Map<String, AbstractPropertyValue> result = PropertyValueUtil.mapProperties(propertyMappings, "ScalableCompute", properties);

        assertEquals(1, result.size());
        assertTrue(result.containsKey("mapped"));
        AbstractPropertyValue mapped = result.get("mapped");
        assertTrue(mapped instanceof ComplexPropertyValue);
        ComplexPropertyValue mappedAsComplexPropertyValue = (ComplexPropertyValue) mapped;
        // here the map entry is just a string (not a scalar)
        assertEquals("value1", mappedAsComplexPropertyValue.getValue().get("prop1"));
    }

    /**
     * The 'ScalableCompute' type as a property 'volumes' that is a list of 'EmbededVolumeProperties'. For each entry we map the 'volume_id' property to
     * 'resource_id'.
     */
    @Test
    public void testSimpleList() {
        Map<String, Map<String, IPropertyMapping>> propertyMappings = Maps.newHashMap();
        Map<String, IPropertyMapping> scalableComputePropertyMappings = Maps.newHashMap();
        propertyMappings.put("ScalableCompute", scalableComputePropertyMappings);
        ComplexPropertyMapping volumesPropertyMapping = new ComplexPropertyMapping();
        volumesPropertyMapping.setList(true);
        volumesPropertyMapping.setType("EmbededVolumeProperties");
        scalableComputePropertyMappings.put("volumes", volumesPropertyMapping);

        Map<String, IPropertyMapping> embededVolumePropertiyMappings = Maps.newHashMap();
        propertyMappings.put("EmbededVolumeProperties", embededVolumePropertiyMappings);
        
        PropertyMapping volumeIdMapping = new PropertyMapping();
        PropertySubMapping volumeIdSubMapping = new PropertySubMapping();
        volumeIdSubMapping.setSourceMapping(new SourceMapping());
        volumeIdSubMapping.setTargetMapping(new TargetMapping());
        volumeIdSubMapping.getTargetMapping().setProperty("resource_id");
        volumeIdMapping.setSubMappings(Lists.newArrayList(volumeIdSubMapping));
        embededVolumePropertiyMappings.put("volume_id", volumeIdMapping);

        Map<String, AbstractPropertyValue> properties = Maps.newHashMap();
        ListPropertyValue volumesProperty = new ListPropertyValue(new ArrayList<Object>());
        ComplexPropertyValue volume1property = new ComplexPropertyValue(new HashMap<String, Object>());
        volume1property.getValue().put("volume_id", new ScalarPropertyValue("volume1"));
        volumesProperty.getValue().add(volume1property);
        ComplexPropertyValue volume2property = new ComplexPropertyValue(new HashMap<String, Object>());
        volume2property.getValue().put("volume_id", new ScalarPropertyValue("volume2"));
        volumesProperty.getValue().add(volume2property);
        properties.put("volumes", volumesProperty);

        Map<String, AbstractPropertyValue> result = PropertyValueUtil.mapProperties(propertyMappings, "ScalableCompute", properties);
        assertEquals(1, result.size());
        assertTrue(result.containsKey("volumes"));
        AbstractPropertyValue resultVolumesProperty = result.get("volumes");
        assertTrue(resultVolumesProperty instanceof ListPropertyValue);
        ListPropertyValue resultVolumesPropertyAsList = (ListPropertyValue)resultVolumesProperty;
        assertEquals(2, resultVolumesPropertyAsList.getValue().size());
        // item0
        Object resultVolumesPropertyItem0 = resultVolumesPropertyAsList.getValue().get(0);
        assertTrue(resultVolumesPropertyItem0 instanceof Map);
        Map resultVolumesPropertyItem0AsMap = (Map) resultVolumesPropertyItem0;
        assertEquals(1, resultVolumesPropertyItem0AsMap.size());
        assertTrue(resultVolumesPropertyItem0AsMap.containsKey("resource_id"));
        assertEquals("volume1", ((ScalarPropertyValue) resultVolumesPropertyItem0AsMap.get("resource_id")).getValue());
        // item1
        Object resultVolumesPropertyItem1 = resultVolumesPropertyAsList.getValue().get(1);
        assertTrue(resultVolumesPropertyItem1 instanceof Map);
        Map resultVolumesPropertyItem1AsMap = (Map) resultVolumesPropertyItem1;
        assertEquals(1, resultVolumesPropertyItem1AsMap.size());
        assertTrue(resultVolumesPropertyItem1AsMap.containsKey("resource_id"));
        assertEquals("volume2", ((ScalarPropertyValue) resultVolumesPropertyItem1AsMap.get("resource_id")).getValue());
    }

    /**
     * The 'ScalableCompute' type as a property 'volumes' that is a list of 'EmbededVolumeProperties'. For each entry we map the 'volume_id' property to
     * 'volume.resource_id'.
     */
    @Test
    public void testListWithPath() {
        Map<String, Map<String, IPropertyMapping>> propertyMappings = Maps.newHashMap();
        Map<String, IPropertyMapping> scalableComputePropertyMappings = Maps.newHashMap();
        propertyMappings.put("ScalableCompute", scalableComputePropertyMappings);
        ComplexPropertyMapping volumesPropertyMapping = new ComplexPropertyMapping();
        volumesPropertyMapping.setList(true);
        volumesPropertyMapping.setType("EmbededVolumeProperties");
        scalableComputePropertyMappings.put("volumes", volumesPropertyMapping);

        Map<String, IPropertyMapping> embededVolumePropertiyMappings = Maps.newHashMap();
        propertyMappings.put("EmbededVolumeProperties", embededVolumePropertiyMappings);

        PropertyMapping volumeIdMapping = new PropertyMapping();
        PropertySubMapping volumeIdSubMapping = new PropertySubMapping();
        volumeIdSubMapping.setSourceMapping(new SourceMapping());
        volumeIdSubMapping.setTargetMapping(new TargetMapping());
        volumeIdSubMapping.getTargetMapping().setProperty("volume");
        volumeIdSubMapping.getTargetMapping().setPath("resource_id");
        volumeIdMapping.setSubMappings(Lists.newArrayList(volumeIdSubMapping));
        embededVolumePropertiyMappings.put("volume_id", volumeIdMapping);

        Map<String, AbstractPropertyValue> properties = Maps.newHashMap();
        ListPropertyValue volumesProperty = new ListPropertyValue(new ArrayList<Object>());
        ComplexPropertyValue volume1property = new ComplexPropertyValue(new HashMap<String, Object>());
        volume1property.getValue().put("volume_id", new ScalarPropertyValue("volume1"));
        volumesProperty.getValue().add(volume1property);
        properties.put("volumes", volumesProperty);

        Map<String, AbstractPropertyValue> result = PropertyValueUtil.mapProperties(propertyMappings, "ScalableCompute", properties);
        assertEquals(1, result.size());
        assertTrue(result.containsKey("volumes"));
        AbstractPropertyValue resultVolumesProperty = result.get("volumes");
        assertTrue(resultVolumesProperty instanceof ListPropertyValue);
        ListPropertyValue resultVolumesPropertyAsList = (ListPropertyValue) resultVolumesProperty;
        assertEquals(1, resultVolumesPropertyAsList.getValue().size());
        // item0
        Object resultVolumesPropertyItem0 = resultVolumesPropertyAsList.getValue().get(0);
        assertTrue(resultVolumesPropertyItem0 instanceof Map);
        Map resultVolumesPropertyItem0AsMap = (Map) resultVolumesPropertyItem0;
        assertEquals(1, resultVolumesPropertyItem0AsMap.size());
        assertTrue(resultVolumesPropertyItem0AsMap.containsKey("volume"));
        Object resultVolumesPropertyItem0AsMapVolume = resultVolumesPropertyItem0AsMap.get("volume");
        assertTrue(resultVolumesPropertyItem0AsMapVolume instanceof ComplexPropertyValue);
        ComplexPropertyValue resultVolumesPropertyItem0AsMapVolumeAsCplx = (ComplexPropertyValue) resultVolumesPropertyItem0AsMapVolume;
        assertEquals("volume1", resultVolumesPropertyItem0AsMapVolumeAsCplx.getValue().get("resource_id"));
    }

    /**
     * The 'ScalableCompute' type as a property 'volume' that is a 'EmbededVolumeProperties' (but not a list). We map the 'volume_id' property to
     * 'resource_id'.
     */
    @Test
    public void testComplexeType() {
        Map<String, Map<String, IPropertyMapping>> propertyMappings = Maps.newHashMap();
        Map<String, IPropertyMapping> scalableComputePropertyMappings = Maps.newHashMap();
        propertyMappings.put("ScalableCompute", scalableComputePropertyMappings);
        ComplexPropertyMapping volumesPropertyMapping = new ComplexPropertyMapping();
        volumesPropertyMapping.setList(false);
        volumesPropertyMapping.setType("EmbededVolumeProperties");
        scalableComputePropertyMappings.put("volume", volumesPropertyMapping);

        Map<String, IPropertyMapping> embededVolumePropertiyMappings = Maps.newHashMap();
        propertyMappings.put("EmbededVolumeProperties", embededVolumePropertiyMappings);

        PropertyMapping volumeIdMapping = new PropertyMapping();
        PropertySubMapping volumeIdSubMapping = new PropertySubMapping();
        volumeIdSubMapping.setSourceMapping(new SourceMapping());
        volumeIdSubMapping.setTargetMapping(new TargetMapping());
        volumeIdSubMapping.getTargetMapping().setProperty("resource_id");
        volumeIdMapping.setSubMappings(Lists.newArrayList(volumeIdSubMapping));
        embededVolumePropertiyMappings.put("volume_id", volumeIdMapping);

        Map<String, AbstractPropertyValue> properties = Maps.newHashMap();
        ComplexPropertyValue volume1property = new ComplexPropertyValue(new HashMap<String, Object>());
        volume1property.getValue().put("volume_id", new ScalarPropertyValue("volume1"));
        properties.put("volume", volume1property);

        Map<String, AbstractPropertyValue> result = PropertyValueUtil.mapProperties(propertyMappings, "ScalableCompute", properties);
        assertEquals(1, result.size());
        assertTrue(result.containsKey("volume"));
        AbstractPropertyValue resultVolumesProperty = result.get("volume");
        // item0
        assertTrue(resultVolumesProperty instanceof ComplexPropertyValue);
        ComplexPropertyValue resultVolumesPropertyItem0AsMap = (ComplexPropertyValue) resultVolumesProperty;
        assertTrue(resultVolumesPropertyItem0AsMap.getValue().containsKey("resource_id"));
        assertEquals("volume1", ((ScalarPropertyValue) resultVolumesPropertyItem0AsMap.getValue().get("resource_id")).getValue());
    }

    /**
     * The 'ScalableCompute' type as a property 'volumes' that is a list of 'EmbededVolumeProperties'. The data type 'EmbededVolumeProperties' embed another
     * list of 'AnotherDataType' in 'others' property. For each entry we map the 'volume_id' property to 'resource_id'.
     */
    @Test
    public void recursiveTest() {
        Map<String, Map<String, IPropertyMapping>> propertyMappings = Maps.newHashMap();
        Map<String, IPropertyMapping> scalableComputePropertyMappings = Maps.newHashMap();
        propertyMappings.put("ScalableCompute", scalableComputePropertyMappings);
        ComplexPropertyMapping volumesPropertyMapping = new ComplexPropertyMapping();
        volumesPropertyMapping.setList(true);
        volumesPropertyMapping.setType("EmbededVolumeProperties");
        scalableComputePropertyMappings.put("volumes", volumesPropertyMapping);

        Map<String, IPropertyMapping> embededVolumePropertiyMappings = Maps.newHashMap();
        propertyMappings.put("EmbededVolumeProperties", embededVolumePropertiyMappings);

        Map<String, IPropertyMapping> anotherDataTypePropertiyMappings = Maps.newHashMap();
        propertyMappings.put("AnotherDataType", anotherDataTypePropertiyMappings);

        ComplexPropertyMapping othersPropertyMapping = new ComplexPropertyMapping();
        othersPropertyMapping.setList(true);
        othersPropertyMapping.setType("AnotherDataType");
        embededVolumePropertiyMappings.put("others", othersPropertyMapping);

        PropertyMapping volumeIdMapping = new PropertyMapping();
        PropertySubMapping volumeIdSubMapping = new PropertySubMapping();
        volumeIdSubMapping.setSourceMapping(new SourceMapping());
        volumeIdSubMapping.setTargetMapping(new TargetMapping());
        volumeIdSubMapping.getTargetMapping().setProperty("resource_id");
        volumeIdMapping.setSubMappings(Lists.newArrayList(volumeIdSubMapping));
        anotherDataTypePropertiyMappings.put("volume_id", volumeIdMapping);

        Map<String, AbstractPropertyValue> properties = Maps.newHashMap();
        ListPropertyValue volumesProperty = new ListPropertyValue(new ArrayList<Object>());
        ListPropertyValue othersProperty = new ListPropertyValue(new ArrayList<Object>());
        ComplexPropertyValue volume1property = new ComplexPropertyValue(new HashMap<String, Object>());
        ComplexPropertyValue others1property = new ComplexPropertyValue(new HashMap<String, Object>());
        others1property.getValue().put("volume_id", new ScalarPropertyValue("volume1"));
        othersProperty.getValue().add(others1property);
        volume1property.getValue().put("others", othersProperty);
        volumesProperty.getValue().add(volume1property);
        properties.put("volumes", volumesProperty);

        Map<String, AbstractPropertyValue> result = PropertyValueUtil.mapProperties(propertyMappings, "ScalableCompute", properties);
        assertEquals(1, result.size());
        assertTrue(result.containsKey("volumes"));
        AbstractPropertyValue resultVolumesProperty = result.get("volumes");
        assertTrue(resultVolumesProperty instanceof ListPropertyValue);
        ListPropertyValue resultVolumesPropertyAsList = (ListPropertyValue) resultVolumesProperty;
        assertEquals(1, resultVolumesPropertyAsList.getValue().size());
        // item0
        Object resultVolumesPropertyItem0 = resultVolumesPropertyAsList.getValue().get(0);
        assertTrue(resultVolumesPropertyItem0 instanceof Map);
        Map resultVolumesPropertyItem0AsMap = (Map) resultVolumesPropertyItem0;
        assertEquals(1, resultVolumesPropertyItem0AsMap.size());
        assertTrue(resultVolumesPropertyItem0AsMap.containsKey("others"));
        Object othersResultProperty = resultVolumesPropertyItem0AsMap.get("others");
        assertTrue(othersResultProperty instanceof ListPropertyValue);
        ListPropertyValue othersResultPropertyAsList = (ListPropertyValue) othersResultProperty;
        assertEquals(1, othersResultPropertyAsList.getValue().size());
        Object othersResultPropertyAsListItem0 = othersResultPropertyAsList.getValue().get(0);
        assertTrue(othersResultPropertyAsListItem0 instanceof Map);
        Map othersResultPropertyAsListItem0AsMap = (Map) othersResultPropertyAsListItem0;
        assertTrue(othersResultPropertyAsListItem0AsMap.containsKey("resource_id"));
        assertEquals("volume1", ((ScalarPropertyValue) othersResultPropertyAsListItem0AsMap.get("resource_id")).getValue());
    }

}
