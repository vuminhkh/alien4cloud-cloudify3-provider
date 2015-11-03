package alien4cloud.paas.cloudify3.util.mapping;

import lombok.Getter;
import lombok.Setter;

/**
 * Defines a mapping from a source property to others.
 */
@Getter
@Setter
public class ComplexPropertyMapping implements IPropertyMapping {

    /** Type of the complex property. */
    private String type;

    /** Indicates if the property is hold as a list. */
    private boolean isList;
}