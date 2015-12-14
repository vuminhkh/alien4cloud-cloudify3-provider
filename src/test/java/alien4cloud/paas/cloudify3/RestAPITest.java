package alien4cloud.paas.cloudify3;

import org.springframework.web.client.AsyncRestTemplate;

import alien4cloud.paas.cloudify3.configuration.CloudConfiguration;
import alien4cloud.paas.cloudify3.configuration.CloudConfigurationHolder;
import alien4cloud.paas.cloudify3.model.Execution;
import alien4cloud.paas.cloudify3.restclient.ExecutionClient;
import alien4cloud.paas.cloudify3.restclient.VersionClient;
import alien4cloud.paas.exception.PluginConfigurationException;

public class RestAPITest {

    public static void main(String[] args) throws PluginConfigurationException {
        AsyncRestTemplate restTemplate = new PluginContextConfiguration().asyncRestTemplate();
        ExecutionClient executionClient = new ExecutionClient();
        VersionClient versionClient = new VersionClient();
        versionClient.setRestTemplate(restTemplate);
        executionClient.setRestTemplate(restTemplate);
        CloudConfiguration defaultConfiguration = new CloudifyOrchestratorFactory().getDefaultConfiguration();
        defaultConfiguration.setUrl("http://129.185.67.108/");
        CloudConfigurationHolder cloudConfigurationHolder = new CloudConfigurationHolder();
        cloudConfigurationHolder.setVersionClient(versionClient);
        versionClient.setConfigurationHolder(cloudConfigurationHolder);
        executionClient.setConfigurationHolder(cloudConfigurationHolder);
        cloudConfigurationHolder.setConfigurationAndNotifyListeners(defaultConfiguration);
        Execution[] executions = executionClient.list("artifact-test-cfy3-Environment", true);
        for (Execution execution : executions) {
            System.out.println(execution.getWorkflowId() + " = " + execution.getIsSystemWorkflow());
        }
    }
}
