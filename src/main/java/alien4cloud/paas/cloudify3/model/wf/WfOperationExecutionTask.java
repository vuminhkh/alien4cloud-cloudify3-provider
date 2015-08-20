package alien4cloud.paas.cloudify3.model.wf;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WfOperationExecutionTask extends WfTask {

    private String interfaceName;
    private String operationName;

    @Override
    public String toString() {
        return getNodeId() + ".call[" + interfaceName + "." + operationName + "]";
    }

}