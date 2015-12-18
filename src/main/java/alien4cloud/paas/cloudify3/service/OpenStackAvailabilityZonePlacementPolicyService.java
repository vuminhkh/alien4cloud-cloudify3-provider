package alien4cloud.paas.cloudify3.service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.components.ComplexPropertyValue;
import alien4cloud.model.components.PropertyValue;
import alien4cloud.model.components.ScalarPropertyValue;
import alien4cloud.model.orchestrators.locations.Location;
import alien4cloud.model.orchestrators.locations.LocationResourceTemplate;
import alien4cloud.model.topology.AbstractPolicy;
import alien4cloud.model.topology.HaPolicy;
import alien4cloud.model.topology.NodeGroup;
import alien4cloud.orchestrators.locations.services.LocationResourceService;
import alien4cloud.orchestrators.plugin.ILocationResourceAccessor;
import alien4cloud.paas.cloudify3.error.AZAssignmentException;
import alien4cloud.paas.model.PaaSInstancePersistentResourceMonitorEvent;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;

/**
 * Workaround to enable back ha policy support in openstack.
 */
@Slf4j
@Component
public class OpenStackAvailabilityZonePlacementPolicyService {
    private static final String SERVER_PROPERTY = "server";
    private static final String VOLUME_PROPERTY = "volume";
    private static final String AZ_KEY = "availability_zone";

    @Inject
    private LocationResourceService locationResourceService;
    @Inject
    private EventService eventService;

    /**
     * Pre-process the topology to add availability zones based on the defined H.A. policies.
     *
     * @param deploymentContext The deployment context that contains the topology to update.
     */
    public void process(PaaSTopologyDeploymentContext deploymentContext) {
        Location location = deploymentContext.getLocations().values().iterator().next();

        if (!"openstack".equals(location.getInfrastructureType())) {
            log.debug("H.A. policy is supported only for openstack.");
            return;
        }

        if (deploymentContext.getDeploymentTopology().getGroups() == null) {
            return;
        }

        // for every group defined
        for (NodeGroup nodeGroup : deploymentContext.getDeploymentTopology().getGroups().values()) {
            // process policy
            for (AbstractPolicy policy : nodeGroup.getPolicies()) {
                processPolicy(location, deploymentContext, policy, nodeGroup.getName(), nodeGroup.getMembers());
            }
        }
    }

    private void processPolicy(Location location, PaaSTopologyDeploymentContext deploymentContext, AbstractPolicy policy, String groupName, Set<String> members) {
        if (!(policy instanceof HaPolicy)) {
            // we skip all policies but h.a.
            log.debug("Skipping policy as type is not h.a. but {}", policy.getType());
            return;
        }

        ILocationResourceAccessor accessor = locationResourceService.accessor(location.getId());
        List<LocationResourceTemplate> availabilityZones = accessor.getResources("alien.cloudify.openstack.nodes.AvailabilityZone");
        if (availabilityZones == null || availabilityZones.size() < 2) {
            log.warn("Skipping policy as administrator has not defined at least 2 availability zones.");
            return;
        }

        int nextAZ = 0;
        // get the location configuration nodes
        for (String member : members) {
            String availabilityZone = null;
            PaaSNodeTemplate target = deploymentContext.getPaaSTopology().getAllNodes().get(member);
            if (target.getStorageNodes() != null) {
                // there is storage nodes, try to put them in same AZ
                for (PaaSNodeTemplate volumeNode : target.getStorageNodes()) {
                    String storageAZ = getAZ(volumeNode, VOLUME_PROPERTY);
                    if (availabilityZone == null) {
                        availabilityZone = storageAZ;
                    } else if (!availabilityZone.equals(storageAZ)) {
                        // Fail as we cannot have volumes in different availability zones for the same compute.
                        log.error("Volumes attached to node {} are defined in different availability zones. HA policy will not be applied for group {}.",
                                target.getId(), groupName);
                        throw new AZAssignmentException("Volumes attached to node cannot lie in different availability zones.");
                    }
                }
            }

            if (availabilityZone == null) {
                // storage doesn't provide a zone, check if the main node provides one.
                availabilityZone = getAZ(target, SERVER_PROPERTY);
            }

            if (availabilityZone == null) {
                // pick next zone using round robin
                ScalarPropertyValue value = (ScalarPropertyValue) availabilityZones.get(nextAZ).getTemplate().getProperties().get("id");
                availabilityZone = value.getValue();
                nextAZ = nextAZ == availabilityZones.size() - 1 ? 0 : nextAZ + 1;
            }

            // set the availability zone to all nodes
            setAZ(deploymentContext.getDeploymentId(), target, availabilityZone, SERVER_PROPERTY);
            if (target.getStorageNodes() != null) {
                for (PaaSNodeTemplate volumeNode : target.getStorageNodes()) {
                    setAZ(deploymentContext.getDeploymentId(), volumeNode, availabilityZone, VOLUME_PROPERTY);
                }
            }
        }
    }

    private String getAZ(PaaSNodeTemplate paaSNodeTemplate, String handlerPropertyKey) {
        AbstractPropertyValue volumePropertyValue = paaSNodeTemplate.getNodeTemplate().getProperties().get(handlerPropertyKey);
        if (volumePropertyValue != null && volumePropertyValue instanceof PropertyValue) {
            Object volume = ((PropertyValue) volumePropertyValue).getValue();
            if (volume instanceof Map) {
                return (String) ((Map) volume).get(AZ_KEY);
            }
        }
        return null;
    }

    private void setAZ(String deploymentId, PaaSNodeTemplate paaSNodeTemplate, String availabilityZone, String handlerPropertyKey) {
        // Enqueue events so the fields will be updated in the deployment topology
        AbstractPropertyValue volumePropertyValue = paaSNodeTemplate.getNodeTemplate().getProperties().get(handlerPropertyKey);
        if (volumePropertyValue == null) {
            volumePropertyValue = new ComplexPropertyValue();
            ((PropertyValue<Map<String, Object>>) volumePropertyValue).setValue(new HashMap<String, Object>());
            paaSNodeTemplate.getNodeTemplate().getProperties().put(handlerPropertyKey, volumePropertyValue);
        }

        Map<String, Object> volumeMap = ((ComplexPropertyValue) volumePropertyValue).getValue();
        volumeMap.put(AZ_KEY, availabilityZone);

        PaaSInstancePersistentResourceMonitorEvent event = new PaaSInstancePersistentResourceMonitorEvent(paaSNodeTemplate.getId(), null, handlerPropertyKey,
                volumeMap);
        event.setDate(new Date().getTime());
        event.setDeploymentId(deploymentId);
        eventService.registerEvent(event);
    }
}