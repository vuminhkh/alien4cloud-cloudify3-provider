package alien4cloud.paas.cloudify3.blueprint;

import java.nio.file.Path;

import alien4cloud.paas.cloudify3.configuration.MappingConfiguration;
import alien4cloud.paas.cloudify3.service.OrchestratorDeploymentPropertiesService;
import alien4cloud.paas.cloudify3.service.PropertyEvaluatorService;
import alien4cloud.paas.cloudify3.service.model.CloudifyDeployment;

public class CommonGenerationUtil extends AbstractGenerationUtil {

    private OrchestratorDeploymentPropertiesService deploymentPropertiesService;

    public CommonGenerationUtil(MappingConfiguration mappingConfiguration, CloudifyDeployment alienDeployment, Path recipePath,
            PropertyEvaluatorService propertyEvaluatorService, OrchestratorDeploymentPropertiesService deploymentPropertiesService) {
        super(mappingConfiguration, alienDeployment, recipePath, propertyEvaluatorService);

        this.deploymentPropertiesService = deploymentPropertiesService;
    }

    public String getMonitoringInterval() {
        return deploymentPropertiesService.getValueOrDefault(alienDeployment.getProviderDeploymentProperties(),
                OrchestratorDeploymentPropertiesService.MONITORING_INTERVAL_INMINUTE);
    }

}
