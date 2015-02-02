package alien4cloud.paas.cloudify3.service;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import alien4cloud.model.components.ImplementationArtifact;
import alien4cloud.model.components.IndexedArtifactToscaElement;
import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.model.components.IndexedRelationshipType;
import alien4cloud.model.components.Interface;
import alien4cloud.model.components.Operation;
import alien4cloud.paas.IPaaSTemplate;
import alien4cloud.paas.cloudify3.configuration.CloudConfiguration;
import alien4cloud.paas.cloudify3.configuration.CloudConfigurationHolder;
import alien4cloud.paas.cloudify3.configuration.ICloudConfigurationChangeListener;
import alien4cloud.paas.cloudify3.service.model.CloudifyDeployment;
import alien4cloud.paas.cloudify3.service.model.MappingConfiguration;
import alien4cloud.paas.cloudify3.service.model.ProviderMappingConfiguration;
import alien4cloud.paas.cloudify3.util.BlueprintGenerationUtil;
import alien4cloud.paas.cloudify3.util.VelocityUtil;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;
import alien4cloud.utils.YamlParserUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Handle blueprint generation from alien model
 *
 * @author Minh Khang VU
 */
@Component("cloudify-blueprint-service")
@Slf4j
public class BlueprintService {

    @Resource
    private CloudConfigurationHolder cloudConfigurationHolder;

    @Resource
    private ClasspathResourceLoaderService resourceLoaderService;

    private Path recipeDirectoryPath;

    private MappingConfiguration mapping;

    private ProviderMappingConfiguration providerMapping;

    private ObjectMapper yamlObjectMapper = YamlParserUtil.createYamlObjectMapper();

    @PostConstruct
    public void postConstruct() throws Exception {
        mapping = yamlObjectMapper.readValue(
                new ClassPathResource("mapping/mapping.yaml", resourceLoaderService.getApplicationContextClassLoader()).getInputStream(),
                MappingConfiguration.class);
        cloudConfigurationHolder.registerListener(new ICloudConfigurationChangeListener() {
            @Override
            public void onConfigurationChange(CloudConfiguration newConfiguration) throws Exception {
                loadProviderMapping();
            }
        });
    }

    private void loadProviderMapping() throws Exception {
        providerMapping = yamlObjectMapper.readValue(new ClassPathResource("mapping/" + cloudConfigurationHolder.getConfiguration().getProvider() + ".yaml",
                resourceLoaderService.getApplicationContextClassLoader()).getInputStream(), ProviderMappingConfiguration.class);
    }

    /**
     * Generate blueprint from an alien deployment request
     *
     * @param alienDeployment the alien deployment's configuration
     * @return the generated blueprint
     */
    @SneakyThrows
    public Path generateBlueprint(CloudifyDeployment alienDeployment) {
        if (providerMapping == null) {
            loadProviderMapping();
        }
        // Where the whole blueprint will be generated
        Path generatedBlueprintDirectoryPath = resolveBlueprintPath(alienDeployment.getRecipeId());
        // Where the main blueprint file will be generated
        Path generatedBlueprintFilePath = generatedBlueprintDirectoryPath.resolve("blueprint.yaml");
        BlueprintGenerationUtil util = new BlueprintGenerationUtil(mapping);
        // The velocity context will be filed up with information in order to be able to generate deployment
        Map<String, Object> context = Maps.newHashMap();
        context.put("cloud", cloudConfigurationHolder.getConfiguration());
        context.put("mapping", mapping);
        context.put("providerMapping", providerMapping);
        context.put("util", util);
        context.put("deployment", alienDeployment);
        context.put("newline", "\n");
        context.put("provider_nodes_file",
                resourceLoaderService.loadResourceFromClasspath("velocity/" + cloudConfigurationHolder.getConfiguration().getProvider() + "-nodes.yaml.vm")
                        .getFileName().toString());
        // Generate the blueprint
        VelocityUtil.generate(resourceLoaderService.loadResourceFromClasspath("velocity/blueprint.yaml.vm"), generatedBlueprintFilePath, context);
        // Copy artifacts
        List<PaaSNodeTemplate> nonNatives = alienDeployment.getNonNatives();
        Set<String> processedNodeTypes = Sets.newHashSet();
        Set<String> processedRelationshipTypes = Sets.newHashSet();
        if (nonNatives != null) {
            for (PaaSNodeTemplate nonNative : nonNatives) {
                IndexedNodeType nonNativeType = nonNative.getIndexedToscaElement();
                if (processedNodeTypes.add(nonNativeType.getElementId())) {
                    // Don't process a type more than once
                    copyImplementationArtifacts(generatedBlueprintDirectoryPath, nonNative, nonNativeType);
                }
                List<PaaSRelationshipTemplate> relationships = nonNative.getRelationshipTemplates();
                for (PaaSRelationshipTemplate relationship : relationships) {
                    IndexedRelationshipType relationshipType = relationship.getIndexedToscaElement();
                    if (processedRelationshipTypes.add(relationshipType.getElementId())) {
                        copyImplementationArtifacts(generatedBlueprintDirectoryPath, relationship, relationshipType);
                    }
                }
            }
        }
        return generatedBlueprintFilePath;
    }

    @SneakyThrows
    private void copyImplementationArtifacts(Path generatedBlueprintDirectoryPath, IPaaSTemplate<?> nonNative, IndexedArtifactToscaElement nonNativeType) {
        Map<String, Interface> interfaces = nonNativeType.getInterfaces();
        // Copy implementation artifacts
        for (Map.Entry<String, Interface> interfaceEntry : interfaces.entrySet()) {
            Map<String, Operation> operations = interfaceEntry.getValue().getOperations();
            for (Map.Entry<String, Operation> operationEntry : operations.entrySet()) {
                ImplementationArtifact artifact = operationEntry.getValue().getImplementationArtifact();
                if (artifact != null) {
                    String artifactRelativePathName = artifact.getArtifactRef();
                    FileSystem csarFS = FileSystems.newFileSystem(nonNative.getCsarPath(), null);
                    Path artifactPath = csarFS.getPath(artifactRelativePathName);
                    Path copiedArtifactDirectory = generatedBlueprintDirectoryPath.resolve(nonNativeType.getArchiveName())
                            .resolve(nonNativeType.getElementId());
                    Files.createDirectories(copiedArtifactDirectory);
                    Path artifactCopiedPath = copiedArtifactDirectory.resolve(artifactRelativePathName);
                    Files.createDirectories(artifactCopiedPath.getParent());
                    Files.copy(artifactPath, artifactCopiedPath);
                }
            }
        }
    }

    /**
     * Find out where the blueprint of a deployment might/should be generated to
     *
     * @param recipeId the recipe's id
     * @return the path to the generated blueprint
     */
    public Path resolveBlueprintPath(String recipeId) {
        return recipeDirectoryPath.resolve(recipeId);
    }

    @Required
    @Value("${directories.alien}/cloudify3")
    public void setRecipeDirectoryPath(final String path) {
        log.debug("Setting temporary path to {}", path);
        recipeDirectoryPath = Paths.get(path).toAbsolutePath();
    }
}
