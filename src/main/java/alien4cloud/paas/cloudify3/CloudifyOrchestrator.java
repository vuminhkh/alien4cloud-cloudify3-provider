package alien4cloud.paas.cloudify3;

import java.util.Collection;
import java.util.Date;
import java.util.Map;

import javax.annotation.Resource;

import alien4cloud.model.topology.LocationPlacementPolicy;
import alien4cloud.model.topology.NodeGroup;
import alien4cloud.paas.cloudify3.configuration.MappingConfigurationHolder;
import alien4cloud.paas.cloudify3.error.SingleLocationRequiredException;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang.NotImplementedException;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import alien4cloud.orchestrators.plugin.ILocationConfiguratorPlugin;
import alien4cloud.orchestrators.plugin.IOrchestratorPlugin;
import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.cloudify3.configuration.CloudConfiguration;
import alien4cloud.paas.cloudify3.configuration.CloudConfigurationHolder;
import alien4cloud.paas.cloudify3.location.ITypeAwareLocationConfigurator;
import alien4cloud.paas.cloudify3.service.CloudifyDeploymentBuilderService;
import alien4cloud.paas.cloudify3.service.CustomWorkflowService;
import alien4cloud.paas.cloudify3.service.DeploymentService;
import alien4cloud.paas.cloudify3.service.EventService;
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
public class CloudifyOrchestrator implements IOrchestratorPlugin<CloudConfiguration> {

    @Resource(name = "cloudify-deployment-service")
    private DeploymentService deploymentService;

    @Resource(name = "cloudify-custom-workflow-service")
    private CustomWorkflowService customWorkflowService;

    @Resource(name = "cloudify-configuration-holder")
    private CloudConfigurationHolder cloudConfigurationHolder;

    @Resource(name = "cloudify-event-service")
    private EventService eventService;

    @Resource(name = "cloudify-deployment-builder-service")
    private CloudifyDeploymentBuilderService cloudifyDeploymentBuilderService;

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private StatusService statusService;

    @Resource
    private MappingConfigurationHolder mappingConfigurationHolder;

    /**
     * ********************************************************************************************************************
     * *****************************************************Deployment*****************************************************
     * ********************************************************************************************************************
     */

    @Override
    public void deploy(PaaSTopologyDeploymentContext deploymentContext, final IPaaSCallback callback) {
        try {
            // Make sure the mapping is initialized for the deployment location
            mappingConfigurationHolder.loadProviderMapping(deploymentContext.getDeploymentTopology());

            CloudifyDeployment deployment = cloudifyDeploymentBuilderService.buildCloudifyDeployment(deploymentContext);
            FutureUtil.associateFutureToPaaSCallback(deploymentService.deploy(deployment), callback);
        } catch (SingleLocationRequiredException e) {
            callback.onFailure(e);
        }
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
    public void init(Map<String, PaaSTopologyDeploymentContext> activeDeployments) {
        if (activeDeployments == null) {
            return;
        } else {
            eventService.init(activeDeployments);
        }
    }

    @Override
    public void setConfiguration(CloudConfiguration newConfiguration) throws PluginConfigurationException {
        if (newConfiguration == null) {
            throw new PluginConfigurationException("Configuration is null");
        }
        if (newConfiguration.getUrl() == null) {
            throw new PluginConfigurationException("Url is null");
        }
        cloudConfigurationHolder.setConfiguration(newConfiguration);
    }

    /**
     * ********************************************************************************************************************
     * *****************************************************Events*********************************************************
     * ********************************************************************************************************************
     */

    @Override
    public void getStatus(PaaSDeploymentContext deploymentContext, IPaaSCallback<DeploymentStatus> callback) {
        statusService.getStatus(deploymentContext.getDeploymentPaaSId(), callback);
    }

    @Override
    public void getInstancesInformation(PaaSTopologyDeploymentContext deploymentContext,
            IPaaSCallback<Map<String, Map<String, InstanceInformation>>> callback) {
        statusService.getInstancesInformation(deploymentContext, callback);
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
     * *****************************************************Not implemented operation**************************************
     * ********************************************************************************************************************
     */

    @Override
    public void scale(PaaSDeploymentContext deploymentContext, String nodeTemplateId, int instances, IPaaSCallback callback) {
        FutureUtil.associateFutureToPaaSCallback(customWorkflowService.scale(deploymentContext.getDeploymentPaaSId(), nodeTemplateId, instances), callback);
    }

    @Override
    public void executeOperation(PaaSTopologyDeploymentContext deploymentContext, NodeOperationExecRequest nodeOperationExecRequest,
            IPaaSCallback<Map<String, String>> callback) throws OperationExecutionException {
        CloudifyDeployment deployment = cloudifyDeploymentBuilderService.buildCloudifyDeployment(deploymentContext);
        ListenableFuture<Map<String, String>> executionFutureResult = customWorkflowService.executeOperation(deployment, nodeOperationExecRequest);
        FutureUtil.associateFutureToPaaSCallback(executionFutureResult, callback);
    }

    @Override
    public void switchInstanceMaintenanceMode(PaaSDeploymentContext arg0, String arg1, String arg2, boolean arg3) {
        throw new NotImplementedException();
    }

    @Override
    public void switchMaintenanceMode(PaaSDeploymentContext arg0, boolean arg1) {
        throw new NotImplementedException();
    }

    @Override
    public ILocationConfiguratorPlugin getConfigurator(String locationType) {
        Collection<ITypeAwareLocationConfigurator> typeAwareLocationConfigurators = applicationContext.getBeansOfType(ITypeAwareLocationConfigurator.class)
                .values();
        for (ITypeAwareLocationConfigurator typeAwareLocationConfigurator : typeAwareLocationConfigurators) {
            if (typeAwareLocationConfigurator.getManagedLocationTypes().contains(locationType)) {
                return typeAwareLocationConfigurator;
            }
        }
        return null;
    }
}
