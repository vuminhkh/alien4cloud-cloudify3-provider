package alien4cloud.paas.cloudify3.service.model;

import java.util.List;
import java.util.Map;

import alien4cloud.paas.wf.AbstractStep;

/**
 * A sub workflow related to a given host.
 */
public class HostWorkflow {

    /**
     * The steps related to this host.
     */
    private Map<String, AbstractStep> steps;

    /**
     * The link between this host steps (internal links).
     */
    private List<WorkflowStepLink> links;

}
