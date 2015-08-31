package alien4cloud.paas.cloudify3.configuration;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import lombok.Getter;

import org.springframework.stereotype.Component;

import alien4cloud.paas.cloudify3.error.BadConfigurationException;
import alien4cloud.plugin.model.ManagedPlugin;
import alien4cloud.utils.YamlParserUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component("cloudify-mapping-configuration-holder")
public class MappingConfigurationHolder {

    @Resource
    private CloudConfigurationHolder cloudConfigurationHolder;

    @Resource
    private ManagedPlugin pluginContext;

    private ObjectMapper yamlObjectMapper = YamlParserUtil.createYamlObjectMapper();

    @Getter
    private MappingConfiguration mappingConfiguration;

    private ProviderMappingConfiguration providerMappingConfiguration;

    @PostConstruct
    public void postConstruct() throws Exception {
        mappingConfiguration = yamlObjectMapper.readValue(
                new BufferedInputStream(Files.newInputStream(pluginContext.getPluginPath().resolve("mapping/mapping.yaml"))), MappingConfiguration.class);
        cloudConfigurationHolder.registerListener(new ICloudConfigurationChangeListener() {
            @Override
            public void onConfigurationChange(CloudConfiguration newConfiguration) throws Exception {
                loadProviderMapping(newConfiguration);
            }
        });
    }

    public ProviderMappingConfiguration getProviderMappingConfiguration() {
        if (providerMappingConfiguration == null) {
            loadProviderMapping(cloudConfigurationHolder.getConfiguration());
        }
        return providerMappingConfiguration;
    }

    private void loadProviderMapping(CloudConfiguration newConfiguration) {
        try {
            this.providerMappingConfiguration = this.yamlObjectMapper.readValue(
                    new BufferedInputStream(new FileInputStream(
                            this.pluginContext.getPluginPath().resolve("provider").resolve(newConfiguration.getProvider()).resolve("mapping.yaml").toFile())),
                    ProviderMappingConfiguration.class);
        } catch (IOException e) {
            throw new BadConfigurationException("Bad configuration, unable to parse provider mapping configuration", e);
        }
    }
}
