package alien4cloud.paas.cloudify3;

import java.util.Date;
import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import alien4cloud.model.cloud.CloudResourceMatcherConfig;
import alien4cloud.model.cloud.CloudResourceType;
import alien4cloud.model.components.PropertyDefinition;
import alien4cloud.model.topology.Topology;
import alien4cloud.paas.IConfigurablePaaSProvider;
import alien4cloud.paas.IManualResourceMatcherPaaSProvider;
import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.IPaaSProvider;
import alien4cloud.paas.cloudify3.configuration.CloudConfiguration;
import alien4cloud.paas.cloudify3.configuration.CloudConfigurationHolder;
import alien4cloud.paas.cloudify3.dao.VersionDAO;
import alien4cloud.paas.cloudify3.error.OperationNotSupportedException;
import alien4cloud.paas.cloudify3.model.Version;
import alien4cloud.paas.cloudify3.service.CloudifyDeploymentBuilderService;
import alien4cloud.paas.cloudify3.service.ComputeTemplateMatcherService;
import alien4cloud.paas.cloudify3.service.DeploymentService;
import alien4cloud.paas.cloudify3.service.EventService;
import alien4cloud.paas.cloudify3.service.NetworkMatcherService;
import alien4cloud.paas.cloudify3.service.StatusService;
import alien4cloud.paas.cloudify3.service.model.CloudifyDeployment;
import alien4cloud.paas.cloudify3.util.FutureUtil;
import alien4cloud.paas.exception.OperationExecutionException;
import alien4cloud.paas.exception.PluginConfigurationException;
import alien4cloud.paas.model.AbstractMonitorEvent;
import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.InstanceInformation;
import alien4cloud.paas.model.NodeOperationExecRequest;
import alien4cloud.paas.model.PaaSDeploymentContext;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;

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
public class CloudifyPaaSProvider implements IConfigurablePaaSProvider<CloudConfiguration>, IManualResourceMatcherPaaSProvider, IPaaSProvider {

    @Resource(name = "cloudify-deployment-service")
    private DeploymentService deploymentService;

    @Resource(name = "cloudify-configuration-holder")
    private CloudConfigurationHolder cloudConfigurationHolder;

    @Resource(name = "cloudify-event-service")
    private EventService eventService;

    @Resource(name = "cloudify-compute-template-matcher-service")
    private ComputeTemplateMatcherService computeTemplateMatcherService;

    @Resource(name = "cloudify-network-matcher-service")
    private NetworkMatcherService networkMatcherService;

    @Resource(name = "cloudify-deployment-builder-service")
    private CloudifyDeploymentBuilderService cloudifyDeploymentBuilderService;

    @Resource
    private VersionDAO versionDAO;

    @Resource
    private StatusService statusService;

    /**
     * ********************************************************************************************************************
     * *****************************************************Deployment*****************************************************
     * ********************************************************************************************************************
     */

    @Override
    public void deploy(PaaSTopologyDeploymentContext deploymentContext, final IPaaSCallback callback) {
        CloudifyDeployment deployment = cloudifyDeploymentBuilderService.buildCloudifyDeployment(deploymentContext);
        FutureUtil.associateFutureToPaaSCallback(deploymentService.deploy(deployment), callback);
    }

    @Override
    public void undeploy(PaaSDeploymentContext deploymentContext, IPaaSCallback callback) {
        FutureUtil.associateFutureToPaaSCallback(deploymentService.undeploy(deploymentContext), callback);
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
            Version version = versionDAO.read();
            statusService.init();
            log.info("Configure PaaS provider for Cloudify version " + version.getVersion());
        } catch (Exception e) {
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
    public void getStatus(PaaSDeploymentContext deploymentContext, IPaaSCallback<DeploymentStatus> callback) {
        statusService.getStatus(deploymentContext.getDeploymentId(), callback);
    }

    @Override
    public void getInstancesInformation(PaaSDeploymentContext deploymentContext, Topology topology,
            IPaaSCallback<Map<String, Map<String, InstanceInformation>>> callback) {
        statusService.getInstancesInformation(deploymentContext.getDeploymentId(), topology, callback);
    }

    @Override
    public void getEventsSince(Date lastTimestamp, int batchSize, final IPaaSCallback<AbstractMonitorEvent[]> eventsCallback) {
        ListenableFuture<AbstractMonitorEvent[]> events = eventService.getEventsSince(lastTimestamp, batchSize);
        Futures.addCallback(events, new FutureCallback<AbstractMonitorEvent[]>() {
            @Override
            public void onSuccess(AbstractMonitorEvent[] result) {
                eventsCallback.onSuccess(result);
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
    public String[] getAvailableResourceIds(CloudResourceType resourceType) {
        switch (resourceType) {
        case COMPUTE:
            Set<String> paaSResourceIds = cloudConfigurationHolder.getConfiguration().getComputeTemplates().keySet();
            return paaSResourceIds.toArray(new String[paaSResourceIds.size()]);
        default:
            throw new OperationNotSupportedException("getAvailableResourceIds " + resourceType + " is not yet managed");
        }
    }

    @Override
    public void updateMatcherConfig(CloudResourceMatcherConfig cloudResourceMatcherConfig) {
        computeTemplateMatcherService.configure(cloudResourceMatcherConfig.getComputeTemplateMapping());
        networkMatcherService.configure(cloudResourceMatcherConfig.getNetworkMapping());
    }

    /**
     * ********************************************************************************************************************
     * *****************************************************Not implemented operation**************************************
     * ********************************************************************************************************************
     */

    @Override
    public void scale(PaaSDeploymentContext deploymentContext, String nodeTemplateId, int instances, IPaaSCallback<?> callback) {
        throw new OperationNotSupportedException("scale is not supported yet");
    }

    @Override
    public void executeOperation(PaaSDeploymentContext deploymentContext, NodeOperationExecRequest nodeOperationExecRequest,
            IPaaSCallback<Map<String, String>> callback) throws OperationExecutionException {
        throw new OperationNotSupportedException("executeOperation is not supported yet");
    }

    @Override
    public Map<String, PropertyDefinition> getDeploymentPropertyMap() {
        return null;
    }
}
