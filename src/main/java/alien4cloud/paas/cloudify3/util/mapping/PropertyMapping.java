package alien4cloud.paas.cloudify3.util.mapping;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

import com.google.common.collect.Lists;

/**
 * Defines a mapping from a source property to others.
 */
@Getter
@Setter
public class PropertyMapping implements IPropertyMapping {
    // Optional sub-paths to map
    List<PropertySubMapping> subMappings = Lists.newArrayList();
}