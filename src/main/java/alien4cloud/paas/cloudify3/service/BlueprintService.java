package alien4cloud.paas.cloudify3.service;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Resource;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import alien4cloud.component.repository.ArtifactLocalRepository;
import alien4cloud.component.repository.ArtifactRepositoryConstants;
import alien4cloud.component.repository.CsarFileRepository;
import alien4cloud.component.repository.exception.CSARVersionNotFoundException;
import alien4cloud.model.components.DeploymentArtifact;
import alien4cloud.model.components.IArtifact;
import alien4cloud.model.components.ImplementationArtifact;
import alien4cloud.model.components.IndexedArtifactToscaElement;
import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.model.components.IndexedRelationshipType;
import alien4cloud.model.components.Interface;
import alien4cloud.model.components.Operation;
import alien4cloud.model.topology.AbstractTemplate;
import alien4cloud.paas.cloudify3.configuration.CloudConfigurationHolder;
import alien4cloud.paas.cloudify3.configuration.MappingConfigurationHolder;
import alien4cloud.paas.cloudify3.service.model.CloudifyDeployment;
import alien4cloud.paas.cloudify3.util.CloudifyDeploymentUtil;
import alien4cloud.paas.cloudify3.util.VelocityUtil;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;
import alien4cloud.utils.FileUtil;

import com.google.common.base.Charsets;
import com.google.common.collect.Maps;

/**
 * Handle blueprint generation from alien model
 *
 * @author Minh Khang VU
 */
@Component("cloudify-blueprint-service")
public class BlueprintService {

    public static final String SHELL_SCRIPT_ARTIFACT = "tosca.artifacts.ShellScript";

    public static final Pattern SCRIPT_ECHO_PATTERN = Pattern.compile("^\\s*echo ((?:([\"'])[^\"]*\\2)|(?:[\\p{Alnum}\\p{Blank}]*))$");

    @Resource
    private CloudConfigurationHolder cloudConfigurationHolder;

    @Resource
    private MappingConfigurationHolder mappingConfigurationHolder;

    @Resource
    private ClasspathResourceLoaderService resourceLoaderService;

    @Resource
    private CsarFileRepository repository;

    @Resource
    private ArtifactLocalRepository artifactRepository;

    private Path recipeDirectoryPath;

    /**
     * Generate blueprint from an alien deployment request
     *
     * @param alienDeployment the alien deployment's configuration
     * @return the generated blueprint
     */
    public Path generateBlueprint(CloudifyDeployment alienDeployment) throws IOException, CSARVersionNotFoundException {
        // Where the whole blueprint will be generated
        Path generatedBlueprintDirectoryPath = resolveBlueprintPath(alienDeployment.getDeploymentId());
        // Where the main blueprint file will be generated
        Path generatedBlueprintFilePath = generatedBlueprintDirectoryPath.resolve("blueprint.yaml");
        CloudifyDeploymentUtil util = new CloudifyDeploymentUtil(mappingConfigurationHolder.getMappingConfiguration(),
                mappingConfigurationHolder.getProviderMappingConfiguration(), alienDeployment, generatedBlueprintDirectoryPath);
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
        // Copy artifacts
        List<PaaSNodeTemplate> nonNatives = alienDeployment.getNonNatives();
        if (nonNatives != null) {
            for (PaaSNodeTemplate nonNative : nonNatives) {
                IndexedNodeType nonNativeType = nonNative.getIndexedToscaElement();
                // Don't process a node more than once
                copyDeploymentArtifacts(generatedBlueprintDirectoryPath, nonNative.getId(), nonNative.getNodeTemplate(), nonNativeType);
                copyImplementationArtifacts(generatedBlueprintDirectoryPath, nonNative.getId(), nonNativeType);
                List<PaaSRelationshipTemplate> relationships = nonNative.getRelationshipTemplates();
                for (PaaSRelationshipTemplate relationship : relationships) {
                    if (relationship.getSource().equals(nonNative.getId())) {
                        IndexedRelationshipType relationshipType = relationship.getIndexedToscaElement();
                        copyDeploymentArtifacts(generatedBlueprintDirectoryPath, nonNative.getId(), relationship.getRelationshipTemplate(), relationshipType);
                        copyImplementationArtifacts(generatedBlueprintDirectoryPath, nonNative.getId(), relationshipType);
                    }
                }
            }
        }
        Path nativeArtifactDirectory = generatedBlueprintDirectoryPath.resolve(mappingConfigurationHolder.getMappingConfiguration()
                .getNativeArtifactDirectoryName());
        if (Files.exists(nativeArtifactDirectory)) {
            throw new IOException(nativeArtifactDirectory.getFileName() + " is a reserved name, please choose another name for your archive");
        }
        if (util.hasConfiguredVolume(alienDeployment.getVolumes())) {
            Path volumeScriptPath = nativeArtifactDirectory.resolve("volume");
            Files.createDirectories(volumeScriptPath);
            Files.copy(resourceLoaderService.loadResourceFromClasspath("artifacts/volume/fdisk.sh"), volumeScriptPath.resolve("fdisk.sh"));
            Files.copy(resourceLoaderService.loadResourceFromClasspath("artifacts/volume/mkfs.sh"), volumeScriptPath.resolve("mkfs.sh"));
            Files.copy(resourceLoaderService.loadResourceFromClasspath("artifacts/volume/mount.sh"), volumeScriptPath.resolve("mount.sh"));
            Files.copy(resourceLoaderService.loadResourceFromClasspath("artifacts/volume/unmount.sh"), volumeScriptPath.resolve("unmount.sh"));
        }
        if (util.mapHasEntries(alienDeployment.getAllDeploymentArtifacts())) {
            Path deploymentArtifactsScriptPath = nativeArtifactDirectory.resolve("deployment_artifacts");
            Files.createDirectories(deploymentArtifactsScriptPath);
            Files.copy(resourceLoaderService.loadResourceFromClasspath("artifacts/deployment_artifacts/download_artifacts.py"),
                    deploymentArtifactsScriptPath.resolve("download_artifacts.py"));
        }
        // Generate the blueprint at the end
        VelocityUtil.generate(resourceLoaderService.loadResourceFromClasspath("velocity/blueprint.yaml.vm"), generatedBlueprintFilePath, context);
        return generatedBlueprintFilePath;
    }

    private void copyShellScriptImplementationArtifact(Path artifactPath, Path artifactCopiedPath) throws IOException {
        BufferedReader artifactReader = null;
        OutputStream artifactOutput = null;
        try {
            artifactReader = Files.newBufferedReader(artifactPath, Charsets.UTF_8);
            artifactOutput = new BufferedOutputStream(Files.newOutputStream(artifactCopiedPath));
            String line;
            while ((line = artifactReader.readLine()) != null) {
                Matcher matcher = SCRIPT_ECHO_PATTERN.matcher(line);
                if (matcher.matches()) {
                    String logContent = matcher.group(1);
                    if (!logContent.startsWith("\"") && !logContent.startsWith("'")) {
                        logContent = "\"" + logContent + "\"";
                    }
                    artifactOutput.write(("ctx logger info " + logContent + "\n").getBytes(Charsets.UTF_8));
                } else {
                    artifactOutput.write((line + "\n").getBytes(Charsets.UTF_8));
                }
            }
        } finally {
            if (artifactReader != null) {
                try {
                    artifactReader.close();
                } catch (IOException e) {
                }
            }
            if (artifactOutput != null) {
                try {
                    artifactOutput.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private void copyArtifact(Path generatedBlueprintDirectoryPath, Path csarPath, String pathToNode, IArtifact artifact, IArtifact originalArtifact)
            throws IOException {
        Path artifactPath;
        Path artifactCopiedPath;
        if (originalArtifact != null && ArtifactRepositoryConstants.ALIEN_ARTIFACT_REPOSITORY.equals(artifact.getArtifactRepository())) {
            // If the internal repository is used
            // Overridden artifact
            Path artifactCopiedDirectory = generatedBlueprintDirectoryPath
                    .resolve(mappingConfigurationHolder.getMappingConfiguration().getTopologyArtifactDirectoryName()).resolve(pathToNode)
                    .resolve(originalArtifact.getArchiveName());
            artifactPath = artifactRepository.resolveFile(artifact.getArtifactRef());
            artifactCopiedPath = artifactCopiedDirectory.resolve(originalArtifact.getArtifactRef());
        } else {
            Path artifactCopiedDirectory = generatedBlueprintDirectoryPath.resolve(artifact.getArchiveName());
            FileSystem csarFS = FileSystems.newFileSystem(csarPath, null);
            String artifactRelativePathName = artifact.getArtifactRef();
            artifactPath = csarFS.getPath(artifactRelativePathName);
            artifactCopiedPath = artifactCopiedDirectory.resolve(artifactRelativePathName);
        }
        if (Files.isRegularFile(artifactCopiedPath)) {
            // already copied do nothing
            return;
        }
        Files.createDirectories(artifactCopiedPath.getParent());
        // For shell script we try to match all echo command and replace them with ctx logger info (cloudify 3 API for logging)
        // Only enabled for debugging purpose
        if (cloudConfigurationHolder.getConfiguration().getDebugScript() && SHELL_SCRIPT_ARTIFACT.equals(artifact.getArtifactType())
                && artifact instanceof ImplementationArtifact) {
            copyShellScriptImplementationArtifact(artifactPath, artifactCopiedPath);
        } else {
            if (Files.isDirectory(artifactPath)) {
                FileUtil.copy(artifactPath, artifactCopiedPath, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.copy(artifactPath, artifactCopiedPath);
            }
        }
    }

    private void copyDeploymentArtifacts(Path generatedBlueprintDirectoryPath, String pathToNode, AbstractTemplate node, IndexedArtifactToscaElement type)
            throws IOException, CSARVersionNotFoundException {
        Map<String, DeploymentArtifact> artifacts = type.getArtifacts();
        if (artifacts == null || artifacts.isEmpty()) {
            return;
        }
        for (Map.Entry<String, DeploymentArtifact> artifactEntry : artifacts.entrySet()) {
            DeploymentArtifact artifact = artifactEntry.getValue();
            if (artifact != null) {
                Path csarPath = repository.getCSAR(artifact.getArchiveName(), artifact.getArchiveVersion());
                Map<String, DeploymentArtifact> topologyArtifacts = node.getArtifacts();
                if (topologyArtifacts != null && topologyArtifacts.containsKey(artifactEntry.getKey())) {
                    copyArtifact(generatedBlueprintDirectoryPath, csarPath, pathToNode, topologyArtifacts.get(artifactEntry.getKey()), artifact);
                } else {
                    copyArtifact(generatedBlueprintDirectoryPath, csarPath, pathToNode, artifact, null);
                }
            }
        }
    }

    private void copyImplementationArtifacts(Path generatedBlueprintDirectoryPath, String pathToNode, IndexedArtifactToscaElement nonNativeType)
            throws IOException, CSARVersionNotFoundException {
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
                    Path csarPath = repository.getCSAR(artifact.getArchiveName(), artifact.getArchiveVersion());
                    copyArtifact(generatedBlueprintDirectoryPath, csarPath, pathToNode, artifact, null);
                }
            }
        }
    }

    public Path resolveBlueprintPath(String deploymentId) {
        return recipeDirectoryPath.resolve(deploymentId);
    }

    @Required
    @Value("${directories.alien}/cloudify3")
    public void setRecipeDirectoryPath(final String path) {
        recipeDirectoryPath = Paths.get(path).toAbsolutePath();
    }
}
