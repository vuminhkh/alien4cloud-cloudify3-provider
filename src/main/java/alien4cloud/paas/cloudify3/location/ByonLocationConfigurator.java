package alien4cloud.paas.cloudify3.location;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.springframework.stereotype.Component;

import com.google.common.collect.Sets;

import alien4cloud.model.deployment.matching.MatchingConfiguration;
import alien4cloud.model.orchestrators.locations.LocationResourceTemplate;
import alien4cloud.orchestrators.plugin.ILocationResourceAccessor;

@Component
public class ByonLocationConfigurator extends AbstractLocationConfigurator {
    @Inject
    private ResourceGenerator resourceGenerator;

    public static final String COMPUTE_TYPE = "alien.nodes.byon.Compute";
    public static final String IMAGE_TYPE = "alien.nodes.byon.Image";
    public static final String FLAVOR_TYPE = "alien.nodes.byon.Flavor";

    @Override
    protected String[] getLocationArchivePaths() {
        return new String[] { "provider/byon/configuration" };
    }

    @Override
    public List<String> getResourcesTypes() {
        return getAllResourcesTypes();
    }

    @Override
    public List<LocationResourceTemplate> instances(ILocationResourceAccessor resourceAccessor) {
        return null;
    }

    @Override
    public Set<String> getManagedLocationTypes() {
        return Sets.newHashSet("byon");
    }

    @Override
    public Map<String, MatchingConfiguration> getMatchingConfigurations() {
        return getMatchingConfigurations("provider/openstack/matching/config.yml");
    }
}
