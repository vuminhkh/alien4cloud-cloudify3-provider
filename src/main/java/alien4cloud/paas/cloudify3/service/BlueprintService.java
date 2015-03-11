package alien4cloud.paas.cloudify3.service;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import alien4cloud.model.components.DeploymentArtifact;
import alien4cloud.model.components.IArtifact;
import alien4cloud.model.components.ImplementationArtifact;
import alien4cloud.model.components.IndexedArtifactToscaElement;
import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.model.components.IndexedRelationshipType;
import alien4cloud.model.components.Interface;
import alien4cloud.model.components.Operation;
import alien4cloud.paas.IPaaSTemplate;
import alien4cloud.paas.cloudify3.configuration.CloudConfigurationHolder;
import alien4cloud.paas.cloudify3.configuration.MappingConfigurationHolder;
import alien4cloud.paas.cloudify3.service.model.CloudifyDeployment;
import alien4cloud.paas.cloudify3.util.BlueprintGenerationUtil;
import alien4cloud.paas.cloudify3.util.VelocityUtil;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;

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
    private MappingConfigurationHolder mappingConfigurationHolder;

    @Resource
    private ClasspathResourceLoaderService resourceLoaderService;

    private Path recipeDirectoryPath;

    /**
     * Generate blueprint from an alien deployment request
     *
     * @param alienDeployment the alien deployment's configuration
     * @return the generated blueprint
     */
    public Path generateBlueprint(CloudifyDeployment alienDeployment) throws IOException {
        // Where the whole blueprint will be generated
        Path generatedBlueprintDirectoryPath = resolveBlueprintPath(alienDeployment.getRecipeId());
        // Where the main blueprint file will be generated
        Path generatedBlueprintFilePath = generatedBlueprintDirectoryPath.resolve("blueprint.yaml");
        BlueprintGenerationUtil util = new BlueprintGenerationUtil(mappingConfigurationHolder.getMappingConfiguration(),
                mappingConfigurationHolder.getProviderMappingConfiguration(), alienDeployment);
        // The velocity context will be filed up with information in order to be able to generate deployment
        Map<String, Object> context = Maps.newHashMap();
        context.put("cloud", cloudConfigurationHolder.getConfiguration());
        context.put("mapping", mappingConfigurationHolder.getMappingConfiguration());
        context.put("providerMapping", mappingConfigurationHolder.getProviderMappingConfiguration());
        context.put("util", util);
        context.put("deployment", alienDeployment);
        context.put("newline", "\n");
        context.put("provider_nodes_file",
                resourceLoaderService.loadResourceFromClasspath("velocity/" + cloudConfigurationHolder.getConfiguration().getProvider() + "-nodes.yaml.vm")
                        .getFileName().toString());
        context.put("provider_types_file",
                resourceLoaderService.loadResourceFromClasspath("velocity/" + cloudConfigurationHolder.getConfiguration().getProvider() + "-types.yaml.vm")
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
                    copyDeploymentArtifacts(generatedBlueprintDirectoryPath, nonNative, nonNativeType);
                    copyImplementationArtifacts(generatedBlueprintDirectoryPath, nonNative, nonNativeType);
                }
                List<PaaSRelationshipTemplate> relationships = nonNative.getRelationshipTemplates();
                for (PaaSRelationshipTemplate relationship : relationships) {
                    IndexedRelationshipType relationshipType = relationship.getIndexedToscaElement();
                    if (processedRelationshipTypes.add(relationshipType.getElementId())) {
                        copyDeploymentArtifacts(generatedBlueprintDirectoryPath, relationship, relationshipType);
                        copyImplementationArtifacts(generatedBlueprintDirectoryPath, relationship, relationshipType);
                    }
                }
            }
        }

        if (util.hasConfiguredVolume(alienDeployment.getVolumes())) {
            Path volumeScriptPath = generatedBlueprintDirectoryPath.resolve("cfy3_native/volume");
            Files.createDirectories(volumeScriptPath);
            Files.copy(resourceLoaderService.loadResourceFromClasspath("artifacts/volume/fdisk.sh"), volumeScriptPath.resolve("fdisk.sh"));
            Files.copy(resourceLoaderService.loadResourceFromClasspath("artifacts/volume/mkfs.sh"), volumeScriptPath.resolve("mkfs.sh"));
            Files.copy(resourceLoaderService.loadResourceFromClasspath("artifacts/volume/mount.sh"), volumeScriptPath.resolve("mount.sh"));
            Files.copy(resourceLoaderService.loadResourceFromClasspath("artifacts/volume/unmount.sh"), volumeScriptPath.resolve("unmount.sh"));
        }
        return generatedBlueprintFilePath;
    }

    private void copyArtifact(Path generatedBlueprintDirectoryPath, Path csarPath, IArtifact artifact, IndexedArtifactToscaElement nonNativeType)
            throws IOException {
        String artifactRelativePathName = artifact.getArtifactRef();
        FileSystem csarFS = FileSystems.newFileSystem(csarPath, null);
        Path artifactPath = csarFS.getPath(artifactRelativePathName);
        Path copiedArtifactDirectory = generatedBlueprintDirectoryPath.resolve(nonNativeType.getArchiveName()).resolve(nonNativeType.getElementId());
        Files.createDirectories(copiedArtifactDirectory);
        Path artifactCopiedPath = copiedArtifactDirectory.resolve(artifactRelativePathName);
        Files.createDirectories(artifactCopiedPath.getParent());
        Files.copy(artifactPath, artifactCopiedPath);
    }

    private void copyDeploymentArtifacts(Path generatedBlueprintDirectoryPath, IPaaSTemplate<?> nonNative, IndexedArtifactToscaElement nonNativeType)
            throws IOException {
        Map<String, DeploymentArtifact> artifacts = nonNativeType.getArtifacts();
        if (artifacts == null || artifacts.isEmpty()) {
            return;
        }
        for (Map.Entry<String, DeploymentArtifact> artifactEntry : artifacts.entrySet()) {
            DeploymentArtifact artifact = artifactEntry.getValue();
            copyArtifact(generatedBlueprintDirectoryPath, nonNative.getCsarPath(), artifact, nonNativeType);
        }
    }

    private void copyImplementationArtifacts(Path generatedBlueprintDirectoryPath, IPaaSTemplate<?> nonNative, IndexedArtifactToscaElement nonNativeType)
            throws IOException {
        Map<String, Interface> interfaces = nonNativeType.getInterfaces();
        if (interfaces == null || interfaces.isEmpty()) {
            return;
        }
        // Copy implementation artifacts
        for (Map.Entry<String, Interface> interfaceEntry : interfaces.entrySet()) {
            Map<String, Operation> operations = interfaceEntry.getValue().getOperations();
            for (Map.Entry<String, Operation> operationEntry : operations.entrySet()) {
                ImplementationArtifact artifact = operationEntry.getValue().getImplementationArtifact();
                if (artifact != null) {
                    copyArtifact(generatedBlueprintDirectoryPath, nonNative.getCsarPath(), artifact, nonNativeType);
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
