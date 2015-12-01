package alien4cloud.paas.cloudify3.service;

import java.util.Arrays;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;

import alien4cloud.model.components.PropertyConstraint;
import alien4cloud.model.components.PropertyDefinition;
import alien4cloud.model.components.constraints.GreaterThanConstraint;
import alien4cloud.tosca.normative.ToscaType;
import alien4cloud.utils.MapUtil;

import com.google.common.collect.Maps;

public class DeploymentPropertiesService {

    public static String MONITORING_INTERVAL_INMINUTE = "monitoring_interval_inSecond";
    // public static String LIVELINESS_TIMEOUT_INSECOND = "liveliness_timeout_inSecond";
    Map<String, PropertyDefinition> deploymentProperties;

    @PostConstruct
    public void buildDeploymentProperties() {
        deploymentProperties = Maps.newHashMap();

        // Field 1 : monitoring_interval_inSec
        PropertyDefinition monitoringInterval = new PropertyDefinition();
        monitoringInterval.setType(ToscaType.INTEGER.toString());
        monitoringInterval.setRequired(false);
        monitoringInterval.setDescription("Interval time in seconds we should check the liveliness of a compute for this deployment. Default is 1min");
        monitoringInterval.setDefault("1");
        GreaterThanConstraint intervalConstraint = new GreaterThanConstraint();
        intervalConstraint.setGreaterThan("0");
        monitoringInterval.setConstraints(Arrays.asList((PropertyConstraint) intervalConstraint));
        deploymentProperties.put(MONITORING_INTERVAL_INMINUTE, monitoringInterval);
    }

    public Map<String, PropertyDefinition> getDeploymentProperties() {
        return deploymentProperties;
    }

    public String getValue(Map<String, String> propertiesValues, String propertyName) {
        return (String) MapUtil.get(propertiesValues, propertyName);
    }

    public String getValueOrDefault(Map<String, String> propertiesValues, String propertyName) {
        String value = getValue(propertiesValues, propertyName);
        if (StringUtils.isBlank(value)) {
            PropertyDefinition definition = deploymentProperties.get(propertyName);
            if (definition != null) {
                value = definition.getDefault();
            }
        }
        return value;
    }

}
