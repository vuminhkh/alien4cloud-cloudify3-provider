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
public class Workflow {

    private Date createdAt;

    private String name;

    private Map<String, Object> parameters;

    public String toString() {
        try {
            return JsonUtil.toString(this);
        } catch (JsonProcessingException e) {
            return "Workflow " + name + " cannot be serialized";
        }
    }
}
