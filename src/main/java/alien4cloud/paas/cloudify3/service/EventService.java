package alien4cloud.paas.cloudify3.service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;
import javax.xml.bind.DatatypeConverter;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import alien4cloud.paas.cloudify3.dao.EventDAO;
import alien4cloud.paas.cloudify3.dao.NodeInstanceDAO;
import alien4cloud.paas.cloudify3.model.CloudifyLifeCycle;
import alien4cloud.paas.cloudify3.model.Event;
import alien4cloud.paas.cloudify3.model.EventType;
import alien4cloud.paas.cloudify3.model.NodeInstance;
import alien4cloud.paas.cloudify3.model.NodeInstanceStatus;
import alien4cloud.paas.cloudify3.model.Workflow;
import alien4cloud.paas.cloudify3.util.MapUtil;
import alien4cloud.paas.model.AbstractMonitorEvent;
import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.InstanceStatus;
import alien4cloud.paas.model.PaaSDeploymentStatusMonitorEvent;
import alien4cloud.paas.model.PaaSInstanceStateMonitorEvent;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Handle cloudify 3 events request
 *
 * @author Minh Khang VU
 */
@Component("cloudify-event-service")
@Slf4j
public class EventService {

    @Resource
    private EventDAO eventDAO;

    @Resource
    private NodeInstanceDAO nodeInstanceDAO;

    /**
     * This queue is used for internal events
     */
    private List<AbstractMonitorEvent> internalProviderEventsQueue = Lists.newLinkedList();

    public ListenableFuture<AbstractMonitorEvent[]> getEventsSince(Date lastTimestamp, int batchSize) {
//        if (!internalProviderEventsQueue.isEmpty()) {
//            List<AbstractMonitorEvent> toBeReturned = internalProviderEventsQueue;
//            if (internalProviderEventsQueue.size() > batchSize) {
//                toBeReturned = internalProviderEventsQueue.subList(0, batchSize);
//            }
//            for (AbstractMonitorEvent event : internalProviderEventsQueue) {
//                event.setDate(lastTimestamp.getTime());
//            }
//            try {
//                return Futures.immediateFuture(internalProviderEventsQueue.toArray(new AbstractMonitorEvent[internalProviderEventsQueue.size()]));
//            } finally {
//                if(toBeReturned.size() == )
//                internalProviderEventsQueue.clear();
//            }
//        }
        ListenableFuture<Event[]> eventsFuture = eventDAO.asyncGetBatch(null, lastTimestamp, 0, batchSize);
        AsyncFunction<Event[], AbstractMonitorEvent[]> cloudify3ToAlienEventsAdapter = new AsyncFunction<Event[], AbstractMonitorEvent[]>() {
            @Override
            public ListenableFuture<AbstractMonitorEvent[]> apply(Event[] cloudifyEvents) {

                final List<AbstractMonitorEvent> alienEvents = Lists.newArrayList();
                final Map<String, List<PaaSInstanceStateMonitorEvent>> instanceEventByDeployments = Maps.newHashMap();
                for (Event cloudifyEvent : cloudifyEvents) {
                    AbstractMonitorEvent alienEvent = toAlienEvent(cloudifyEvent);
                    if (alienEvent != null) {
                        if (log.isDebugEnabled()) {
                            log.debug("Received event {}", cloudifyEvent);
                        }
                        alienEvents.add(alienEvent);
                        if (alienEvent instanceof PaaSInstanceStateMonitorEvent) {
                            PaaSInstanceStateMonitorEvent instanceStateMonitorEvent = (PaaSInstanceStateMonitorEvent) alienEvent;
                            List<PaaSInstanceStateMonitorEvent> instanceEvensForDeployment = instanceEventByDeployments.get(instanceStateMonitorEvent
                                    .getDeploymentId());
                            if (instanceEvensForDeployment == null) {
                                instanceEvensForDeployment = Lists.newArrayList();
                                instanceEventByDeployments.put(instanceStateMonitorEvent.getDeploymentId(), instanceEvensForDeployment);
                            }
                            instanceEvensForDeployment.add(instanceStateMonitorEvent);
                        }
                    } else {
                        if (log.isDebugEnabled()) {
                            log.debug("Filtered event {}", cloudifyEvent);
                        }
                    }
                }
                // At this point alienEvents do not have runtime properties, we must enrich with this information
                if (instanceEventByDeployments.isEmpty()) {
                    return Futures.immediateFuture(alienEvents.toArray(new AbstractMonitorEvent[alienEvents.size()]));
                }
                // Retrieve node instances for deployments that has PaaSInstanceStateMonitorEvent
                Set<String> deploymentsThatNeedNodeInstanceInfo = instanceEventByDeployments.keySet();
                List<ListenableFuture<NodeInstance[]>> nodeInstanceFutures = Lists.newArrayList();
                for (String deploymentThatNeedNodeInstanceInfo : deploymentsThatNeedNodeInstanceInfo) {
                    nodeInstanceFutures.add(nodeInstanceDAO.asyncList(deploymentThatNeedNodeInstanceInfo));
                }
                ListenableFuture<List<NodeInstance[]>> nodeInstancesFuture = Futures.allAsList(nodeInstanceFutures);
                // Try to convert the list of array of node instances to a map of deployment --> node instance id --> node instance
                Function<List<NodeInstance[]>, Map<String, Map<String, NodeInstance>>> nodeInstancesListToMapAdapter = new Function<List<NodeInstance[]>, Map<String, Map<String, NodeInstance>>>() {
                    @Override
                    public Map<String, Map<String, NodeInstance>> apply(List<NodeInstance[]> nodeInstancesList) {
                        Map<String, Map<String, NodeInstance>> nodeInstancesMapByDeployment = Maps.newHashMap();
                        for (NodeInstance[] nodeInstances : nodeInstancesList) {
                            if (nodeInstances != null && nodeInstances.length > 0) {
                                Map<String, NodeInstance> nodeInstanceMap = Maps.newHashMap();
                                nodeInstancesMapByDeployment.put(nodeInstances[0].getDeploymentId(), nodeInstanceMap);
                                for (NodeInstance nodeInstance : nodeInstances) {
                                    nodeInstanceMap.put(nodeInstance.getId(), nodeInstance);
                                }
                            }
                        }
                        return nodeInstancesMapByDeployment;
                    }
                };
                ListenableFuture<Map<String, Map<String, NodeInstance>>> nodeInstancesMapFuture = Futures.transform(nodeInstancesFuture,
                        nodeInstancesListToMapAdapter);
                Function<Map<String, Map<String, NodeInstance>>, AbstractMonitorEvent[]> nodeInstancesMapToEventsAdapter = new Function<Map<String, Map<String, NodeInstance>>, AbstractMonitorEvent[]>() {
                    @Override
                    public AbstractMonitorEvent[] apply(Map<String, Map<String, NodeInstance>> nodeInstancesMap) {
                        for (Map.Entry<String, List<PaaSInstanceStateMonitorEvent>> instanceStateEventEntry : instanceEventByDeployments.entrySet()) {
                            Map<String, NodeInstance> allDeploymentInstances = nodeInstancesMap.get(instanceStateEventEntry.getKey());
                            if (allDeploymentInstances != null) {
                                for (PaaSInstanceStateMonitorEvent instanceStateMonitorEvent : instanceStateEventEntry.getValue()) {
                                    if (allDeploymentInstances.containsKey(instanceStateMonitorEvent.getInstanceId())) {
                                        NodeInstance nodeInstance = allDeploymentInstances.get(instanceStateMonitorEvent.getInstanceId());
                                        instanceStateMonitorEvent.setRuntimeProperties(MapUtil.toString(nodeInstance.getRuntimeProperties()));
                                    }
                                }
                            }
                        }
                        if (log.isDebugEnabled()) {
                            for (AbstractMonitorEvent event : alienEvents) {
                                log.debug("Send event {} to Alien", event);
                            }
                        }
                        return alienEvents.toArray(new AbstractMonitorEvent[alienEvents.size()]);
                    }
                };
                return Futures.transform(nodeInstancesMapFuture, nodeInstancesMapToEventsAdapter);
            }
        };
        return Futures.transform(eventsFuture, cloudify3ToAlienEventsAdapter);
    }

    private AbstractMonitorEvent toAlienEvent(Event cloudifyEvent) {
        AbstractMonitorEvent alienEvent = null;
        switch (cloudifyEvent.getEventType()) {
        case EventType.WORKFLOW_STARTED:
            PaaSDeploymentStatusMonitorEvent startedStatusEvent = new PaaSDeploymentStatusMonitorEvent();
            if (Workflow.INSTALL.equals(cloudifyEvent.getContext().getWorkflowId())) {
                startedStatusEvent.setDeploymentStatus(DeploymentStatus.DEPLOYMENT_IN_PROGRESS);
            } else if (Workflow.UNINSTALL.equals(cloudifyEvent.getContext().getWorkflowId())) {
                startedStatusEvent.setDeploymentStatus(DeploymentStatus.UNDEPLOYMENT_IN_PROGRESS);
            } else {
                return null;
            }
            alienEvent = startedStatusEvent;
            break;
        case EventType.WORKFLOW_SUCCEEDED:
            PaaSDeploymentStatusMonitorEvent succeededStatusEvent = new PaaSDeploymentStatusMonitorEvent();
            if (Workflow.INSTALL.equals(cloudifyEvent.getContext().getWorkflowId())) {
                succeededStatusEvent.setDeploymentStatus(DeploymentStatus.DEPLOYED);
            } else if (Workflow.UNINSTALL.equals(cloudifyEvent.getContext().getWorkflowId())) {
                succeededStatusEvent.setDeploymentStatus(DeploymentStatus.UNDEPLOYED);
            } else {
                return null;
            }
            alienEvent = succeededStatusEvent;
            break;
        case EventType.WORKFLOW_FAILED:
            PaaSDeploymentStatusMonitorEvent failedStatusEvent = new PaaSDeploymentStatusMonitorEvent();
            failedStatusEvent.setDeploymentStatus(DeploymentStatus.FAILURE);
            alienEvent = failedStatusEvent;
            break;
        case EventType.TASK_SUCCEEDED:
            String newInstanceState = CloudifyLifeCycle.getSucceededInstanceState(cloudifyEvent.getContext().getOperation());
            if (newInstanceState == null) {
                return null;
            }
            PaaSInstanceStateMonitorEvent instanceTaskStartedEvent = new PaaSInstanceStateMonitorEvent();
            instanceTaskStartedEvent.setInstanceId(cloudifyEvent.getContext().getNodeId());
            instanceTaskStartedEvent.setNodeTemplateId(cloudifyEvent.getContext().getNodeName());
            instanceTaskStartedEvent.setInstanceState(newInstanceState);
            instanceTaskStartedEvent.setInstanceStatus(getInstanceStatusFromState(newInstanceState));
            alienEvent = instanceTaskStartedEvent;
            break;
        default:
            return null;
        }
        alienEvent.setDate(DatatypeConverter.parseDateTime(cloudifyEvent.getTimestamp()).getTimeInMillis());
        alienEvent.setDeploymentId(cloudifyEvent.getContext().getDeploymentId());
        return alienEvent;
    }

    private InstanceStatus getInstanceStatusFromState(String state) {
        switch (state) {
        case NodeInstanceStatus.STARTED:
            return InstanceStatus.SUCCESS;
        case NodeInstanceStatus.STOPPING:
        case NodeInstanceStatus.STOPPED:
        case NodeInstanceStatus.STARTING:
        case NodeInstanceStatus.CONFIGURING:
        case NodeInstanceStatus.CONFIGURED:
        case NodeInstanceStatus.CREATING:
        case NodeInstanceStatus.CREATED:
        case NodeInstanceStatus.DELETING:
            return InstanceStatus.PROCESSING;
        case NodeInstanceStatus.DELETED:
            return null;
        default:
            return InstanceStatus.FAILURE;
        }
    }
}
