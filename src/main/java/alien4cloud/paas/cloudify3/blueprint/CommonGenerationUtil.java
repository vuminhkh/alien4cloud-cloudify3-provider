package alien4cloud.paas.cloudify3.blueprint;

import java.nio.file.Path;

import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.components.ScalarPropertyValue;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.paas.cloudify3.CloudifyOrchestratorFactory;
import alien4cloud.paas.cloudify3.configuration.MappingConfiguration;
import alien4cloud.paas.cloudify3.model.DeploymentPropertiesNames;
import alien4cloud.paas.cloudify3.service.OrchestratorDeploymentPropertiesService;
import alien4cloud.paas.cloudify3.service.PropertyEvaluatorService;
import alien4cloud.paas.cloudify3.service.model.CloudifyDeployment;
import alien4cloud.utils.MapUtil;

public class CommonGenerationUtil extends AbstractGenerationUtil {

    private OrchestratorDeploymentPropertiesService deploymentPropertiesService;

    public CommonGenerationUtil(MappingConfiguration mappingConfiguration, CloudifyDeployment alienDeployment, Path recipePath,
            PropertyEvaluatorService propertyEvaluatorService, OrchestratorDeploymentPropertiesService deploymentPropertiesService) {
        super(mappingConfiguration, alienDeployment, recipePath, propertyEvaluatorService);

        this.deploymentPropertiesService = deploymentPropertiesService;
    }

    public String getMonitoringInterval() {
        return deploymentPropertiesService.getValueOrDefault(alienDeployment.getProviderDeploymentProperties(),
                DeploymentPropertiesNames.MONITORING_INTERVAL_INMINUTE);
    }

    public String getCfyScriptVersion() {
        return CloudifyOrchestratorFactory.CFY_SCRIPT_VERSION;
    }

    public String getScalarPropertyValue(NodeTemplate nodeTemplate, String propertyName) {
        AbstractPropertyValue value = (AbstractPropertyValue) MapUtil.get(nodeTemplate.getProperties(), propertyName);
        if (value != null) {
            return ((ScalarPropertyValue) value).getValue();
        }

        return "";
    }

}
