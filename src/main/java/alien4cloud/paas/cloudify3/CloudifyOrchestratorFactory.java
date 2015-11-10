package alien4cloud.paas.cloudify3;

import java.util.Collections;
import java.util.Map;

import javax.annotation.Resource;

import alien4cloud.paas.cloudify3.configuration.Imports;
import com.google.common.collect.Lists;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.google.common.collect.Maps;

import alien4cloud.model.components.PropertyDefinition;
import alien4cloud.model.orchestrators.ArtifactSupport;
import alien4cloud.model.orchestrators.locations.LocationSupport;
import alien4cloud.orchestrators.plugin.IOrchestratorPluginFactory;
import alien4cloud.paas.IPaaSProvider;
import alien4cloud.paas.cloudify3.configuration.CloudConfiguration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CloudifyOrchestratorFactory implements IOrchestratorPluginFactory<CloudifyOrchestrator, CloudConfiguration> {

    private static final String CFY_VERSION = "3.2.1";

    private static final String CFY_SCRIPT_VERSION = "1.2.1";

    @Resource
    private ApplicationContext factoryContext;

    private Map<IPaaSProvider, AnnotationConfigApplicationContext> contextMap = Collections
            .synchronizedMap(Maps.<IPaaSProvider, AnnotationConfigApplicationContext> newIdentityHashMap());

    @Override
    public Class<CloudConfiguration> getConfigurationType() {
        return CloudConfiguration.class;
    }

    @Override
    public CloudConfiguration getDefaultConfiguration() {
        CloudConfiguration cloudConfiguration = new CloudConfiguration();
        Imports imports = new Imports();
        imports.setAws(Lists.newArrayList("http://www.getcloudify.org/spec/cloudify/" + CFY_VERSION + "/types.yaml", "http://www.getcloudify.org/spec/aws-plugin/" + CFY_SCRIPT_VERSION + "/plugin.yaml"));
        imports.setOpenstack(Lists.newArrayList("http://www.getcloudify.org/spec/cloudify/" + CFY_VERSION + "/types.yaml", "http://www.getcloudify.org/spec/openstack-plugin/" + CFY_SCRIPT_VERSION + "/plugin.yaml"));
        cloudConfiguration.setImports(imports);
        return cloudConfiguration;
    }

    @Override
    public CloudifyOrchestrator newInstance() {
        /**
         * Hierarchy of context (parent on the left) :
         * Alien Context --> Factory Context --> Real Cloudify 3 context
         * Each cloud will create a different cloudify 3 context
         */
        AnnotationConfigApplicationContext pluginContext = new AnnotationConfigApplicationContext();
        pluginContext.setParent(factoryContext);
        pluginContext.setClassLoader(factoryContext.getClassLoader());
        pluginContext.register(PluginContextConfiguration.class);
        pluginContext.refresh();
        log.info("Created new Cloudify 3 context {} for factory {}", pluginContext.getId(), factoryContext.getId());
        CloudifyOrchestrator provider = pluginContext.getBean(CloudifyOrchestrator.class);
        contextMap.put(provider, pluginContext);
        return provider;
    }

    public Map<String, PropertyDefinition> getDeploymentPropertyDefinitions() {
        return null;
    }

    @Override
    public void destroy(CloudifyOrchestrator instance) {
        AnnotationConfigApplicationContext context = contextMap.remove(instance);
        if (context == null) {
            log.warn("Context not found for paaS provider instance {}", instance);
        } else {
            log.info("Dispose context created for paaS provider {}", instance);
            context.close();
        }
    }

    @Override
    public LocationSupport getLocationSupport() {
        return new LocationSupport(false, new String[] { "openstack" });
    }

    @Override
    public ArtifactSupport getArtifactSupport() {
        return new ArtifactSupport(new String[] { "tosca.artifacts.Implementation.Bash" });
    }
}