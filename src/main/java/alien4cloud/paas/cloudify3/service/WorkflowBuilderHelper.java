package alien4cloud.paas.cloudify3.service;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import alien4cloud.model.cloud.NetworkTemplate;
import alien4cloud.model.cloud.StorageTemplate;
import alien4cloud.paas.cloudify3.blueprint.NetworkGenerationUtil;
import alien4cloud.paas.cloudify3.blueprint.VolumeGenerationUtil;
import alien4cloud.paas.cloudify3.configuration.MappingConfigurationHolder;
import alien4cloud.paas.cloudify3.service.model.MatchedPaaSComputeTemplate;
import alien4cloud.paas.cloudify3.service.model.MatchedPaaSTemplate;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.plan.ToscaNodeLifecycleConstants;
import alien4cloud.paas.wf.AbstractStep;
import alien4cloud.paas.wf.NodeActivityStep;
import alien4cloud.paas.wf.Workflow;
import alien4cloud.paas.wf.util.WorkflowUtils;

/**
 * TODO: this class should have several implementations (per provider).
 */
@Slf4j
public class WorkflowBuilderHelper {

    private Map<String, MatchedPaaSTemplate<NetworkTemplate>> matchedExternalNetworksMap;

    private List<MatchedPaaSTemplate<NetworkTemplate>> matchedExternalNetworks;

    private Map<String, MatchedPaaSComputeTemplate> matchedComputesMap;

    private MappingConfigurationHolder mappingConfigurationHolder;

    private InstallWorkflowModifier installWorkflowModifier = new InstallWorkflowModifier();

    private UninstallWorkflowModifier uninstallWorkflowModifier = new UninstallWorkflowModifier();

    private List<MatchedPaaSTemplate<StorageTemplate>> volumes;

    private List<PaaSNodeTemplate> nonNatives;

    private Map<String, MatchedPaaSTemplate<NetworkTemplate>> matchedInternalNetworksMap;

    private List<MatchedPaaSTemplate<NetworkTemplate>> matchedInternalNetworks;

    public WorkflowBuilderHelper(Map<String, MatchedPaaSTemplate<NetworkTemplate>> matchedExternalNetworksMap,
            List<MatchedPaaSTemplate<NetworkTemplate>> matchedExternalNetworks, Map<String, MatchedPaaSComputeTemplate> matchedComputesMap,
            MappingConfigurationHolder mappingConfigurationHolder, List<MatchedPaaSTemplate<StorageTemplate>> volumes, List<PaaSNodeTemplate> nonNatives,
            Map<String, MatchedPaaSTemplate<NetworkTemplate>> matchedInternalNetworksMap, List<MatchedPaaSTemplate<NetworkTemplate>> matchedInternalNetworks) {
        super();
        this.matchedExternalNetworksMap = matchedExternalNetworksMap;
        this.matchedExternalNetworks = matchedExternalNetworks;
        this.matchedComputesMap = matchedComputesMap;
        this.mappingConfigurationHolder = mappingConfigurationHolder;
        this.volumes = volumes;
        this.nonNatives = nonNatives;
        this.matchedInternalNetworksMap = matchedInternalNetworksMap;
        this.matchedInternalNetworks = matchedInternalNetworks;
    }

    private AbstractStep getWorkflowStep(Workflow wf, String stepName) {
        return wf.getSteps().get(stepName);
    }

    private boolean shouldAddStep(Workflow wf, String stepName) {
        AbstractStep step = getWorkflowStep(wf, stepName);
        if (!(step instanceof NodeActivityStep)) {
            return true;
        }
        NodeActivityStep nodeActivityStep = (NodeActivityStep) step;
        String nodeId = nodeActivityStep.getNodeId();
        if (matchedExternalNetworksMap.containsKey(nodeId)) {
            // external networks are not added to the blueprint, so they shouldn't be added to the wf
            return false;
        }
        return true;
    }

    public Map<String, Workflow> buildPaaSWorkflows(Map<String, Workflow> workflows) {
        for (Workflow workflow : workflows.values()) {
            // first iteration to remove useless steps
            Iterator<Entry<String, AbstractStep>> stepIterator = workflow.getSteps().entrySet().iterator();
            Set<String> removedStep = new LinkedHashSet<String>();
            while(stepIterator.hasNext()) {
                Entry<String, AbstractStep> stepEntry = stepIterator.next();
                String stepName = stepEntry.getKey();
                if (!shouldAddStep(workflow, stepName)) {
                    stepIterator.remove();
                    removedStep.add(stepName);
                }
            }
            if (!removedStep.isEmpty()) {
                // some steps have been removed, need to update the links
                stepIterator = workflow.getSteps().entrySet().iterator();
                while (stepIterator.hasNext()) {
                    Entry<String, AbstractStep> stepEntry = stepIterator.next();
                    AbstractStep step = stepEntry.getValue();
                    removeFromSteps(step.getFollowingSteps(), removedStep);
                    removeFromSteps(step.getPrecedingSteps(), removedStep);
                }
            }
            if (workflow.isStandard()) {
                // FIXME: should not be done for amazon
                WorkflowModifier workflowModifier = getWorkflowModifier(workflow);
                for (MatchedPaaSTemplate<NetworkTemplate> internalNetwork : matchedInternalNetworksMap.values()) {
                    String networkId = internalNetwork.getPaaSNodeTemplate().getId();
                    String subnetId = networkId + "_subnet";
                    workflowModifier.addNode(workflow, subnetId);
                    workflowModifier.addHostedOnRelation(workflow, subnetId, networkId);
                }

                // for compute connected to external network, we want to link them with a floating ip node
                for (Entry<String, MatchedPaaSComputeTemplate> matchedComputeEntry : matchedComputesMap.entrySet()) {
                    MatchedPaaSComputeTemplate compute = matchedComputeEntry.getValue();
                    boolean hasFloatingIp = NetworkGenerationUtil._hasMatchedNetwork(compute.getPaaSNodeTemplate().getNetworkNodes(), matchedExternalNetworks);
                    if (hasFloatingIp) {
                        // the compute has a floating ip, we need to add the floating ip steps related to the floating ip node
                        String floatingIpNodeId = mappingConfigurationHolder.getMappingConfiguration().getGeneratedNodePrefix() + "_floating_ip_"
                                + compute.getPaaSNodeTemplate().getId();
                        workflowModifier.addNode(workflow, floatingIpNodeId);
                        workflowModifier.addDependsOnRelation(workflow, compute.getPaaSNodeTemplate().getId(), floatingIpNodeId);
                    }
                    // FIXME: should not be done for amazon
                    boolean hasInternalNetwork = NetworkGenerationUtil._hasMatchedNetwork(compute.getPaaSNodeTemplate().getNetworkNodes(),
                            matchedInternalNetworks);
                    if (hasInternalNetwork) {
                        List<PaaSNodeTemplate> internalNetworkTemplates = NetworkGenerationUtil._getInternalNetworks(compute.getPaaSNodeTemplate()
                                .getNetworkNodes(), matchedInternalNetworks);
                        for (PaaSNodeTemplate internalNetworkTemplate : internalNetworkTemplates) {
                            String subnetId = internalNetworkTemplate.getId() + "_subnet";
                            workflowModifier.addDependsOnRelation(workflow, compute.getPaaSNodeTemplate().getId(), subnetId);
                        }
                    }
                }
                // now look for configured block storages
                boolean hasConfiguredBlockStorages = false;
                for (MatchedPaaSTemplate<StorageTemplate> volume : volumes) {
                    if (VolumeGenerationUtil._isConfiguredVolume(volume.getPaaSNodeTemplate())) {
                        hasConfiguredBlockStorages = true;
                        String fsNodeId = mappingConfigurationHolder.getMappingConfiguration().getGeneratedNodePrefix() + "_file_system_"
                                + volume.getPaaSNodeTemplate().getId();
                        workflowModifier.addNode(workflow, fsNodeId);
                        // the FS depends on the Volume
                        workflowModifier.addHostedOnRelation(workflow, fsNodeId, volume.getPaaSNodeTemplate().getId());
                        // the FS is hosted on the compute
                        workflowModifier.addHostedOnRelation(workflow, fsNodeId, volume.getPaaSNodeTemplate().getParent().getId());
                    }
                }
                // non natives templates that are connected to a BS should depends on the regarding FS node
                if (hasConfiguredBlockStorages) {
                    for (PaaSNodeTemplate nonNative : nonNatives) {
                        PaaSNodeTemplate[] attachedVolumes = VolumeGenerationUtil._getConfiguredAttachedVolumes(nonNative);
                        if (attachedVolumes != null && attachedVolumes.length > 0) {
                            for (PaaSNodeTemplate attachedVolume : attachedVolumes) {
                                String fsNodeId = mappingConfigurationHolder.getMappingConfiguration().getGeneratedNodePrefix() + "_file_system_"
                                        + attachedVolume.getId();
                                workflowModifier.addDependsOnRelation(workflow, nonNative.getId(), fsNodeId);
                            }
                        }
                    }
                }
            }
            log.info("Workflow after tranformation : " + WorkflowUtils.debugWorkflow(workflow));
        }
        return workflows;
    }

    private Set<String> removeFromSteps(Set<String> stepNameSet, Set<String> stepToRemove) {
        if (stepNameSet == null) {
            return stepNameSet;
        }
        stepNameSet.removeAll(stepToRemove);
        return stepNameSet;
    }

    private WorkflowModifier getWorkflowModifier(Workflow workflow) {
        if (workflow.getName().equals(Workflow.INSTALL_WF)) {
            return installWorkflowModifier;
        } else if (workflow.getName().equals(Workflow.UNINSTALL_WF)) {
            return uninstallWorkflowModifier;
        } else {
            return null;
        }
    }

    private abstract class WorkflowModifier {
        public abstract void addNode(Workflow workflow, String nodeId);

        protected AbstractStep appendStep(Workflow wf, AbstractStep lastStep, AbstractStep step) {
            if (lastStep != null) {
                WorkflowUtils.linkSteps(lastStep, step);
            }
            return step;
        }

        public abstract void addHostedOnRelation(Workflow workflow, String sourceNodeId, String targetNodeId);

        public abstract void addDependsOnRelation(Workflow workflow, String sourceNodeId, String targetNodeId);
    }

    private class InstallWorkflowModifier extends WorkflowModifier {

        @Override
        public void addNode(Workflow workflow, String nodeId) {
            AbstractStep step = appendStep(workflow, null, WorkflowUtils.addStateStep(workflow, nodeId, ToscaNodeLifecycleConstants.INITIAL));
            step = appendStep(workflow, step, WorkflowUtils.addStateStep(workflow, nodeId, ToscaNodeLifecycleConstants.CREATING));
            step = appendStep(workflow, step, WorkflowUtils.addOperationStep(workflow, nodeId, ToscaNodeLifecycleConstants.STANDARD, ToscaNodeLifecycleConstants.CREATE));
            step = appendStep(workflow, step, WorkflowUtils.addStateStep(workflow, nodeId, ToscaNodeLifecycleConstants.CREATED));
            step = appendStep(workflow, step, WorkflowUtils.addStateStep(workflow, nodeId, ToscaNodeLifecycleConstants.CONFIGURING));
            step = appendStep(workflow, step,
                    WorkflowUtils.addOperationStep(workflow, nodeId, ToscaNodeLifecycleConstants.STANDARD, ToscaNodeLifecycleConstants.CONFIGURE));
            step = appendStep(workflow, step, WorkflowUtils.addStateStep(workflow, nodeId, ToscaNodeLifecycleConstants.CONFIGURED));
            step = appendStep(workflow, step, WorkflowUtils.addStateStep(workflow, nodeId, ToscaNodeLifecycleConstants.STARTING));
            step = appendStep(workflow, step,
                    WorkflowUtils.addOperationStep(workflow, nodeId, ToscaNodeLifecycleConstants.STANDARD, ToscaNodeLifecycleConstants.START));
            step = appendStep(workflow, step, WorkflowUtils.addStateStep(workflow, nodeId, ToscaNodeLifecycleConstants.STARTED));
        }

        @Override
        public void addHostedOnRelation(Workflow workflow, String sourceNodeId, String targetNodeId) {
            AbstractStep source = WorkflowUtils.getStateStepByNode(workflow, sourceNodeId, ToscaNodeLifecycleConstants.INITIAL);
            AbstractStep target = WorkflowUtils.getStateStepByNode(workflow, targetNodeId, ToscaNodeLifecycleConstants.STARTED);
            WorkflowUtils.linkSteps(target, source);
        }

        @Override
        public void addDependsOnRelation(Workflow workflow, String sourceNodeId, String targetNodeId) {
            AbstractStep source = WorkflowUtils.getStateStepByNode(workflow, sourceNodeId, ToscaNodeLifecycleConstants.CONFIGURING);
            AbstractStep target = WorkflowUtils.getStateStepByNode(workflow, targetNodeId, ToscaNodeLifecycleConstants.STARTED);
            WorkflowUtils.linkSteps(target, source);
        }

    }

    private class UninstallWorkflowModifier extends WorkflowModifier {

        @Override
        public void addNode(Workflow workflow, String nodeId) {
            AbstractStep step = appendStep(workflow, null, WorkflowUtils.addStateStep(workflow, nodeId, ToscaNodeLifecycleConstants.STOPPING));
            step = appendStep(workflow, step,
                    WorkflowUtils.addOperationStep(workflow, nodeId, ToscaNodeLifecycleConstants.STANDARD, ToscaNodeLifecycleConstants.STOP));
            step = appendStep(workflow, step, WorkflowUtils.addStateStep(workflow, nodeId, ToscaNodeLifecycleConstants.STOPPED));
            step = appendStep(workflow, step, WorkflowUtils.addStateStep(workflow, nodeId, ToscaNodeLifecycleConstants.DELETING));
            step = appendStep(workflow, step,
                    WorkflowUtils.addOperationStep(workflow, nodeId, ToscaNodeLifecycleConstants.STANDARD, ToscaNodeLifecycleConstants.DELETE));
            step = appendStep(workflow, step, WorkflowUtils.addStateStep(workflow, nodeId, ToscaNodeLifecycleConstants.DELETED));
        }

        private void addRelation(Workflow workflow, String sourceNodeId, String targetNodeId) {
            AbstractStep source = WorkflowUtils.getStateStepByNode(workflow, sourceNodeId, ToscaNodeLifecycleConstants.DELETED);
            AbstractStep target = WorkflowUtils.getStateStepByNode(workflow, targetNodeId, ToscaNodeLifecycleConstants.STOPPING);
            WorkflowUtils.linkSteps(source, target);
        }

        @Override
        public void addHostedOnRelation(Workflow workflow, String sourceNodeId, String targetNodeId) {
            addRelation(workflow, sourceNodeId, targetNodeId);
        }

        @Override
        public void addDependsOnRelation(Workflow workflow, String sourceNodeId, String targetNodeId) {
            addRelation(workflow, sourceNodeId, targetNodeId);
        }

    }

}
