package alien4cloud.paas.cloudify3.model.wf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;
import alien4cloud.paas.plan.ParallelJoinStateGateway;

@Getter
@Setter
public class WfStep {

    private List<WfStep> precedingSteps = new ArrayList<WfStep>();
    private List<WfStep> followingSteps = new ArrayList<WfStep>();
    private WfTask task;
    private ParallelJoinStateGateway join2resolv;

    // a unique id that will be used in velocity template
    private String id;

    public WfStep() {
        super();
        id = UUID.randomUUID().toString();
    }

    public void addPreceding(WfStep step) {
        precedingSteps.add(step);
    }

    public void addFollowing(WfStep step) {
        followingSteps.add(step);
    }

    // public String getId() {
    // return task.toString();
    // }

}