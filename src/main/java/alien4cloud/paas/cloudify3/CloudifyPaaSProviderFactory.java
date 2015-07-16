package alien4cloud.paas.cloudify3;

import java.util.Collections;
import java.util.Map;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import alien4cloud.model.components.PropertyDefinition;
import alien4cloud.paas.IConfigurablePaaSProvider;
import alien4cloud.paas.IConfigurablePaaSProviderFactory;
import alien4cloud.paas.IDeploymentParameterizablePaaSProviderFactory;
import alien4cloud.paas.IPaaSProvider;
import alien4cloud.paas.cloudify3.configuration.CloudConfiguration;
import alien4cloud.tosca.normative.ToscaType;

import com.google.common.collect.Maps;

@Slf4j
public class CloudifyPaaSProviderFactory implements IConfigurablePaaSProviderFactory<CloudConfiguration>,
        IDeploymentParameterizablePaaSProviderFactory<IConfigurablePaaSProvider<CloudConfiguration>> {

    @Resource
    private ApplicationContext factoryContext;

    public static final String DELETABLE_BLOCKSTORAGE = "deletable_blockstorage";

    private static final Map<String, PropertyDefinition> deploymentPropertyMap = Maps.newHashMap();

    static {
        PropertyDefinition deletableBlockStorage = new PropertyDefinition();
        deletableBlockStorage.setType(ToscaType.BOOLEAN.toString());
        deletableBlockStorage.setRequired(false);
        deletableBlockStorage.setDescription("Indicates that all deployment related blockstorage are deletable.");
        deletableBlockStorage.setDefault("false");
        deploymentPropertyMap.put(DELETABLE_BLOCKSTORAGE, deletableBlockStorage);
    }

    private Map<IPaaSProvider, AnnotationConfigApplicationContext> contextMap = Collections.synchronizedMap(Maps
            .<IPaaSProvider, AnnotationConfigApplicationContext> newIdentityHashMap());

    @Override
    public Class<CloudConfiguration> getConfigurationType() {
        return CloudConfiguration.class;
    }

    @Override
    public CloudConfiguration getDefaultConfiguration() {
        return new CloudConfiguration();
    }

    @Override
    public IConfigurablePaaSProvider<CloudConfiguration> newInstance() {
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
        IConfigurablePaaSProvider<CloudConfiguration> provider = pluginContext.getBean(CloudifyPaaSProvider.class);
        contextMap.put(provider, pluginContext);
        return provider;
    }

    @Override
    public Map<String, PropertyDefinition> getDeploymentPropertyDefinitions() {
        return deploymentPropertyMap;
    }

    @Override
    public void destroy(IConfigurablePaaSProvider<CloudConfiguration> instance) {
        AnnotationConfigApplicationContext context = contextMap.remove(instance);
        if (context == null) {
            log.warn("Context not found for paaS provider instance {}", instance);
        } else {
            log.info("Dispose context created for paaS provider {}", instance);
            context.close();
        }
    }
}