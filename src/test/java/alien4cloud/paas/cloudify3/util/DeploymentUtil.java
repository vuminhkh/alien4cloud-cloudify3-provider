package alien4cloud.paas.cloudify3.util;

import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import alien4cloud.model.application.DeploymentSetup;
import alien4cloud.paas.cloudify3.service.ComputeTemplateMatcherService;
import alien4cloud.paas.cloudify3.service.model.AlienDeployment;
import alien4cloud.paas.cloudify3.service.model.MatchedPaaSComputeTemplate;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.plan.TopologyTreeBuilderService;
import alien4cloud.tosca.container.model.topology.Topology;

import com.google.common.collect.Lists;

@Component
@Slf4j
public class DeploymentUtil {

    @Resource
    private ComputeTemplateMatcherService computeTemplateMatcherService;

    @Resource
    private TopologyTreeBuilderService topologyTreeBuilderService;

    public AlienDeployment buildAlienDeployment(String deploymentId, String deploymentName, Topology topology, DeploymentSetup deploymentSetup) {
        AlienDeployment alienDeployment = new AlienDeployment();
        Map<String, PaaSNodeTemplate> nodes = topologyTreeBuilderService.buildPaaSNodeTemplate(topology);
        List<PaaSNodeTemplate> computes = topologyTreeBuilderService.getHostedOnTree(nodes);
        List<MatchedPaaSComputeTemplate> matchedComputes = Lists.newArrayList();
        for (PaaSNodeTemplate compute : computes) {
            String templateId = computeTemplateMatcherService.getTemplateId(deploymentSetup.getCloudResourcesMapping().get(compute.getId()));
            matchedComputes.add(new MatchedPaaSComputeTemplate(compute, templateId));
        }
        alienDeployment.setComputes(matchedComputes);
        alienDeployment.setNodes(nodes);
        alienDeployment.setTopology(topology);
        alienDeployment.setDeploymentId(deploymentId);
        alienDeployment.setDeploymentName(deploymentName);
        return alienDeployment;
    }
}
