package alien4cloud.paas.cloudify3.blueprint;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.components.PropertyValue;
import alien4cloud.paas.cloudify3.configuration.MappingConfiguration;
import alien4cloud.paas.cloudify3.service.PropertyEvaluatorService;
import alien4cloud.paas.cloudify3.service.model.CloudifyDeployment;
import alien4cloud.paas.cloudify3.util.mapping.PropertiesMappingUtil;
import alien4cloud.paas.cloudify3.util.mapping.PropertyMapping;
import alien4cloud.paas.cloudify3.util.mapping.PropertyValueUtil;
import alien4cloud.paas.exception.NotSupportedException;

public class CommonGenerationUtil extends AbstractGenerationUtil {

    public CommonGenerationUtil(MappingConfiguration mappingConfiguration, CloudifyDeployment alienDeployment, Path recipePath,
            PropertyEvaluatorService propertyEvaluatorService) {
        super(mappingConfiguration, alienDeployment, recipePath, propertyEvaluatorService);
    }

}
