package alien4cloud.paas.cloudify3.model.wf;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WfSetStateTask extends WfTask {

    private String stateName;

    @Override
    public String toString() {
        return getNodeId() + ".setState[" + stateName + "]";
    }

}