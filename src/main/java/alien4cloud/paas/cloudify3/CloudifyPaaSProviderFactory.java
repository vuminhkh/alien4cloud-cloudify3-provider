package alien4cloud.paas.cloudify3;

import java.util.Collections;
import java.util.Map;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import alien4cloud.paas.IConfigurablePaaSProvider;
import alien4cloud.paas.IConfigurablePaaSProviderFactory;
import alien4cloud.paas.IPaaSProvider;
import alien4cloud.paas.cloudify3.configuration.CloudConfiguration;

import com.google.common.collect.Maps;

@Slf4j
public class CloudifyPaaSProviderFactory implements IConfigurablePaaSProviderFactory<CloudConfiguration> {

    @Resource
    private ApplicationContext factoryContext;

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