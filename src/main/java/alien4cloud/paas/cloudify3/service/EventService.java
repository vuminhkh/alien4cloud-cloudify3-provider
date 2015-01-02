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
import alien4cloud.paas.cloudify3.model.Workflow;
import alien4cloud.paas.cloudify3.util.MapUtil;
import alien4cloud.paas.model.AbstractMonitorEvent;
import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.PaaSDeploymentStatusMonitorEvent;
import alien4cloud.paas.model.PaaSInstanceStateMonitorEvent;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.FutureCallback;
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

    @Resource
    private StatusService statusService;

    /**
     * This queue is used for internal events
     */
    private List<AbstractMonitorEvent> internalProviderEventsQueue = Lists.newLinkedList();

    public synchronized ListenableFuture<AbstractMonitorEvent[]> getEventsSince(Date lastTimestamp, int batchSize) {

        // Process internal events
        final ListenableFuture<AbstractMonitorEvent[]> internalEvents = processInternalQueue(lastTimestamp, batchSize);
        if (internalEvents != null) {
            // Deliver internal events first, next time when Alien poll, we'll deliver cloudify events
            return internalEvents;
        }
        // Try to get events from cloudify
        ListenableFuture<Event[]> eventsFuture = eventDAO.asyncGetBatch(null, lastTimestamp, 0, batchSize);
        AsyncFunction<Event[], AbstractMonitorEvent[]> cloudify3ToAlienEventsAdapter = new AsyncFunction<Event[], AbstractMonitorEvent[]>() {
            @Override
            public ListenableFuture<AbstractMonitorEvent[]> apply(Event[] cloudifyEvents) {
                // Convert cloudify events to alien events
                List<AbstractMonitorEvent> alienEvents = toAlienEvents(cloudifyEvents);
                // At this point alienEvents do not have runtime properties, we must enrich them from node instance information
                return enrichAlienEvents(alienEvents);
            }
        };
        ListenableFuture<AbstractMonitorEvent[]> alienEventsFuture = Futures.transform(eventsFuture, cloudify3ToAlienEventsAdapter);

        // In case of a deployment finish event, we must send back the instance state event in order to have some runtime properties such as ip address ...
        Futures.addCallback(alienEventsFuture, new FutureCallback<AbstractMonitorEvent[]>() {
            @Override
            public void onSuccess(AbstractMonitorEvent[] result) {
                for (final AbstractMonitorEvent event : result) {
                    if (event instanceof PaaSDeploymentStatusMonitorEvent) {
                        DeploymentStatus deploymentStatus = ((PaaSDeploymentStatusMonitorEvent) event).getDeploymentStatus();
                        statusService.registerDeploymentEvent(event.getDeploymentId(), deploymentStatus);
                        if (DeploymentStatus.DEPLOYED.equals(deploymentStatus)) {
                            ListenableFuture<NodeInstance[]> nodeInstancesFuture = nodeInstanceDAO.asyncList(event.getDeploymentId());
                            Futures.addCallback(nodeInstancesFuture, new FutureCallback<NodeInstance[]>() {
                                @Override
                                public void onSuccess(NodeInstance[] result) {
                                    for (NodeInstance nodeInstance : result) {
                                        internalProviderEventsQueue.add(toAlienEvent(nodeInstance));
                                    }
                                }

                                @Override
                                public void onFailure(Throwable t) {
                                    log.error("Error happened while trying to retrieve runtime properties for finished deployment " + event.getDeploymentId(),
                                            t);
                                }
                            });
                        }
                    }
                }
            }

            @Override
            public void onFailure(Throwable t) {
                log.error("Error happened while trying to retrieve events", t);
            }
        });
        return alienEventsFuture;
    }

    public synchronized void registerDeploymentEvent(String deploymentId, DeploymentStatus deploymentStatus) {
        statusService.registerDeploymentEvent(deploymentId, deploymentStatus);
        PaaSDeploymentStatusMonitorEvent deploymentStatusMonitorEvent = new PaaSDeploymentStatusMonitorEvent();
        deploymentStatusMonitorEvent.setDeploymentStatus(deploymentStatus);
        deploymentStatusMonitorEvent.setDeploymentId(deploymentId);
        internalProviderEventsQueue.add(deploymentStatusMonitorEvent);
    }

    private ListenableFuture<AbstractMonitorEvent[]> enrichAlienEvents(final List<AbstractMonitorEvent> alienEvents) {

        // From the list of alien events, get a map of deployment id --> instance state events
        final Map<String, List<PaaSInstanceStateMonitorEvent>> instanceEventByDeployments = extractInstanceStateEventsMap(alienEvents);

        // If there are no instance state events, return immediately
        if (instanceEventByDeployments.isEmpty()) {
            return Futures.immediateFuture(alienEvents.toArray(new AbstractMonitorEvent[alienEvents.size()]));
        }

        // From the map of deployment id --> instance state event, get the map of deployment id --> cloudify node instance
        ListenableFuture<Map<String, Map<String, NodeInstance>>> nodeInstancesMapFuture = getNodeInstanceByDeploymentMap(instanceEventByDeployments);

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

    private ListenableFuture<AbstractMonitorEvent[]> processInternalQueue(Date lastTimestamp, int batchSize) {
        if (internalProviderEventsQueue.isEmpty()) {
            return null;
        }
        List<AbstractMonitorEvent> toBeReturned = internalProviderEventsQueue;
        if (internalProviderEventsQueue.size() > batchSize) {
            // There are more than the required batch
            toBeReturned = internalProviderEventsQueue.subList(0, batchSize);
        }
        for (AbstractMonitorEvent event : toBeReturned) {
            event.setDate(lastTimestamp.getTime());
        }
        try {
            return Futures.immediateFuture(toBeReturned.toArray(new AbstractMonitorEvent[toBeReturned.size()]));
        } finally {
            if (toBeReturned == internalProviderEventsQueue) {
                // Less than required batch
                internalProviderEventsQueue.clear();
            } else {
                // More than required batch
                List<AbstractMonitorEvent> newQueue = Lists.newLinkedList();
                for (int i = batchSize; i < internalProviderEventsQueue.size(); i++) {
                    newQueue.add(internalProviderEventsQueue.get(i));
                }
                internalProviderEventsQueue.clear();
                internalProviderEventsQueue = newQueue;
            }
        }
    }

    private ListenableFuture<Map<String, Map<String, NodeInstance>>> getNodeInstanceByDeploymentMap(
            Map<String, List<PaaSInstanceStateMonitorEvent>> instanceEventByDeployments) {

        Set<String> deploymentsThatNeedNodeInstanceInfo = instanceEventByDeployments.keySet();
        List<ListenableFuture<NodeInstance[]>> nodeInstanceFutures = Lists.newArrayList();
        // Retrieve node instances for deployments that has PaaSInstanceStateMonitorEvent
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
        return Futures.transform(nodeInstancesFuture, nodeInstancesListToMapAdapter);
    }

    private Map<String, List<PaaSInstanceStateMonitorEvent>> extractInstanceStateEventsMap(List<AbstractMonitorEvent> alienEvents) {
        final Map<String, List<PaaSInstanceStateMonitorEvent>> instanceEventByDeployments = Maps.newHashMap();
        for (AbstractMonitorEvent alienEvent : alienEvents) {
            if (alienEvent instanceof PaaSInstanceStateMonitorEvent) {
                PaaSInstanceStateMonitorEvent instanceStateMonitorEvent = (PaaSInstanceStateMonitorEvent) alienEvent;
                List<PaaSInstanceStateMonitorEvent> instanceEvensForDeployment = instanceEventByDeployments.get(instanceStateMonitorEvent.getDeploymentId());
                if (instanceEvensForDeployment == null) {
                    instanceEvensForDeployment = Lists.newArrayList();
                    instanceEventByDeployments.put(instanceStateMonitorEvent.getDeploymentId(), instanceEvensForDeployment);
                }
                instanceEvensForDeployment.add(instanceStateMonitorEvent);
            }
        }
        return instanceEventByDeployments;
    }

    private List<AbstractMonitorEvent> toAlienEvents(Event[] cloudifyEvents) {
        final List<AbstractMonitorEvent> alienEvents = Lists.newArrayList();
        for (Event cloudifyEvent : cloudifyEvents) {
            AbstractMonitorEvent alienEvent = toAlienEvent(cloudifyEvent);
            if (alienEvent != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Received event {}", cloudifyEvent);
                }
                alienEvents.add(alienEvent);
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("Filtered event {}", cloudifyEvent);
                }
            }
        }
        return alienEvents;
    }

    private PaaSInstanceStateMonitorEvent toAlienEvent(NodeInstance nodeInstance) {
        PaaSInstanceStateMonitorEvent instanceStateMonitorEvent = new PaaSInstanceStateMonitorEvent();
        instanceStateMonitorEvent.setInstanceState(nodeInstance.getState());
        instanceStateMonitorEvent.setInstanceStatus(statusService.getInstanceStatusFromState(nodeInstance.getState()));
        instanceStateMonitorEvent.setDeploymentId(nodeInstance.getDeploymentId());
        instanceStateMonitorEvent.setNodeTemplateId(nodeInstance.getNodeId());
        instanceStateMonitorEvent.setInstanceId(nodeInstance.getId());
        instanceStateMonitorEvent.setRuntimeProperties(MapUtil.toString(nodeInstance.getRuntimeProperties()));
        return instanceStateMonitorEvent;
    }

    private AbstractMonitorEvent toAlienEvent(Event cloudifyEvent) {
        AbstractMonitorEvent alienEvent;
        switch (cloudifyEvent.getEventType()) {
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
            instanceTaskStartedEvent.setInstanceStatus(statusService.getInstanceStatusFromState(newInstanceState));
            alienEvent = instanceTaskStartedEvent;
            break;
        default:
            return null;
        }
        alienEvent.setDate(DatatypeConverter.parseDateTime(cloudifyEvent.getTimestamp()).getTimeInMillis());
        alienEvent.setDeploymentId(cloudifyEvent.getContext().getDeploymentId());
        return alienEvent;
    }
}
