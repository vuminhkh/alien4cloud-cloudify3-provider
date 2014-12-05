package alien4cloud.paas.cloudify3.model;

import java.util.Date;
import java.util.Map;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import alien4cloud.rest.utils.JsonUtil;

import com.fasterxml.jackson.core.JsonProcessingException;

@Getter
@Setter
@ToString
@EqualsAndHashCode
@SuppressWarnings("PMD.UnusedPrivateField")
public class Execution {

    private String id;

    private String blueprintId;

    private String workflowId;

    private String deploymentId;

    private ExecutionStatus status;

    private String error;

    private Date createdAt;

    private Map<String, Object> parameters;

    public String toString() {
        try {
            return JsonUtil.toString(this);
        } catch (JsonProcessingException e) {
            return "Execution " + id + " cannot be serialized";
        }
    }
}
