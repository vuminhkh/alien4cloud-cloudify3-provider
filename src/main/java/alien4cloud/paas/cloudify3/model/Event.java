package alien4cloud.paas.cloudify3.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@Getter
@Setter
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
public class Event extends AbstractCloudifyModel {

    private String eventType;

    @JsonProperty("@timestamp")
    private String timestamp;

    private EventContext context;

    private EventMessage message;
}