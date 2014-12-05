package alien4cloud.paas.cloudify3.configuration;

import lombok.Getter;
import lombok.Setter;

import org.springframework.stereotype.Component;

@Getter
@Setter
@SuppressWarnings("PMD.UnusedPrivateField")
@Component
public class CloudConfiguration {

    private String url = "http://11.0.0.7:8100";
}
