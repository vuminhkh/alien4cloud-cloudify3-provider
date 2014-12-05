package alien4cloud.paas.cloudify3.model;

import java.util.Date;
import java.util.Map;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import alien4cloud.rest.utils.JsonUtil;

import com.fasterxml.jackson.core.JsonProcessingException;

@Getter
@Setter
@EqualsAndHashCode
@SuppressWarnings("PMD.UnusedPrivateField")
public class Deployment {

    private String id;

    private String blueprintId;

    private Date createdAt;

    private Date updatedAt;

    private Workflow[] workflows;

    private Map<String, Object> inputs;

    private Map<String, Object> outputs;

    private Map<String, Object> policyTriggers;

    private Map<String, Object> groups;

    private Map<String, Object> policyTypes;

    public String toString() {
        try {
            return JsonUtil.toString(this);
        } catch (JsonProcessingException e) {
            return "Deployment " + id + " cannot be serialized";
        }
    }
}
