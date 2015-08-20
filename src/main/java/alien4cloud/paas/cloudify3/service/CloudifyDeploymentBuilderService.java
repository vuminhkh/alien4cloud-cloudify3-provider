package alien4cloud.paas.cloudify3.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Resource;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import alien4cloud.model.cloud.AvailabilityZone;
import alien4cloud.model.cloud.CloudResourceMatcherConfig;
import alien4cloud.model.cloud.NetworkTemplate;
import alien4cloud.model.cloud.StorageTemplate;
import alien4cloud.model.components.DeploymentArtifact;
import alien4cloud.model.components.IndexedModelUtils;
import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.model.components.IndexedRelationshipType;
import alien4cloud.paas.cloudify3.model.wf.WfOperationExecutionTask;
import alien4cloud.paas.cloudify3.model.wf.WfSetStateTask;
import alien4cloud.paas.cloudify3.model.wf.WfStep;
import alien4cloud.paas.cloudify3.model.wf.WfTask;
import alien4cloud.paas.cloudify3.service.model.CloudifyDeployment;
import alien4cloud.paas.cloudify3.service.model.IMatchedPaaSTemplate;
import alien4cloud.paas.cloudify3.service.model.MatchedPaaSComputeTemplate;
import alien4cloud.paas.cloudify3.service.model.MatchedPaaSTemplate;
import alien4cloud.paas.cloudify3.service.model.Relationship;
import alien4cloud.paas.ha.AvailabilityZoneAllocator;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.paas.plan.BuildPlanGenerator;
import alien4cloud.paas.plan.OperationCallActivity;
import alien4cloud.paas.plan.ParallelGateway;
import alien4cloud.paas.plan.ParallelJoinStateGateway;
import alien4cloud.paas.plan.StartEvent;
import alien4cloud.paas.plan.StateUpdateEvent;
import alien4cloud.paas.plan.WorkflowStep;
import alien4cloud.tosca.normative.NormativeRelationshipConstants;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@Component("cloudify-deployment-builder-service")
@Slf4j
public class CloudifyDeploymentBuilderService {

    @Resource(name = "cloudify-compute-template-matcher-service")
    private ComputeTemplateMatcherService computeTemplateMatcherService;

    @Resource(name = "cloudify-network-matcher-service")
    private NetworkMatcherService networkMatcherService;

    @Resource(name = "cloudify-storage-matcher-service")
    private StorageTemplateMatcherService storageMatcherService;

    private AvailabilityZoneAllocator availabilityZoneAllocator = new AvailabilityZoneAllocator();

    @Setter
    private CloudResourceMatcherConfig cloudResourceMatcherConfig;

    private <T extends IMatchedPaaSTemplate> Map<String, T> buildTemplateMap(List<T> matchedPaaSTemplates) {
        Map<String, T> mapping = Maps.newHashMap();
        for (T matchedPaaSTemplate : matchedPaaSTemplates) {
            mapping.put(matchedPaaSTemplate.getPaaSNodeTemplate().getId(), matchedPaaSTemplate);
        }
        return mapping;
    }

    private List<IndexedNodeType> getTypesOrderedByDerivedFromHierarchy(List<PaaSNodeTemplate> nodes) {
        Map<String, IndexedNodeType> nodeTypeMap = Maps.newHashMap();
        for (PaaSNodeTemplate node : nodes) {
            nodeTypeMap.put(node.getIndexedToscaElement().getElementId(), node.getIndexedToscaElement());
        }
        return IndexedModelUtils.orderByDerivedFromHierarchy(nodeTypeMap);
    }

    public CloudifyDeployment buildCloudifyDeployment(PaaSTopologyDeploymentContext deploymentContext) {
        Map<String, AvailabilityZone> availabilityZoneMap = availabilityZoneAllocator.processAllocation(deploymentContext.getPaaSTopology(),
                deploymentContext.getDeploymentSetup(), cloudResourceMatcherConfig);
        List<MatchedPaaSComputeTemplate> matchedComputes = computeTemplateMatcherService.match(deploymentContext.getPaaSTopology().getComputes(),
                deploymentContext.getDeploymentSetup().getCloudResourcesMapping(), availabilityZoneMap);
        List<MatchedPaaSTemplate<NetworkTemplate>> matchedNetworks = networkMatcherService.match(deploymentContext.getPaaSTopology().getNetworks(),
                deploymentContext.getDeploymentSetup().getNetworkMapping());
        List<MatchedPaaSTemplate<StorageTemplate>> matchedStorages = storageMatcherService.match(deploymentContext.getPaaSTopology().getVolumes(),
                deploymentContext.getDeploymentSetup().getStorageMapping());

        Map<String, IndexedNodeType> nonNativesTypesMap = Maps.newHashMap();
        Map<String, IndexedRelationshipType> nonNativesRelationshipsTypesMap = Maps.newHashMap();
        for (PaaSNodeTemplate nonNative : deploymentContext.getPaaSTopology().getNonNatives()) {
            nonNativesTypesMap.put(nonNative.getIndexedToscaElement().getElementId(), nonNative.getIndexedToscaElement());
            List<PaaSRelationshipTemplate> relationshipTemplates = nonNative.getRelationshipTemplates();
            for (PaaSRelationshipTemplate relationshipTemplate : relationshipTemplates) {
                if (!NormativeRelationshipConstants.DEPENDS_ON.equals(relationshipTemplate.getIndexedToscaElement().getElementId())
                        && !NormativeRelationshipConstants.HOSTED_ON.equals(relationshipTemplate.getIndexedToscaElement().getElementId())) {
                    nonNativesRelationshipsTypesMap.put(relationshipTemplate.getIndexedToscaElement().getElementId(),
                            relationshipTemplate.getIndexedToscaElement());
                }
            }
        }

        List<MatchedPaaSTemplate<NetworkTemplate>> matchedInternalNetworks = Lists.newArrayList();
        List<MatchedPaaSTemplate<NetworkTemplate>> matchedExternalNetworks = Lists.newArrayList();

        for (MatchedPaaSTemplate<NetworkTemplate> matchedNetwork : matchedNetworks) {
            if (matchedNetwork.getPaaSResourceTemplate().getIsExternal()) {
                matchedExternalNetworks.add(matchedNetwork);
            } else {
                matchedInternalNetworks.add(matchedNetwork);
            }
        }
        Map<String, Map<String, DeploymentArtifact>> allArtifacts = Maps.newHashMap();
        for (Map.Entry<String, PaaSNodeTemplate> nodeEntry : deploymentContext.getPaaSTopology().getAllNodes().entrySet()) {
            PaaSNodeTemplate node = nodeEntry.getValue();
            Map<String, DeploymentArtifact> artifacts = node.getIndexedToscaElement().getArtifacts();
            if (artifacts != null && !artifacts.isEmpty()) {
                allArtifacts.put(node.getId(), artifacts);
            }
        }

        Map<Relationship, Map<String, DeploymentArtifact>> allRelationshipArtifacts = Maps.newHashMap();
        for (Map.Entry<String, PaaSNodeTemplate> nodeEntry : deploymentContext.getPaaSTopology().getAllNodes().entrySet()) {
            List<PaaSRelationshipTemplate> relationships = nodeEntry.getValue().getRelationshipTemplates();
            if (relationships != null && !relationships.isEmpty()) {
                for (PaaSRelationshipTemplate relationship : relationships) {
                    Map<String, DeploymentArtifact> artifacts = relationship.getIndexedToscaElement().getArtifacts();
                    if (artifacts != null && !artifacts.isEmpty()) {
                        allRelationshipArtifacts.put(new Relationship(relationship.getId(), relationship.getSource(), relationship.getRelationshipTemplate()
                                .getTarget()), artifacts);
                    }
                }
            }
        }

        // the install workflow plan provided by alien
        StartEvent startEvent = new BuildPlanGenerator(true).generate(deploymentContext.getPaaSTopology().getComputes());
        List<WfStep> installWorkflow = buildWorkflow(startEvent);

        CloudifyDeployment deployment = new CloudifyDeployment(deploymentContext.getDeploymentPaaSId(), deploymentContext.getDeploymentId(), matchedComputes,
                matchedInternalNetworks, matchedExternalNetworks, matchedStorages, buildTemplateMap(matchedComputes),
                buildTemplateMap(matchedInternalNetworks), buildTemplateMap(matchedExternalNetworks), buildTemplateMap(matchedStorages), deploymentContext
                        .getPaaSTopology().getNonNatives(), IndexedModelUtils.orderByDerivedFromHierarchy(nonNativesTypesMap),
                IndexedModelUtils.orderByDerivedFromHierarchy(nonNativesRelationshipsTypesMap), getTypesOrderedByDerivedFromHierarchy(deploymentContext
                        .getPaaSTopology().getComputes()), getTypesOrderedByDerivedFromHierarchy(deploymentContext.getPaaSTopology().getNetworks()),
                getTypesOrderedByDerivedFromHierarchy(deploymentContext.getPaaSTopology().getVolumes()), deploymentContext.getPaaSTopology().getAllNodes(),
                allArtifacts, allRelationshipArtifacts, deploymentContext.getDeploymentSetup().getProviderDeploymentProperties(), installWorkflow);
        return deployment;
    }

    private List<WfStep> buildWorkflow(StartEvent startEvent) {
        List<WfStep> steps = new ArrayList<WfStep>();
        // just a map to remember all setState related steps (we need his to resolve joins)
        Map<String, WfStep> setStateSteps = new HashMap<String, WfStep>();
        populateWorkflow(steps, startEvent.getNextStep(), null, setStateSteps, (WfStep) null);
        for (WfStep step : steps) {
            if (step.getJoin2resolv() != null) {
                ParallelJoinStateGateway pjsg = step.getJoin2resolv();
                Map<String, String[]> stateMap = pjsg.getValidStatesPerElementMap();
                log.info("ParallelJoinStateGateway : state map : " + stateMap);
                // just look for the right task in the list
                List<WfStep> joinSteps = new ArrayList<WfStep>();
                for (Entry<String, String[]> e : stateMap.entrySet()) {
                    String nodeName = e.getKey();
                    // only one state is managed for the moment
                    String stateName = e.getValue()[0];
                    log.info("ParallelJoinStateGateway : setStateSteps content : " + setStateSteps);
                    log.info("ParallelJoinStateGateway : setStateSteps looking using : " + nodeName + "$" + stateName);
                    WfStep joinStep = setStateSteps.get(nodeName + "$" + stateName);
                    log.info("ParallelJoinStateGateway : joins step detected : " + joinStep);
                    if (joinStep != null) {
                        joinSteps.add(joinStep);
                    }
                }
                for (WfStep precedingStep : joinSteps) {
                    step.addPreceding(precedingStep);
                    precedingStep.addFollowing(step);
                }
            }
        }
        return steps;
    }

    private void populateWorkflow(List<WfStep> steps, WorkflowStep alienStep, ParallelJoinStateGateway join2resolv, Map<String, WfStep> setStateSteps,
            WfStep... lastWfSteps) {
        if (alienStep instanceof OperationCallActivity || alienStep instanceof StateUpdateEvent) {
            WfStep newStep = new WfStep();
            newStep.setJoin2resolv(join2resolv);
            if (lastWfSteps != null) {
                for (WfStep lastWfStep : lastWfSteps) {
                    if (lastWfStep != null) {
                        newStep.addPreceding(lastWfStep);
                        lastWfStep.addFollowing(newStep);
                    }
                }
            }
            WfTask t = null;
            if (alienStep instanceof OperationCallActivity) {
                OperationCallActivity oca = (OperationCallActivity) alienStep;
                WfOperationExecutionTask oet = new WfOperationExecutionTask();
                oet.setNodeId(oca.getNodeTemplateId());
                oet.setInterfaceName(oca.getInterfaceName());
                oet.setOperationName(oca.getOperationName());
                t = oet;
            } else if (alienStep instanceof StateUpdateEvent) {
                StateUpdateEvent sue = (StateUpdateEvent) alienStep;
                WfSetStateTask sst = new WfSetStateTask();
                sst.setNodeId(sue.getElementId());
                sst.setStateName(sue.getState());
                setStateSteps.put(sst.getNodeId() + "$" + sst.getStateName(), newStep);
                t = sst;
            }
            newStep.setTask(t);
            steps.add(newStep);
            populateWorkflow(steps, alienStep.getNextStep(), null, setStateSteps, newStep);
        } else if (alienStep instanceof ParallelGateway) {
            ParallelGateway pg = (ParallelGateway) alienStep;
            for (WorkflowStep ws : pg.getParallelSteps()) {
                populateWorkflow(steps, ws, join2resolv, setStateSteps, lastWfSteps);
            }
        } else if (alienStep instanceof ParallelJoinStateGateway) {
            log.info("ParallelJoinStateGateway step detected");
            ParallelJoinStateGateway pjsg = (ParallelJoinStateGateway) alienStep;
            populateWorkflow(steps, pjsg.getNextStep(), pjsg, setStateSteps, lastWfSteps);
        }
    }

}
