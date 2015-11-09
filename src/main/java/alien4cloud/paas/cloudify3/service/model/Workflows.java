package alien4cloud.paas.cloudify3.service.model;

import java.util.Map;

import alien4cloud.paas.wf.Workflow;

public class Workflows {

    private Map<String, Workflow> workflows;

    /**
     * The install workflow steps by host.
     * <ul>
     * <li>key is the hostworkflows
     * <li>value is a sub-workflow related to the given host
     * </ul>
     */
    private Map<String, HostWorkflow> installWorkflowSteps;

    /**
     * The uninstall workflow steps by host.
     * <ul>
     * <li>key is the host
     * <li>value is a sub-workflow related to the given host
     * </ul>
     */
    private Map<String, HostWorkflow> uninstallWorkflowSteps;

    // workflowId ->
    private Map<String, StandardWorkflow> standardWorkflows;

}
