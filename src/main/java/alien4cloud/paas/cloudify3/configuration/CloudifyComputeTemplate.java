package alien4cloud.paas.cloudify3.configuration;

import lombok.Getter;
import lombok.Setter;

@SuppressWarnings("PMD.UnusedPrivateField")
@Getter
@Setter
public class CloudifyComputeTemplate {

    private String image;

    private String flavor;
}
