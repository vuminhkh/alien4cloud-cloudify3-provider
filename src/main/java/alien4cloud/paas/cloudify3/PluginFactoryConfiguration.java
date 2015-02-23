package alien4cloud.paas.cloudify3;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PluginFactoryConfiguration {

    @Bean(name = "cloudify-paas-provider")
    public CloudifyPaaSProviderFactory cloudifyPaaSProviderFactory() {
        return new CloudifyPaaSProviderFactory();
    }
}
