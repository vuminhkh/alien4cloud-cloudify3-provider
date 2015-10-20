package alien4cloud.paas.cloudify3.util.mapping;

import lombok.Getter;
import lombok.Setter;

/**
 * Mapping information for the target.
 */
@Getter
@Setter
public class TargetMapping {
    String property; // name of the property in which to map the value
    String path; // optional sub-path from the target property
    String unit; // optional parameter that allows conversion of a Scalar Unit to Scalar based on the required unit.
}