package alien4cloud.paas.cloudify3.blueprint;

import java.nio.file.Path;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import alien4cloud.paas.cloudify3.configuration.MappingConfiguration;
import alien4cloud.paas.cloudify3.configuration.ProviderMappingConfiguration;
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

    private NetworkGenerationUtil network;

    private VolumeGenerationUtil volume;

    private NonNativeTypeGenerationUtil nonNative;

    private WorkflowGenerationUtil workflow;

    public BlueprintGenerationUtil(MappingConfiguration mappingConfiguration, ProviderMappingConfiguration providerMappingConfiguration,
            CloudifyDeployment alienDeployment, Path recipePath, PropertyEvaluatorService propertyEvaluatorService) {
        super(mappingConfiguration, providerMappingConfiguration, alienDeployment, recipePath, propertyEvaluatorService);
        this.compute = new ComputeGenerationUtil(mappingConfiguration, providerMappingConfiguration, alienDeployment, recipePath, propertyEvaluatorService);
        this.network = new NetworkGenerationUtil(mappingConfiguration, providerMappingConfiguration, alienDeployment, recipePath, propertyEvaluatorService);
        this.volume = new VolumeGenerationUtil(mappingConfiguration, providerMappingConfiguration, alienDeployment, recipePath, propertyEvaluatorService);
        this.nonNative = new NonNativeTypeGenerationUtil(mappingConfiguration, providerMappingConfiguration, alienDeployment, recipePath,
                propertyEvaluatorService);
        this.workflow = new WorkflowGenerationUtil(mappingConfiguration, providerMappingConfiguration, alienDeployment, recipePath, propertyEvaluatorService);
    }
}
