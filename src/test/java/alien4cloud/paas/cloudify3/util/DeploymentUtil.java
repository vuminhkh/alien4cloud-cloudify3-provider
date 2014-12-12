package alien4cloud.paas.cloudify3.util;


import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;

import alien4cloud.model.application.DeploymentSetup;
import alien4cloud.paas.cloudify3.matcher.ComputeTemplateMatcher;
import alien4cloud.paas.cloudify3.service.model.AlienDeployment;
import alien4cloud.paas.cloudify3.service.model.PaaSComputeTemplate;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.plan.TopologyTreeBuilderService;
import alien4cloud.tosca.container.model.topology.Topology;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DeploymentUtil {

    @Resource
    private ComputeTemplateMatcher computeTemplateMatcher;

    @Resource
    private TopologyTreeBuilderService topologyTreeBuilderService;


    public AlienDeployment buildAlienDeployment(String deploymentId, Topology topology, DeploymentSetup deploymentSetup) {
        AlienDeployment alienDeployment = new AlienDeployment();
        Map<String, PaaSNodeTemplate> nodes = topologyTreeBuilderService.buildPaaSNodeTemplate(topology);
        List<PaaSNodeTemplate> computes = topologyTreeBuilderService.getHostedOnTree(nodes);
        List<PaaSComputeTemplate> matchedComputes = Lists.newArrayList();
        for (PaaSNodeTemplate compute : computes) {
            String templateId = computeTemplateMatcher.getTemplateId(deploymentSetup.getCloudResourcesMapping().get(compute.getId()));
            matchedComputes.add(new PaaSComputeTemplate(compute, templateId));
        }
        alienDeployment.setComputes(matchedComputes);
        alienDeployment.setNodes(nodes);
        alienDeployment.setTopology(topology);
        alienDeployment.setDeploymentId(deploymentId);
        return alienDeployment;
    }
}
