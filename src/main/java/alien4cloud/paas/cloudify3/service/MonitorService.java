package alien4cloud.paas.cloudify3.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import alien4cloud.component.CSARRepositorySearchService;
import alien4cloud.exception.NotFoundException;
import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.model.components.ListPropertyValue;
import alien4cloud.model.components.ScalarPropertyValue;
import alien4cloud.model.deployment.Deployment;
import alien4cloud.model.deployment.DeploymentTopology;
import alien4cloud.model.orchestrators.locations.Location;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.RelationshipTemplate;
import alien4cloud.paas.cloudify3.configuration.MappingConfigurationHolder;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSTopology;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.paas.plan.TopologyTreeBuilderService;
import alien4cloud.topology.TopologyService;
import alien4cloud.tosca.normative.NormativeRelationshipConstants;
import alien4cloud.utils.TypeMap;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

@Service
public class MonitorService {

    private static String NODE_TO_MONITOR = "nodes_to_monitor";

    public static final String MONITOR_TYPE = "alien.cloudify.nodes.Monitor";

    @Resource
    private CSARRepositorySearchService csarSearchService;

    @Resource
    private TopologyService topologyService;

    @Resource
    private MappingConfigurationHolder mappingConfigurationHolder;

    @Resource
    private TopologyTreeBuilderService topologyTreeBuilderService;

    public PaaSTopologyDeploymentContext process(PaaSTopologyDeploymentContext deploymentContext) {
        // any type that is modified is cached in this map in order to be reused later while regenerating the deployment ctx
        TypeMap cache = new TypeMap();

        Set<PaaSNodeTemplate> computesToMonitor = getMonitorableComputes(deploymentContext);
        if (CollectionUtils.isEmpty(computesToMonitor)) {
            // nothing to do...
            return deploymentContext;
        }

        NodeTemplate monitorNode = addMonitorNode(deploymentContext.getDeploymentTopology(), cache);
        linkMonitorToComputes(computesToMonitor, monitorNode, deploymentContext);

        // generate the new PaaSTopologyDeploymentContext from the modified topology
        PaaSTopologyDeploymentContext newDeploymentContext = buildTopologyDeploymentContext(deploymentContext.getDeployment(),
                deploymentContext.getLocations(), deploymentContext.getDeploymentTopology(), cache);

        return newDeploymentContext;

    }

    private PaaSTopologyDeploymentContext buildTopologyDeploymentContext(Deployment deployment, Map<String, Location> locations, DeploymentTopology topology,
            TypeMap cache) {
        PaaSTopologyDeploymentContext topologyDeploymentContext = new PaaSTopologyDeploymentContext();
        topologyDeploymentContext.setLocations(locations);
        topologyDeploymentContext.setDeployment(deployment);
        PaaSTopology paaSTopology = topologyTreeBuilderService.buildPaaSTopology(topology, cache);
        topologyDeploymentContext.setPaaSTopology(paaSTopology);
        topologyDeploymentContext.setDeploymentTopology(topology);
        topologyDeploymentContext.setDeployment(deployment);
        return topologyDeploymentContext;
    }

    private void linkMonitorToComputes(Set<PaaSNodeTemplate> computesToMonitor, NodeTemplate monitorNode, PaaSTopologyDeploymentContext deploymentContext) {
        for (PaaSNodeTemplate toMonitor : computesToMonitor) {
            RelationshipTemplate dependOnRelTemplate = new RelationshipTemplate();
            dependOnRelTemplate.setType(NormativeRelationshipConstants.DEPENDS_ON);
            dependOnRelTemplate.setTarget(toMonitor.getId());
            String relationName = monitorNode.getName().concat("DependsOn").concat(toMonitor.getId());
            monitorNode.getRelationships().put(relationName, dependOnRelTemplate);
            addNodeToMonitorPropertyValue(monitorNode, toMonitor.getId());
        }
    }

    private NodeTemplate addMonitorNode(DeploymentTopology deploymentTopology, TypeMap cache) {
        IndexedNodeType monitorType = getMonitorNodeType(deploymentTopology, cache);
        NodeTemplate monitorTemplate = topologyService.buildNodeTemplate(deploymentTopology.getDependencies(), monitorType, null);
        String name = mappingConfigurationHolder.getMappingConfiguration().getGeneratedNodePrefix().concat("_monitor");
        monitorTemplate.setName(name);
        monitorTemplate.setRelationships(Maps.<String, RelationshipTemplate> newHashMap());
        deploymentTopology.getNodeTemplates().put(name, monitorTemplate);

        // init the ndoes properties if needed
        if (monitorTemplate.getProperties() == null) {
            monitorTemplate.setProperties(Maps.<String, AbstractPropertyValue> newHashMap());
        }
        return monitorTemplate;
    }

    private void addNodeToMonitorPropertyValue(NodeTemplate monitorNodeTemplate, String nodeToMonitor) {
        AbstractPropertyValue value = monitorNodeTemplate.getProperties().get(NODE_TO_MONITOR);
        ListPropertyValue valueAsList = null;
        if (value == null) {
            valueAsList = new ListPropertyValue(Lists.newArrayList());
            monitorNodeTemplate.getProperties().put(NODE_TO_MONITOR, valueAsList);
        } else {
            valueAsList = (ListPropertyValue) value;
        }
        valueAsList.getValue().add(new ScalarPropertyValue(nodeToMonitor));
    }

    private IndexedNodeType getMonitorNodeType(DeploymentTopology deploymentTopology, TypeMap cache) {
        IndexedNodeType monitorType = cache.get(IndexedNodeType.class, MONITOR_TYPE);
        if (monitorType == null) {
            monitorType = csarSearchService.getElementInDependencies(IndexedNodeType.class, MONITOR_TYPE, deploymentTopology.getDependencies());
            if (monitorType == null) {
                // This should never happen, but in case
                throw new NotFoundException("Can't add monitoring... Type <" + MONITOR_TYPE + "> from dependencies < " + deploymentTopology.getDependencies()
                        + " > not found in repository .");
            }
            cache.put(monitorType.getElementId(), monitorType);
        }
        return monitorType;
    }

    private Set<PaaSNodeTemplate> getMonitorableComputes(PaaSTopologyDeploymentContext deploymentContext) {
        Set<PaaSNodeTemplate> computesToReplaceSet = Sets.newHashSet();
        List<PaaSNodeTemplate> computes = deploymentContext.getPaaSTopology().getComputes();
        for (PaaSNodeTemplate compute : computes) {
            // we monitor only if the compute is not a windows type
            // TODO better way to find that this is not a windows compute, taking in accound the location
            if (!compute.getIndexedToscaElement().getElementId().contains("WindowsCompute")) {
                computesToReplaceSet.add(compute);
            }
        }
        return computesToReplaceSet;
    }
}
