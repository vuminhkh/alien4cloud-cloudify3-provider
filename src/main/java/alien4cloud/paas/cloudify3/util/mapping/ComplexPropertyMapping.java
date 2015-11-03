package alien4cloud.paas.cloudify3.util.mapping;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Defines a mapping fro a ComplexProperty type.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ComplexPropertyMapping implements IPropertyMapping {

    /** Type of the complex property. */
    private String type;

    /** Indicates if the property is hold as a list. */
    private boolean isList;
}