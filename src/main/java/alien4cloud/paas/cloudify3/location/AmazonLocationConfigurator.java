package alien4cloud.paas.cloudify3.location;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import alien4cloud.deployment.matching.services.nodes.MatchingConfigurations;
import alien4cloud.deployment.matching.services.nodes.MatchingConfigurationsParser;
import alien4cloud.model.deployment.matching.MatchingConfiguration;
import alien4cloud.model.orchestrators.locations.LocationResourceTemplate;
import alien4cloud.orchestrators.plugin.ILocationResourceAccessor;
import alien4cloud.orchestrators.plugin.model.PluginArchive;
import alien4cloud.paas.cloudify3.error.BadConfigurationException;
import alien4cloud.plugin.model.ManagedPlugin;
import alien4cloud.tosca.ArchiveParser;
import alien4cloud.tosca.model.ArchiveRoot;
import alien4cloud.tosca.parser.ParsingException;
import alien4cloud.tosca.parser.ParsingResult;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

@Slf4j
@Component
public class AmazonLocationConfigurator implements ITypeAwareLocationConfigurator {
    @Inject
    private ArchiveParser archiveParser;
    @Inject
    private ManagedPlugin selfContext;
    @Inject
    private ResourceGenerator resourceGenerator;
    @Inject
    private MatchingConfigurationsParser matchingConfigurationsParser;

    private List<PluginArchive> archives;

    public static final String COMPUTE_TYPE = "alien.cloudify.aws.nodes.Compute";
    public static final String IMAGE_TYPE = "alien.cloudify.aws.nodes.Image";
    public static final String FLAVOR_TYPE = "alien.cloudify.aws.nodes.InstanceType";
    private static final String IMAGE_ID_PROP = "image_id";
    private static final String FLAVOR_ID_PROP = "instance_type";

    public static final Set<String> FILTERS = Sets.newHashSet();

    @PostConstruct
    public void postConstruct() {
        this.archives = Lists.newArrayList();
        Path archivePath = this.selfContext.getPluginPath().resolve("provider/amazon/configuration");
        // Parse the archives
        try {
            ParsingResult<ArchiveRoot> result = this.archiveParser.parseDir(archivePath);
            PluginArchive pluginArchive = new PluginArchive(result.getResult(), archivePath);
            this.archives.add(pluginArchive);
        } catch (ParsingException e) {
            log.error("Failed to parse archive, plugin won't work", e);
            throw new BadConfigurationException("Failed to parse archive, plugin won't work", e);
        }
    }

    @Override
    public List<PluginArchive> pluginArchives() {
        return this.archives;
    }

    @Override
    public List<String> getResourcesTypes() {
        List<String> resourcesTypes = Lists.newArrayList();
        for (PluginArchive pluginArchive : this.archives) {
            for (String nodeType : pluginArchive.getArchive().getNodeTypes().keySet()) {
                if (!FILTERS.contains(nodeType)) {
                    resourcesTypes.add(nodeType);
                }
            }
        }
        return resourcesTypes;
    }

    @Override
    public List<LocationResourceTemplate> instances(ILocationResourceAccessor resourceAccessor) {
        return resourceGenerator.generateComputes(COMPUTE_TYPE, IMAGE_TYPE, FLAVOR_TYPE, IMAGE_ID_PROP, FLAVOR_ID_PROP, resourceAccessor);
    }

    @Override
    public Set<String> getManagedLocationTypes() {
        return Sets.newHashSet("amazon");
    }

    @Override
    public Map<String, MatchingConfiguration> getMatchingConfigurations() {
        Path matchingConfigPath = selfContext.getPluginPath().resolve("provider/amazon/matching/config.yml");
        MatchingConfigurations matchingConfigurations = null;
        try {
            matchingConfigurations = matchingConfigurationsParser.parseFile(matchingConfigPath).getResult();
        } catch (ParsingException e) {
            return Maps.newHashMap();
        }
        return matchingConfigurations.getMatchingConfigurations();
    }
}
