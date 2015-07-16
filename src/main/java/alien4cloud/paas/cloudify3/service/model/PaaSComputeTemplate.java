package alien4cloud.paas.cloudify3.service.model;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@SuppressWarnings("PMD.UnusedPrivateField")
@Getter
@Setter
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
public class PaaSComputeTemplate {

    private String image;

    private String flavor;

    private String availabilityZone;

    // TODO it's ugly, for byon only while waiting for paaS provider refactoring
    private Map<String, String> userData;
}
