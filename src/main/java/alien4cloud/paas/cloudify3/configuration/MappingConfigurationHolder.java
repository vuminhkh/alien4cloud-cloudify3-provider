package alien4cloud.paas.cloudify3.configuration;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import alien4cloud.model.deployment.DeploymentTopology;
import alien4cloud.model.topology.AbstractPolicy;
import alien4cloud.model.topology.LocationPlacementPolicy;
import alien4cloud.model.topology.NodeGroup;
import alien4cloud.paas.cloudify3.error.BadConfigurationException;
import alien4cloud.paas.cloudify3.error.SingleLocationRequiredException;
import alien4cloud.plugin.model.ManagedPlugin;
import alien4cloud.utils.YamlParserUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component("cloudify-mapping-configuration-holder")
public class MappingConfigurationHolder {

    @Resource
    private CloudConfigurationHolder cloudConfigurationHolder;

    @Resource
    private ManagedPlugin pluginContext;

    private ObjectMapper yamlObjectMapper = YamlParserUtil.createYamlObjectMapper();

    @Getter
    private MappingConfiguration mappingConfiguration;

    @Getter
    private String locationType;

    private ProviderMappingConfiguration providerMappingConfiguration;

    @PostConstruct
    public void postConstruct() throws Exception {
        mappingConfiguration = yamlObjectMapper.readValue(
                new BufferedInputStream(Files.newInputStream(pluginContext.getPluginPath().resolve("mapping/mapping.yaml"))), MappingConfiguration.class);
    }

    /**
     * Get the mapping for the current location (Cloudify 3 actually supports a single location).
     *
     * @return The mapping for the current location.
     */
    public ProviderMappingConfiguration getProviderMappingConfiguration() {
        return providerMappingConfiguration;
    }

    /**
     * Load the provider mapping to deploy the given deployment topology.
     *
     * @param deploymentTopology The deployment topology that should contains a single location placement policy.
     */
    public void loadProviderMapping(DeploymentTopology deploymentTopology) {
        if (deploymentTopology == null || deploymentTopology.getLocationGroups() == null || deploymentTopology.getLocationGroups().size() != 1) {
            throw new SingleLocationRequiredException();
        }
        LocationPlacementPolicy placementPolicy = null;

        for (NodeGroup group : deploymentTopology.getLocationGroups().values()) {
            for (AbstractPolicy policy : group.getPolicies()) {
                if (policy instanceof LocationPlacementPolicy) {
                    if (placementPolicy == null) {
                        placementPolicy = (LocationPlacementPolicy) policy;
                    } else {
                        throw new SingleLocationRequiredException();
                    }
                } // ignore other policies
            }
        }
        if (placementPolicy == null) {
            throw new SingleLocationRequiredException();
        }
        loadProviderMapping(placementPolicy.getType());
    }

    private void loadProviderMapping(String locationType) {
        log.info("Loading mapping for location of type " + locationType);
        if (locationType.equals(this.locationType)) {
            return; // The location is already initialized.
        }
        try {
            this.providerMappingConfiguration = this.yamlObjectMapper.readValue(
                    new BufferedInputStream(new FileInputStream(
                            this.pluginContext.getPluginPath().resolve("provider").resolve(locationType).resolve("mapping.yaml").toFile())),
                    ProviderMappingConfiguration.class);
        } catch (IOException e) {
            throw new BadConfigurationException("Bad configuration, unable to parse provider mapping configuration", e);
        }
    }
}
