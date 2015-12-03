package alien4cloud.paas.cloudify3.blueprint;

import java.nio.file.Path;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import alien4cloud.paas.cloudify3.configuration.MappingConfiguration;
import alien4cloud.paas.cloudify3.service.OrchestratorDeploymentPropertiesService;
import alien4cloud.paas.cloudify3.service.PropertyEvaluatorService;
import alien4cloud.paas.cloudify3.service.model.CloudifyDeployment;

/**
 * Some utilities method which help generating Cloudify 3 blueprint
 *
 * @author Minh Khang VU
 */
@Slf4j
@Getter
public class BlueprintGenerationUtil extends AbstractGenerationUtil {

    private ComputeGenerationUtil compute;

    private NonNativeTypeGenerationUtil nonNative;

    private NativeTypeGenerationUtil natives;

    private WorkflowGenerationUtil workflow;

    private CommonGenerationUtil common;

    private NetworkGenerationUtil network;

    public BlueprintGenerationUtil(MappingConfiguration mappingConfiguration, CloudifyDeployment alienDeployment, Path recipePath,
            PropertyEvaluatorService propertyEvaluatorService, OrchestratorDeploymentPropertiesService deploymentPropertiesService) {
        super(mappingConfiguration, alienDeployment, recipePath, propertyEvaluatorService);
        this.compute = new ComputeGenerationUtil(mappingConfiguration, alienDeployment, recipePath, propertyEvaluatorService);
        this.nonNative = new NonNativeTypeGenerationUtil(mappingConfiguration, alienDeployment, recipePath, propertyEvaluatorService);
        this.workflow = new WorkflowGenerationUtil(mappingConfiguration, alienDeployment, recipePath, propertyEvaluatorService);
        this.common = new CommonGenerationUtil(mappingConfiguration, alienDeployment, recipePath, propertyEvaluatorService, deploymentPropertiesService);
        this.network = new NetworkGenerationUtil(mappingConfiguration, alienDeployment, recipePath, propertyEvaluatorService);
        this.natives = new NativeTypeGenerationUtil(mappingConfiguration, alienDeployment, recipePath, propertyEvaluatorService);
    }
}
