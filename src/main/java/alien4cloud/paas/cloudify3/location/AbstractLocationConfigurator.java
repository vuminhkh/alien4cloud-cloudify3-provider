package alien4cloud.paas.cloudify3.location;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import alien4cloud.deployment.matching.services.nodes.MatchingConfigurations;
import alien4cloud.deployment.matching.services.nodes.MatchingConfigurationsParser;
import alien4cloud.model.deployment.matching.MatchingConfiguration;
import alien4cloud.orchestrators.plugin.model.PluginArchive;
import alien4cloud.paas.cloudify3.service.PluginArchiveService;
import alien4cloud.plugin.model.ManagedPlugin;
import alien4cloud.tosca.parser.ParsingException;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public abstract class AbstractLocationConfigurator implements ITypeAwareLocationConfigurator {
    @Inject
    private ManagedPlugin selfContext;
    @Inject
    private PluginArchiveService archiveService;
    @Inject
    private MatchingConfigurationsParser matchingConfigurationsParser;

    protected List<PluginArchive> archives;

    public void parseLocationArchives(String... paths) {
        this.archives = Lists.newArrayList();
        for (String path : paths) {
            this.archives.add(archiveService.parsePluginArchives(path));
        }
    }

    public List<String> getAllResourcesTypes() {
        List<String> resourcesTypes = Lists.newArrayList();
        for (PluginArchive pluginArchive : this.archives) {
            for (String nodeType : pluginArchive.getArchive().getNodeTypes().keySet()) {
                resourcesTypes.add(nodeType);
            }
        }
        return resourcesTypes;
    }

    public Map<String, MatchingConfiguration> getMatchingConfigurations(String matchingConfigRelativePath) {
        Path matchingConfigPath = selfContext.getPluginPath().resolve(matchingConfigRelativePath);
        MatchingConfigurations matchingConfigurations = null;
        try {
            matchingConfigurations = matchingConfigurationsParser.parseFile(matchingConfigPath).getResult();
        } catch (ParsingException e) {
            return Maps.newHashMap();
        }
        return matchingConfigurations.getMatchingConfigurations();
    }

    @Override
    public List<PluginArchive> pluginArchives() {
        return this.archives;
    }

}
