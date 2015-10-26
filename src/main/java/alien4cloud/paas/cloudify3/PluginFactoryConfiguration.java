package alien4cloud.paas.cloudify3;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PluginFactoryConfiguration {

    @Bean(name = "cloudify-orchestrator")
    public CloudifyOrchestratorFactory cloudifyOrchestratorFactory() {
        return new CloudifyOrchestratorFactory();
    }
}
