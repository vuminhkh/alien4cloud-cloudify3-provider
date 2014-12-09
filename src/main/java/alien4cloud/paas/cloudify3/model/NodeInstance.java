package alien4cloud.paas.cloudify3.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
@SuppressWarnings("PMD.UnusedPrivateField")
public class NodeInstance extends AbstractCloudifyModel {

    private String id;

    private String nodeId;

    private String hostId;

    private String deploymentId;

    private Map<String, Object> runtimeProperties;

    private String state;
}
