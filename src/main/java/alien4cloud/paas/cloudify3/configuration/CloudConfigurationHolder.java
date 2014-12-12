package alien4cloud.paas.cloudify3.configuration;

import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@SuppressWarnings("PMD.UnusedPrivateField")
@Component
public class CloudConfigurationHolder {

    private CloudConfiguration configuration = new CloudConfiguration();

}
