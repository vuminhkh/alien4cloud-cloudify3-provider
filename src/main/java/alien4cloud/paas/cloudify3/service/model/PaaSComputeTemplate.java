package alien4cloud.paas.cloudify3.service.model;

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

    public PaaSComputeTemplate(String image, String flavor) {
        this.image = image;
        this.flavor = flavor;
    }
}
