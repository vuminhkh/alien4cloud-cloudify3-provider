package alien4cloud.paas.cloudify3;

import java.util.Date;
import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang.NotImplementedException;
import org.springframework.stereotype.Component;

import alien4cloud.model.cloud.CloudResourceMatcherConfig;
import alien4cloud.model.cloud.CloudResourceType;
import alien4cloud.paas.IConfigurablePaaSProvider;
import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.ITemplateManagedPaaSProvider;
import alien4cloud.paas.cloudify3.configuration.CloudConfiguration;
import alien4cloud.paas.cloudify3.configuration.CloudConfigurationHolder;
import alien4cloud.paas.cloudify3.service.CloudifyDeploymentBuilderService;
import alien4cloud.paas.cloudify3.service.ComputeTemplateMatcherService;
import alien4cloud.paas.cloudify3.service.CustomWorkflowService;
import alien4cloud.paas.cloudify3.service.DeploymentService;
import alien4cloud.paas.cloudify3.service.EventService;
import alien4cloud.paas.cloudify3.service.NetworkMatcherService;
import alien4cloud.paas.cloudify3.service.StatusService;
import alien4cloud.paas.cloudify3.service.StorageTemplateMatcherService;
import alien4cloud.paas.cloudify3.service.model.CloudifyDeployment;
import alien4cloud.paas.cloudify3.util.FutureUtil;
import alien4cloud.paas.exception.OperationExecutionException;
import alien4cloud.paas.exception.PluginConfigurationException;
import alien4cloud.paas.model.AbstractMonitorEvent;
import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.InstanceInformation;
import alien4cloud.paas.model.NodeOperationExecRequest;
import alien4cloud.paas.model.PaaSComputeTemplate;
import alien4cloud.paas.model.PaaSDeploymentContext;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;

import com.google.common.collect.Sets;
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
public class CloudifyPaaSProvider implements IConfigurablePaaSProvider<CloudConfiguration>, ITemplateManagedPaaSProvider {

    @Resource(name = "cloudify-deployment-service")
    private DeploymentService deploymentService;

    @Resource(name = "cloudify-custom-workflow-service")
    private CustomWorkflowService customWorkflowService;

    @Resource(name = "cloudify-configuration-holder")
    private CloudConfigurationHolder cloudConfigurationHolder;

    @Resource(name = "cloudify-event-service")
    private EventService eventService;

    @Resource(name = "cloudify-compute-template-matcher-service")
    private ComputeTemplateMatcherService computeTemplateMatcherService;

    @Resource(name = "cloudify-network-matcher-service")
    private NetworkMatcherService networkMatcherService;

    @Resource(name = "cloudify-storage-matcher-service")
    private StorageTemplateMatcherService storageMatcherService;

    @Resource(name = "cloudify-deployment-builder-service")
    private CloudifyDeploymentBuilderService cloudifyDeploymentBuilderService;

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
    public void getInstancesInformation(PaaSTopologyDeploymentContext deploymentContext, IPaaSCallback<Map<String, Map<String, InstanceInformation>>> callback) {
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
     * *****************************************************Matcher********************************************************
     * ********************************************************************************************************************
     */

    @Override
    public String[] getAvailableResourceIds(CloudResourceType resourceType) {
        PaaSComputeTemplate[] templates = cloudConfigurationHolder.getConfiguration().getTemplateConfigurations();
        if (templates == null || templates.length == 0) {
            return null;
        }
        Set<String> result = Sets.newHashSet();
        switch (resourceType) {
        case IMAGE:
            for (PaaSComputeTemplate template : templates) {
                result.add(template.getImageId());
            }
            break;
        case FLAVOR:
            for (PaaSComputeTemplate template : templates) {
                result.add(template.getFlavorId());
            }
            break;
        default:
            return null;
        }
        return result.toArray(new String[result.size()]);
    }

    @Override
    public String[] getAvailableResourceIds(CloudResourceType resourceType, String imageId) {
        return getAvailableResourceIds(resourceType);
    }

    @Override
    public void updateMatcherConfig(CloudResourceMatcherConfig cloudResourceMatcherConfig) {
        computeTemplateMatcherService.configure(cloudResourceMatcherConfig.getImageMapping(), cloudResourceMatcherConfig.getFlavorMapping(),
                cloudResourceMatcherConfig.getAvailabilityZoneMapping());
        networkMatcherService.configure(cloudResourceMatcherConfig.getNetworkMapping());
        storageMatcherService.configure(cloudResourceMatcherConfig.getStorageMapping());
        cloudifyDeploymentBuilderService.setCloudResourceMatcherConfig(cloudResourceMatcherConfig);
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

    /**
     * For byon only
     * 
     * @return the list of compute templates
     */
    @Override
    public PaaSComputeTemplate[] getAvailablePaaSComputeTemplates() {
        return cloudConfigurationHolder.getConfiguration().getTemplateConfigurations();
    }
}
