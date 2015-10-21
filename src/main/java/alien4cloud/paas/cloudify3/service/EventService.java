package alien4cloud.paas.cloudify3.service;

import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;
import javax.xml.bind.DatatypeConverter;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import alien4cloud.paas.cloudify3.model.CloudifyLifeCycle;
import alien4cloud.paas.cloudify3.model.Event;
import alien4cloud.paas.cloudify3.model.EventType;
import alien4cloud.paas.cloudify3.model.Workflow;
import alien4cloud.paas.cloudify3.restclient.DeploymentEventClient;
import alien4cloud.paas.model.AbstractMonitorEvent;
import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.PaaSDeploymentStatusMonitorEvent;
import alien4cloud.paas.model.PaaSInstanceStateMonitorEvent;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Handle cloudify 3 events request
 */
@Component("cloudify-event-service")
@Slf4j
public class EventService {

    @Resource
    private DeploymentEventClient eventClient;

    @Resource
    private StatusService statusService;

    // TODO : May manage in a better manner this kind of state
    private Map<String, String> paaSDeploymentIdToAlienDeploymentIdMapping = Maps.newConcurrentMap();

    private Map<String, String> alienDeploymentIdToPaaSDeploymentIdMapping = Maps.newConcurrentMap();

    public void init(Map<String, PaaSTopologyDeploymentContext> activeDeploymentContexts) {
        for (Map.Entry<String, PaaSTopologyDeploymentContext> activeDeploymentContextEntry : activeDeploymentContexts.entrySet()) {
            paaSDeploymentIdToAlienDeploymentIdMapping.put(activeDeploymentContextEntry.getKey(), activeDeploymentContextEntry.getValue().getDeploymentId());
            alienDeploymentIdToPaaSDeploymentIdMapping.put(activeDeploymentContextEntry.getValue().getDeploymentId(), activeDeploymentContextEntry.getKey());
        }
    }

    /**
     * This queue is used for internal events
     */
    private List<AbstractMonitorEvent> internalProviderEventsQueue = Lists.newLinkedList();

    public synchronized ListenableFuture<AbstractMonitorEvent[]> getEventsSince(Date lastTimestamp, int batchSize) {

        // Process internal events
        final ListenableFuture<AbstractMonitorEvent[]> internalEvents = processInternalQueue(batchSize);
        if (internalEvents != null) {
            // Deliver internal events first, next time when Alien poll, we'll deliver cloudify events
            return internalEvents;
        }
        // Try to get events from cloudify
        ListenableFuture<Event[]> eventsFuture = eventClient.asyncGetBatch(null, lastTimestamp, 0, batchSize);
        Function<Event[], AbstractMonitorEvent[]> cloudify3ToAlienEventsAdapter = new Function<Event[], AbstractMonitorEvent[]>() {
            @Override
            public AbstractMonitorEvent[] apply(Event[] cloudifyEvents) {
                // Convert cloudify events to alien events
                List<AbstractMonitorEvent> alienEvents = toAlienEvents(cloudifyEvents);
                return alienEvents.toArray(new AbstractMonitorEvent[alienEvents.size()]);
            }
        };
        ListenableFuture<AbstractMonitorEvent[]> alienEventsFuture = Futures.transform(eventsFuture, cloudify3ToAlienEventsAdapter);

        // Add a callback to deliver deployment status events
        Futures.addCallback(alienEventsFuture, new FutureCallback<AbstractMonitorEvent[]>() {
            @Override
            public void onSuccess(AbstractMonitorEvent[] result) {
                for (final AbstractMonitorEvent event : result) {
                    if (event instanceof PaaSDeploymentStatusMonitorEvent) {
                        if (log.isDebugEnabled()) {
                            log.debug("Send event {} to Alien", event);
                        }
                        final DeploymentStatus deploymentStatus = ((PaaSDeploymentStatusMonitorEvent) event).getDeploymentStatus();
                        final String paaSDeploymentId = alienDeploymentIdToPaaSDeploymentIdMapping.get(event.getDeploymentId());
                        statusService.registerDeploymentEvent(paaSDeploymentId, deploymentStatus);
                        if (DeploymentStatus.DEPLOYED.equals(deploymentStatus)) {
                            log.info("Deployment {} has finished successfully", paaSDeploymentId);
                        } else if (DeploymentStatus.UNDEPLOYED.equals(deploymentStatus)) {
                            log.info("Un-Deployment {} has finished successfully", paaSDeploymentId);
                            paaSDeploymentIdToAlienDeploymentIdMapping.remove(paaSDeploymentId);
                            alienDeploymentIdToPaaSDeploymentIdMapping.remove(event.getDeploymentId());
                        } else {
                            log.info("Deployment {} has finished with status {}", paaSDeploymentId,
                                    ((PaaSDeploymentStatusMonitorEvent) event).getDeploymentStatus());
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

    public synchronized void registerDeploymentEvent(String deploymentPaaSId, String deploymentId, DeploymentStatus deploymentStatus) {
        statusService.registerDeploymentEvent(deploymentPaaSId, deploymentStatus);
        paaSDeploymentIdToAlienDeploymentIdMapping.put(deploymentPaaSId, deploymentId);
        alienDeploymentIdToPaaSDeploymentIdMapping.put(deploymentId, deploymentPaaSId);
        PaaSDeploymentStatusMonitorEvent deploymentStatusMonitorEvent = new PaaSDeploymentStatusMonitorEvent();
        deploymentStatusMonitorEvent.setDeploymentStatus(deploymentStatus);
        deploymentStatusMonitorEvent.setDeploymentId(deploymentId);
        internalProviderEventsQueue.add(deploymentStatusMonitorEvent);
    }

    private ListenableFuture<AbstractMonitorEvent[]> processInternalQueue(int batchSize) {
        if (internalProviderEventsQueue.isEmpty()) {
            return null;
        }
        List<AbstractMonitorEvent> toBeReturned = internalProviderEventsQueue;
        if (internalProviderEventsQueue.size() > batchSize) {
            // There are more than the required batch
            toBeReturned = internalProviderEventsQueue.subList(0, batchSize);
        }
        try {
            if (log.isDebugEnabled()) {
                for (AbstractMonitorEvent event : toBeReturned) {
                    log.debug("Send event {} to Alien", event);
                }
            }
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

    private AbstractMonitorEvent toAlienEvent(Event cloudifyEvent) {
        AbstractMonitorEvent alienEvent;
        switch (cloudifyEvent.getEventType()) {
        case EventType.WORKFLOW_SUCCEEDED:
            PaaSDeploymentStatusMonitorEvent succeededStatusEvent = new PaaSDeploymentStatusMonitorEvent();
            if (Workflow.INSTALL.equals(cloudifyEvent.getContext().getWorkflowId())) {
                succeededStatusEvent.setDeploymentStatus(DeploymentStatus.DEPLOYED);
            } else if (Workflow.DELETE_DEPLOYMENT_ENVIRONMENT.equals(cloudifyEvent.getContext().getWorkflowId())) {
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
            if (Workflow.DELETE_DEPLOYMENT_ENVIRONMENT.equals(cloudifyEvent.getContext().getWorkflowId())
                    && "riemann_controller.tasks.delete".equals(cloudifyEvent.getContext().getTaskName())) {
                PaaSDeploymentStatusMonitorEvent undeployedEvent = new PaaSDeploymentStatusMonitorEvent();
                undeployedEvent.setDeploymentStatus(DeploymentStatus.UNDEPLOYED);
                alienEvent = undeployedEvent;
            } else {
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
            }
            break;
        default:
            return null;
        }
        alienEvent.setDate(DatatypeConverter.parseDateTime(cloudifyEvent.getTimestamp()).getTimeInMillis());
        String alienDeploymentId = paaSDeploymentIdToAlienDeploymentIdMapping.get(cloudifyEvent.getContext().getDeploymentId());
        if (alienDeploymentId == null) {
            if (log.isDebugEnabled()) {
                log.debug("Alien deployment id is not found for paaS deployment {}, must ignore this event {}", cloudifyEvent.getContext().getDeploymentId(),
                        cloudifyEvent);
            }
            return null;
        }
        alienEvent.setDeploymentId(alienDeploymentId);
        return alienEvent;
    }
}
