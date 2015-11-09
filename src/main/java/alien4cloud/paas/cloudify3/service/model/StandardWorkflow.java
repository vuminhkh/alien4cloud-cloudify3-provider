package alien4cloud.paas.cloudify3.service.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

import alien4cloud.paas.wf.AbstractStep;

public class StandardWorkflow {

    /**
     * Steps that are not related to any host.
     */
    private Map<String, AbstractStep> orphanSteps;
    
    /**
     * Links that concerns several hosts.
     */
    private List<WorkflowStepLink> externalLinks;

    private Set<String> hosts;
}
