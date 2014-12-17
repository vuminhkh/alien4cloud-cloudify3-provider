package alien4cloud.paas.cloudify3;

import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import alien4cloud.model.application.DeploymentSetup;
import alien4cloud.model.cloud.CloudResourceMatcherConfig;
import alien4cloud.paas.AbstractPaaSProvider;
import alien4cloud.paas.IConfigurablePaaSProvider;
import alien4cloud.paas.IManualResourceMatcherPaaSProvider;
import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.cloudify3.configuration.CloudConfiguration;
import alien4cloud.paas.cloudify3.configuration.CloudConfigurationHolder;
import alien4cloud.paas.cloudify3.dao.VersionDAO;
import alien4cloud.paas.cloudify3.error.OperationNotSupportedException;
import alien4cloud.paas.cloudify3.service.ComputeTemplateMatcherService;
import alien4cloud.paas.cloudify3.service.DeploymentService;
import alien4cloud.paas.cloudify3.service.EventService;
import alien4cloud.paas.cloudify3.service.model.AlienDeployment;
import alien4cloud.paas.cloudify3.service.model.MatchedPaaSComputeTemplate;
import alien4cloud.paas.exception.OperationExecutionException;
import alien4cloud.paas.exception.PluginConfigurationException;
import alien4cloud.paas.model.AbstractMonitorEvent;
import alien4cloud.paas.model.NodeOperationExecRequest;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.tosca.container.model.topology.Topology;
import alien4cloud.tosca.model.PropertyDefinition;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * The cloudify 3 PaaS Provider implementation
 *
 * @author Minh Khang VU
 */
@Slf4j
@Component("cloudify-paas-provider-bean")
public class CloudifyPaaSProvider extends AbstractPaaSProvider implements IConfigurablePaaSProvider<CloudConfiguration>, IManualResourceMatcherPaaSProvider {

    @Resource(name = "cloudify-deployment-service")
    private DeploymentService deploymentService;

    @Resource(name = "cloudify-configuration-holder")
    private CloudConfigurationHolder cloudConfigurationHolder;

    @Resource(name = "cloudify-event-service")
    private EventService eventService;

    @Resource(name = "cloudify-compute-template-matcher-service")
    private ComputeTemplateMatcherService computeTemplateMatcherService;

    @Resource
    private VersionDAO versionDAO;

    /**
     * ********************************************************************************************************************
     * *****************************************************Deployment*****************************************************
     * ********************************************************************************************************************
     */

    @Override
    protected void doDeploy(String deploymentName, String deploymentId, Topology topology, List<PaaSNodeTemplate> computes,
            Map<String, PaaSNodeTemplate> nodes, DeploymentSetup deploymentSetup) {
        List<MatchedPaaSComputeTemplate> matchedComputes = computeTemplateMatcherService.match(computes, deploymentSetup);
        AlienDeployment deployment = new AlienDeployment(deploymentId, deploymentName, topology, matchedComputes, nodes);
        deploymentService.deploy(deployment);
    }

    @Override
    public void undeploy(String deploymentId) {
        deploymentService.undeploy(deploymentId);
    }

    /**
     * ********************************************************************************************************************
     * *****************************************************Configurations*************************************************
     * ********************************************************************************************************************
     */

    @Override
    public CloudConfiguration getDefaultConfiguration() {
        return cloudConfigurationHolder.getConfiguration();
    }

    @Override
    public void setConfiguration(CloudConfiguration newConfiguration) throws PluginConfigurationException {
        if (newConfiguration == null) {
            throw new PluginConfigurationException("Configuration is null");
        }
        if (newConfiguration.getUrl() == null) {
            throw new PluginConfigurationException("Url is null");
        }
        CloudConfiguration oldConfiguration = cloudConfigurationHolder.getConfiguration();
        cloudConfigurationHolder.setConfiguration(newConfiguration);
        try {
            versionDAO.read();
        } catch (RestClientException e) {
            cloudConfigurationHolder.setConfiguration(oldConfiguration);
            throw new PluginConfigurationException("Url is not correct");
        }
    }

    /**
     * ********************************************************************************************************************
     * *****************************************************Events*********************************************************
     * ********************************************************************************************************************
     */

    @Override
    public void getEventsSince(Date lastTimestamp, int batchSize, final IPaaSCallback<AbstractMonitorEvent[]> eventsCallback) {
        ListenableFuture<AbstractMonitorEvent[]> events = eventService.getEventsSince(lastTimestamp, batchSize);
        Futures.addCallback(events, new FutureCallback<AbstractMonitorEvent[]>() {
            @Override
            public void onSuccess(AbstractMonitorEvent[] result) {
                eventsCallback.onData(result);
            }

            @Override
            public void onFailure(Throwable t) {
                eventsCallback.onFailure(t);
            }
        });
    }

    /**
     * ********************************************************************************************************************
     * *****************************************************Matcher********************************************************
     * ********************************************************************************************************************
     */

    @Override
    public void updateMatcherConfig(CloudResourceMatcherConfig cloudResourceMatcherConfig) {
        computeTemplateMatcherService.configure(cloudResourceMatcherConfig);
    }

    /**
     * ********************************************************************************************************************
     * *****************************************************Not implemented operation**************************************
     * ********************************************************************************************************************
     */

    @Override
    public void scale(String deploymentId, String nodeTemplateId, int instances) {
        throw new OperationNotSupportedException("scale is not supported yet");
    }

    @Override
    public Map<String, String> executeOperation(String deploymentId, NodeOperationExecRequest nodeOperationExecRequest) throws OperationExecutionException {
        throw new OperationNotSupportedException("executeOperation is not supported yet");
    }

    @Override
    public Map<String, PropertyDefinition> getDeploymentPropertyMap() {
        return null;
    }
}
