package alien4cloud.paas.cloudify3.blueprint;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import alien4cloud.model.components.PropertyDefinition;
import alien4cloud.paas.cloudify3.configuration.MappingConfiguration;
import alien4cloud.paas.cloudify3.service.PropertyEvaluatorService;
import alien4cloud.paas.cloudify3.service.model.CloudifyDeployment;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.tosca.normative.ToscaType;

import com.google.common.collect.Sets;

public class ComputeGenerationUtil extends NativeTypeGenerationUtil {

    public ComputeGenerationUtil(MappingConfiguration mappingConfiguration, CloudifyDeployment alienDeployment, Path recipePath,
            PropertyEvaluatorService propertyEvaluatorService) {
        super(mappingConfiguration, alienDeployment, recipePath, propertyEvaluatorService);
    }

    public String getDefault(PropertyDefinition propertyDefinition) {
        if (ToscaType.isSimple(propertyDefinition.getType())) {
            return StringUtils.isNotBlank(propertyDefinition.getDefault()) ? propertyDefinition.getDefault() : "\"\"";
        } else {
            return "{}";
        }
    }

    public Set<PaaSNodeTemplate> getNodesToMonitor(List<PaaSNodeTemplate> computes) {
        Set<PaaSNodeTemplate> nodesToMonitor = Sets.newHashSet();
        for (PaaSNodeTemplate compute : computes) {
            // we monitor only if the compute is not a windows type
            // TODO better way to find that this is not a windows compute, taking in accound the location
            if (!compute.getIndexedToscaElement().getElementId().contains("WindowsCompute")) {
                nodesToMonitor.add(compute);
            }
        }
        return nodesToMonitor;
    }

}
