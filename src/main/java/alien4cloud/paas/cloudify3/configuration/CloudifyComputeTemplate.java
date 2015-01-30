package alien4cloud.paas.cloudify3.configuration;

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
public class CloudifyComputeTemplate {

    private String image;

    private String flavor;
}
