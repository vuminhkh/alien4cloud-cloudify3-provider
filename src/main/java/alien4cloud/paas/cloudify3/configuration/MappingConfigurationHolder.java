package alien4cloud.paas.cloudify3.configuration;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import lombok.Getter;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import alien4cloud.paas.cloudify3.error.BadConfigurationException;
import alien4cloud.utils.FileUtil;
import alien4cloud.utils.YamlParserUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component("cloudify-mapping-configuration-holder")
public class MappingConfigurationHolder {

    @Resource
    private CloudConfigurationHolder cloudConfigurationHolder;

    @Resource
    private ApplicationContext applicationContext;

    private Path pluginProviderResourcesPath;

    private ObjectMapper yamlObjectMapper = YamlParserUtil.createYamlObjectMapper();

    @Getter
    private MappingConfiguration mappingConfiguration;

    private ProviderMappingConfiguration providerMappingConfiguration;

    private Path pluginRecipeTemplatePath;

    @PostConstruct
    public void postConstruct() throws Exception {
        URL providerResourcesURL = applicationContext.getClassLoader().getResource("provider/");
        Path providerResourcesPath = Paths.get(providerResourcesURL.toURI());
        FileUtil.copy(providerResourcesPath, pluginProviderResourcesPath, StandardCopyOption.REPLACE_EXISTING);
        mappingConfiguration = yamlObjectMapper.readValue(new ClassPathResource("mapping/mapping.yaml", applicationContext.getClassLoader()).getInputStream(),
                MappingConfiguration.class);
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
            Path providerNodesTemplate = pluginProviderResourcesPath.resolve(newConfiguration.getProvider()).resolve("nodes.yaml.vm");
            Path providerTypesTemplate = pluginProviderResourcesPath.resolve(newConfiguration.getProvider()).resolve("types.yaml.vm");
            FileUtil.copy(providerNodesTemplate, this.pluginRecipeTemplatePath.resolve("nodes.yaml.vm"), StandardCopyOption.REPLACE_EXISTING);
            FileUtil.copy(providerTypesTemplate, this.pluginRecipeTemplatePath.resolve("types.yaml.vm"), StandardCopyOption.REPLACE_EXISTING);
            providerMappingConfiguration = yamlObjectMapper.readValue(
                    new BufferedInputStream(new FileInputStream(pluginProviderResourcesPath.resolve(cloudConfigurationHolder.getConfiguration().getProvider())
                            .resolve("mapping.yaml").toFile())), ProviderMappingConfiguration.class);
        } catch (IOException e) {
            throw new BadConfigurationException("Bad configuration, unable to parse provider mapping configuration", e);
        }
    }

    @Required
    @Value("${directories.alien}/cloudify3")
    public void setCloudifyPath(final String path) throws IOException {
        Path cloudifyResourcesPath = Paths.get(path).toAbsolutePath().resolve("resources");
        this.pluginProviderResourcesPath = cloudifyResourcesPath.resolve("provider");
        this.pluginRecipeTemplatePath = cloudifyResourcesPath.resolve("recipe").resolve("velocity");
    }
}
