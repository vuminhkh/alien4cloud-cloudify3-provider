package alien4cloud.paas.cloudify3.service;

import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;
import javax.xml.bind.DatatypeConverter;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import alien4cloud.common.AlienConstants;
import alien4cloud.paas.cloudify3.dao.DeploymentEventDAO;
import alien4cloud.paas.cloudify3.dao.NodeDAO;
import alien4cloud.paas.cloudify3.dao.NodeInstanceDAO;
import alien4cloud.paas.cloudify3.model.AbstractCloudifyModel;
import alien4cloud.paas.cloudify3.model.CloudifyLifeCycle;
import alien4cloud.paas.cloudify3.model.Event;
import alien4cloud.paas.cloudify3.model.EventType;
import alien4cloud.paas.cloudify3.model.Node;
import alien4cloud.paas.cloudify3.model.NodeInstance;
import alien4cloud.paas.cloudify3.model.Workflow;
import alien4cloud.paas.cloudify3.service.model.NativeType;
import alien4cloud.paas.cloudify3.util.MapUtil;
import alien4cloud.paas.model.AbstractMonitorEvent;
import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.PaaSDeploymentStatusMonitorEvent;
import alien4cloud.paas.model.PaaSInstanceStateMonitorEvent;
import alien4cloud.paas.model.PaaSInstanceStorageMonitorEvent;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.tosca.normative.NormativeBlockStorageConstants;

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
    private DeploymentEventDAO eventDAO;

    @Resource
    private NodeInstanceDAO nodeInstanceDAO;

    @Resource
    private NodeDAO nodeDAO;

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
                        if (log.isDebugEnabled()) {
                            log.debug("Send event {} to Alien", event);
                        }
                        final DeploymentStatus deploymentStatus = ((PaaSDeploymentStatusMonitorEvent) event).getDeploymentStatus();
                        final String paaSDeploymentId = alienDeploymentIdToPaaSDeploymentIdMapping.get(event.getDeploymentId());
                        statusService.registerDeploymentEvent(paaSDeploymentId, deploymentStatus);
                        if (DeploymentStatus.DEPLOYED.equals(deploymentStatus)) {
                            log.info("Deployment {} has finished successfully", paaSDeploymentId);
                            ListenableFuture<NodeInstance[]> instancesFuture = nodeInstanceDAO.asyncList(paaSDeploymentId);
                            ListenableFuture<Node[]> nodesFuture = nodeDAO.asyncList(paaSDeploymentId, null);
                            ListenableFuture<List<AbstractCloudifyModel[]>> combinedFutures = Futures.allAsList(instancesFuture, nodesFuture);

                            Futures.addCallback(combinedFutures, new FutureCallback<List<AbstractCloudifyModel[]>>() {
                                @Override
                                public void onSuccess(List<AbstractCloudifyModel[]> nodeInfos) {
                                    NodeInstance[] instances = (NodeInstance[]) nodeInfos.get(0);
                                    Node[] nodes = (Node[]) nodeInfos.get(1);
                                    Map<String, Node> nodeMap = Maps.newHashMap();
                                    for (Node node : nodes) {
                                        nodeMap.put(node.getId(), node);
                                    }
                                    for (NodeInstance nodeInstance : instances) {
                                        AbstractMonitorEvent alienEvent = toAlienEvent(nodeInstance, nodeMap.get(nodeInstance.getNodeId()));
                                        if (alienEvent != null) {
                                            internalProviderEventsQueue.add(alienEvent);
                                        }
                                    }
                                }

                                @Override
                                public void onFailure(Throwable t) {
                                    log.error("Error happened while trying to retrieve runtime properties for finished deployment " + paaSDeploymentId, t);
                                }
                            });
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

    private ListenableFuture<AbstractMonitorEvent[]> enrichAlienEvents(final List<AbstractMonitorEvent> alienEvents) {

        // From the list of alien events, get a map of deployment id --> instance state events
        final Map<String, List<PaaSInstanceStateMonitorEvent>> instanceEventByDeployments = extractInstanceStateEventsMap(alienEvents);

        // If there are no instance state events, return immediately
        if (instanceEventByDeployments.isEmpty()) {
            return Futures.immediateFuture(alienEvents.toArray(new AbstractMonitorEvent[alienEvents.size()]));
        }

        // From the map of deployment id --> instance state event, get the map of deployment id --> cloudify node instance
        ListenableFuture<Map<String, Map<String, NodeInstance>>> nodeInstancesMapFuture = getNodeInstancesByDeploymentMap(instanceEventByDeployments.keySet());

        ListenableFuture<Map<String, Map<String, Node>>> nodesMapFuture = getNodesByDeploymentMap(instanceEventByDeployments.keySet());

        ListenableFuture<List<Map<String, ? extends Map<String, ? extends AbstractCloudifyModel>>>> combinedFutures = Futures.allAsList(nodeInstancesMapFuture,
                nodesMapFuture);
        Function<List<Map<String, ? extends Map<String, ? extends AbstractCloudifyModel>>>, AbstractMonitorEvent[]> nodeInstancesMapToEventsAdapter = new Function<List<Map<String, ? extends Map<String, ? extends AbstractCloudifyModel>>>, AbstractMonitorEvent[]>() {

            @Override
            public AbstractMonitorEvent[] apply(List<Map<String, ? extends Map<String, ? extends AbstractCloudifyModel>>> nodesInfo) {
                Map<String, Map<String, NodeInstance>> nodeInstancesMap = (Map<String, Map<String, NodeInstance>>) nodesInfo.get(0);
                Map<String, Map<String, Node>> nodeMap = (Map<String, Map<String, Node>>) nodesInfo.get(1);
                for (Map.Entry<String, List<PaaSInstanceStateMonitorEvent>> instanceStateEventEntry : instanceEventByDeployments.entrySet()) {
                    Map<String, NodeInstance> allDeployedInstances = nodeInstancesMap.get(instanceStateEventEntry.getKey());
                    Map<String, Node> allDeployedNodes = nodeMap.get(instanceStateEventEntry.getKey());
                    if (allDeployedInstances != null && allDeployedNodes != null) {
                        Iterator<PaaSInstanceStateMonitorEvent> instanceStateMonitorEventIterator = instanceStateEventEntry.getValue().iterator();
                        while (instanceStateMonitorEventIterator.hasNext()) {
                            PaaSInstanceStateMonitorEvent instanceStateMonitorEvent = instanceStateMonitorEventIterator.next();
                            Node node = allDeployedNodes.get(instanceStateMonitorEvent.getNodeTemplateId());
                            NodeInstance nodeInstance = allDeployedInstances.get(instanceStateMonitorEvent.getInstanceId());
                            if (nodeInstance != null && nodeInstance.getRuntimeProperties() != null) {
                                Map<String, String> runtimeProperties = MapUtil.toString(nodeInstance.getRuntimeProperties());
                                instanceStateMonitorEvent.setRuntimeProperties(runtimeProperties);
                                if (node != null && node.getProperties() != null) {
                                    String nativeType = statusService.getNativeType(node);
                                    if (nativeType != null) {
                                        Map<String, String> attributes = statusService.getAttributesFromRuntimeProperties(nativeType, runtimeProperties);
                                        instanceStateMonitorEvent.setAttributes(attributes);
                                        if (nativeType.equals(NativeType.VOLUME)) {
                                            String volumeId = attributes.get(NormativeBlockStorageConstants.VOLUME_ID);
                                            if (volumeId != null) {
                                                Object rawVolumeProperties = node.getProperties().get("volume");
                                                if (rawVolumeProperties != null && rawVolumeProperties instanceof Map) {
                                                    Object rawAvailabilityZone = ((Map<String, Object>) rawVolumeProperties).get("availability_zone");
                                                    if (rawAvailabilityZone != null && rawAvailabilityZone instanceof String) {
                                                        volumeId = rawAvailabilityZone + AlienConstants.STORAGE_AZ_VOLUMEID_SEPARATOR + volumeId;
                                                    }
                                                }
                                                instanceStateMonitorEventIterator.remove();
                                                PaaSInstanceStorageMonitorEvent storageEvent = new PaaSInstanceStorageMonitorEvent(instanceStateMonitorEvent,
                                                        volumeId, false);
                                                int eventIndex = alienEvents.indexOf(instanceStateMonitorEvent);
                                                // Replace the old event with the storage event
                                                // It's ugly and temporary, as we'll try not to create a new type of event for each native type
                                                alienEvents.set(eventIndex, storageEvent);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                return alienEvents.toArray(new AbstractMonitorEvent[alienEvents.size()]);
            }
        };
        return Futures.transform(combinedFutures, nodeInstancesMapToEventsAdapter);
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

    private ListenableFuture<Map<String, Map<String, Node>>> getNodesByDeploymentMap(Set<String> deploymentsThatNeedNodeInfo) {

        List<ListenableFuture<Node[]>> nodeFutures = Lists.newArrayList();
        // Retrieve node instances for deployments that has PaaSInstanceStateMonitorEvent
        for (String deploymentThatNeedNodeInstanceInfo : deploymentsThatNeedNodeInfo) {
            nodeFutures.add(nodeDAO.asyncList(deploymentThatNeedNodeInstanceInfo, null));
        }
        ListenableFuture<List<Node[]>> nodeInstancesFuture = Futures.allAsList(nodeFutures);
        // Try to convert the list of array of node to a map of deployment --> node instance id --> node instance
        Function<List<Node[]>, Map<String, Map<String, Node>>> nodeInstancesListToMapAdapter = new Function<List<Node[]>, Map<String, Map<String, Node>>>() {
            @Override
            public Map<String, Map<String, Node>> apply(List<Node[]> nodeArrayList) {
                Map<String, Map<String, Node>> nodeMapByDeployment = Maps.newHashMap();
                for (Node[] nodeArray : nodeArrayList) {
                    if (nodeArray != null && nodeArray.length > 0) {
                        Map<String, Node> nodeMap = Maps.newHashMap();
                        nodeMapByDeployment.put(nodeArray[0].getDeploymentId(), nodeMap);
                        for (Node node : nodeArray) {
                            nodeMap.put(node.getId(), node);
                        }
                    }
                }
                return nodeMapByDeployment;
            }
        };
        return Futures.transform(nodeInstancesFuture, nodeInstancesListToMapAdapter);
    }

    private ListenableFuture<Map<String, Map<String, NodeInstance>>> getNodeInstancesByDeploymentMap(Set<String> deploymentsThatNeedNodeInstanceInfo) {

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
                String alienDeploymentId = instanceStateMonitorEvent.getDeploymentId();
                String paaSDeploymentId = alienDeploymentIdToPaaSDeploymentIdMapping.get(alienDeploymentId);
                List<PaaSInstanceStateMonitorEvent> instanceEvensForDeployment = instanceEventByDeployments.get(paaSDeploymentId);
                if (instanceEvensForDeployment == null) {
                    instanceEvensForDeployment = Lists.newArrayList();
                    instanceEventByDeployments.put(paaSDeploymentId, instanceEvensForDeployment);
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

    private PaaSInstanceStateMonitorEvent toAlienEvent(NodeInstance nodeInstance, Node node) {
        PaaSInstanceStateMonitorEvent instanceStateMonitorEvent = new PaaSInstanceStateMonitorEvent();
        instanceStateMonitorEvent.setInstanceState(nodeInstance.getState());
        instanceStateMonitorEvent.setInstanceStatus(statusService.getInstanceStatusFromState(nodeInstance.getState()));
        String alienDeploymentId = paaSDeploymentIdToAlienDeploymentIdMapping.get(nodeInstance.getDeploymentId());
        if (alienDeploymentId == null) {
            if (log.isDebugEnabled()) {
                log.debug("Alien deployment id is not found for paaS deployment {}, must ignore this node instance {}", nodeInstance.getDeploymentId(),
                        nodeInstance);
            }
            return null;
        }
        instanceStateMonitorEvent.setDeploymentId(nodeInstance.getDeploymentId());
        instanceStateMonitorEvent.setNodeTemplateId(nodeInstance.getNodeId());
        instanceStateMonitorEvent.setInstanceId(nodeInstance.getId());
        if (nodeInstance.getRuntimeProperties() != null) {
            Map<String, String> runtimeProperties = MapUtil.toString(nodeInstance.getRuntimeProperties());
            instanceStateMonitorEvent.setRuntimeProperties(runtimeProperties);
            if (node != null && node.getProperties() != null) {
                String nativeType = statusService.getNativeType(node);
                if (nativeType != null) {
                    instanceStateMonitorEvent.setAttributes(statusService.getAttributesFromRuntimeProperties(nativeType, runtimeProperties));
                }
            }
        }
        return instanceStateMonitorEvent;
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
