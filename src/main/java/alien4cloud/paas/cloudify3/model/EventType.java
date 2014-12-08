package alien4cloud.paas.cloudify3.model;

public class EventType {

    private EventType() {
    }

    public static final String WORKFLOW_STAGE = "workflow_stage";
    public static final String WORKFLOW_NODE_EVENT = "workflow_node_event";
    public static final String WORKFLOW_STARTED = "workflow_started";
    public static final String WORKFLOW_SUCCEEDED = "workflow_succeeded";
    public static final String SENDING_TASK = "sending_task";
    public static final String TASK_STARTED = "task_started";
    public static final String TASK_SUCCEEDED = "task_succeeded";
}
