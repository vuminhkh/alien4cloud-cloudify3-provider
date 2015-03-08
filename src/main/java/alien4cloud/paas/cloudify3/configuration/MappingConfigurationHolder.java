package alien4cloud.paas.cloudify3.configuration;

import java.io.IOException;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import lombok.Getter;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import alien4cloud.paas.cloudify3.error.BadConfigurationException;
import alien4cloud.paas.cloudify3.service.ClasspathResourceLoaderService;
import alien4cloud.utils.YamlParserUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component("cloudify-mapping-configuration-holder")
public class MappingConfigurationHolder {

    @Resource
    private CloudConfigurationHolder cloudConfigurationHolder;

    @Resource
    private ClasspathResourceLoaderService resourceLoaderService;

    private ObjectMapper yamlObjectMapper = YamlParserUtil.createYamlObjectMapper();

    @Getter
    private MappingConfiguration mappingConfiguration;

    private ProviderMappingConfiguration providerMappingConfiguration;

    @PostConstruct
    public void postConstruct() throws Exception {
        mappingConfiguration = yamlObjectMapper.readValue(
                new ClassPathResource("mapping/mapping.yaml", resourceLoaderService.getApplicationContextClassLoader()).getInputStream(),
                MappingConfiguration.class);
        cloudConfigurationHolder.registerListener(new ICloudConfigurationChangeListener() {
            @Override
            public void onConfigurationChange(CloudConfiguration newConfiguration) throws Exception {
                loadProviderMapping();
            }
        });
    }

    public ProviderMappingConfiguration getProviderMappingConfiguration() {
        if (providerMappingConfiguration == null) {
            loadProviderMapping();
        }
        return providerMappingConfiguration;
    }

    private void loadProviderMapping() {
        try {
            providerMappingConfiguration = yamlObjectMapper.readValue(new ClassPathResource("mapping/"
                    + cloudConfigurationHolder.getConfiguration().getProvider() + ".yaml", resourceLoaderService.getApplicationContextClassLoader())
                    .getInputStream(), ProviderMappingConfiguration.class);
        } catch (IOException e) {
            throw new BadConfigurationException("Bad configuration, unable to parse provider mapping configuration", e);
        }
    }
}
