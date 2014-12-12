package alien4cloud.paas.cloudify3.service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Resource;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFuture;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;

import alien4cloud.paas.cloudify3.configuration.CloudConfigurationHolder;
import alien4cloud.paas.cloudify3.dao.BlueprintDAO;
import alien4cloud.paas.cloudify3.model.Blueprint;
import alien4cloud.paas.cloudify3.service.model.AlienDeployment;
import alien4cloud.paas.cloudify3.util.FutureUtil;
import alien4cloud.paas.cloudify3.util.VelocityUtil;
import alien4cloud.utils.YamlParserUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

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
    private BlueprintDAO blueprintDAO;

    @Resource
    private ClasspathResourceLoaderService resourceLoaderService;

    private Path recipeDirectoryPath;

    private ObjectMapper yamlObjectMapper = YamlParserUtil.createYamlObjectMapper();

    /**
     * Generate blueprint from an alien deployment request
     *
     * @param alienDeployment the alien deployment's configuration
     * @return the generated blueprint
     */
    @SneakyThrows
    public Path generateBlueprint(AlienDeployment alienDeployment) {
        // Where the whole blueprint will be generated
        Path generatedBlueprintDirectoryPath = recipeDirectoryPath.resolve(alienDeployment.getDeploymentId());
        // Where the main blueprint file will be generated
        Path generatedBlueprintFilePath = generatedBlueprintDirectoryPath.resolve("blueprint.yaml");
        // The velocity context will be filed up with information in order to be able to generate deployment
        Map<String, Object> context = Maps.newHashMap();
        context.put("cloud", cloudConfigurationHolder.getConfiguration());
        JavaType mapStringObjectType = yamlObjectMapper.getTypeFactory().constructParametricType(HashMap.class, String.class, Object.class);
        Map<String, Object> mapping = yamlObjectMapper.readValue(new ClassPathResource("mapping/openstack.yaml").getInputStream(), mapStringObjectType);
        context.put("mapping", mapping);
        context.put("deployment", alienDeployment);
        // Generate the blueprint
        VelocityUtil.generate(resourceLoaderService.loadResourceFromClasspath("velocity/blueprint.yaml.vm"), generatedBlueprintFilePath, context);
        return generatedBlueprintFilePath;
    }

    /**
     * Upload the blueprint to cloudify manager
     *
     * @param blueprintName     the blueprint name
     * @param blueprintFilePath the blueprint path
     * @return the future created blueprint
     */
    public ListenableFuture<Blueprint> uploadBlueprint(String blueprintName, Path blueprintFilePath) {
        // Create the blueprint on cloudify manager
        return FutureUtil.unwrap(blueprintDAO.asyncCreate(blueprintName, blueprintFilePath.toString()));
    }

    @Required
    @Value("${directories.alien}/cloudify3")
    public void setRecipeDirectoryPath(final String path) {
        log.debug("Setting temporary path to {}", path);
        recipeDirectoryPath = Paths.get(path).toAbsolutePath();
    }
}
