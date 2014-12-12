package alien4cloud.paas.cloudify3.service;

import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.springframework.stereotype.Component;

import alien4cloud.model.application.DeploymentSetup;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.tosca.container.model.topology.Topology;
import lombok.extern.slf4j.Slf4j;

/**
 * Handle deployment of the topology on cloudify 3
 *
 * @author Minh Khang VU
 */
@Component("cloudify-deployment-service")
@Slf4j
public class DeploymentService {

    @Resource
    private BlueprintService blueprintService;

    public void deploy(String deploymentName, String deploymentId, Topology topology, List<PaaSNodeTemplate> computes, Map<String, PaaSNodeTemplate> nodes, DeploymentSetup setup) {
        log.info("Deploying app {} with id {}", deploymentName, deploymentId);
    }

    public void undeploy(String deploymentId) {
        log.info("Undeploying app {} with id {}", deploymentId);
    }

}