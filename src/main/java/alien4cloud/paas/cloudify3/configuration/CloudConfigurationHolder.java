package alien4cloud.paas.cloudify3.configuration;

import lombok.Getter;
import lombok.Setter;

import org.springframework.stereotype.Component;

@Getter
@Setter
@SuppressWarnings("PMD.UnusedPrivateField")
@Component("cloudify-configuration-holder")
public class CloudConfigurationHolder {

    private CloudConfiguration configuration = new CloudConfiguration();

}
