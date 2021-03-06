package alien4cloud.paas.cloudify3.service;

import java.util.Map;

import javax.annotation.Resource;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import alien4cloud.model.components.FunctionPropertyValue;
import alien4cloud.model.components.IValue;
import alien4cloud.model.components.Interface;
import alien4cloud.model.components.Operation;
import alien4cloud.model.components.ScalarPropertyValue;
import alien4cloud.paas.cloudify3.blueprint.BlueprintGenerationUtil;
import alien4cloud.paas.cloudify3.configuration.MappingConfigurationHolder;
import alien4cloud.paas.cloudify3.model.Deployment;
import alien4cloud.paas.cloudify3.model.NodeInstance;
import alien4cloud.paas.cloudify3.model.Workflow;
import alien4cloud.paas.cloudify3.restclient.ExecutionClient;
import alien4cloud.paas.cloudify3.restclient.NodeInstanceClient;
import alien4cloud.paas.cloudify3.service.model.CloudifyDeployment;
import alien4cloud.paas.exception.OperationExecutionException;
import alien4cloud.paas.function.FunctionEvaluator;
import alien4cloud.paas.model.NodeOperationExecRequest;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.tosca.normative.ToscaFunctionConstants;
import alien4cloud.utils.MapUtil;

import com.google.common.base.Function;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Handle custom workflow (non lifecycle workflow) which permit to modify the deployment at runtime
 *
 * @author Minh Khang VU
 */
@Component("cloudify-custom-workflow-service")
public class CustomWorkflowService extends RuntimeService {

    @Resource
    private ExecutionClient executionDAO;

    @Resource
    private NodeInstanceClient nodeInstanceDAO;

    @Resource
    private MappingConfigurationHolder mappingConfigurationHolder;

    @Resource
    private RuntimePropertiesService runtimePropertiesService;

    @Resource
    private BlueprintService blueprintService;

    @Resource
    private PropertyEvaluatorService propertyEvaluatorService;

    @Resource
    private OrchestratorDeploymentPropertiesService deploymentPropertiesService;

    private Map<String, Object> buildWorkflowParameters(CloudifyDeployment deployment, BlueprintGenerationUtil util,
            NodeOperationExecRequest nodeOperationExecRequest, PaaSNodeTemplate node, Operation operation) {
        Map<String, Object> workflowParameters = Maps.newHashMap();
        workflowParameters.put("operation", nodeOperationExecRequest.getInterfaceName() + "." + nodeOperationExecRequest.getOperationName());
        if (StringUtils.isNotBlank(nodeOperationExecRequest.getInstanceId())) {
            workflowParameters.put("node_instance_ids", new String[] { nodeOperationExecRequest.getInstanceId() });
        }
        if (StringUtils.isNotBlank(nodeOperationExecRequest.getNodeTemplateName())) {
            workflowParameters.put("node_ids", new String[] { nodeOperationExecRequest.getNodeTemplateName() });
        }
        if (MapUtils.isNotEmpty(operation.getInputParameters())) {
            Map<String, Object> inputs = Maps.newHashMap();
            Map<String, Object> process = Maps.newHashMap();
            Map<String, String> inputParameterValues = Maps.newHashMap();
            // operation_kwargs --> process --> env
            workflowParameters.put("operation_kwargs", inputs);
            inputs.put("process", process);
            process.put("env", inputParameterValues);
            Map<String, IValue> inputParameters = operation.getInputParameters();
            for (Map.Entry<String, IValue> inputParameterEntry : inputParameters.entrySet()) {
                String parameterName = inputParameterEntry.getKey();
                String parameterValue = null;
                if (inputParameterEntry.getValue() instanceof FunctionPropertyValue) {
                    FunctionPropertyValue function = (FunctionPropertyValue) inputParameterEntry.getValue();
                    if (ToscaFunctionConstants.GET_PROPERTY.equals(function.getFunction())) {
                        parameterValue = FunctionEvaluator.evaluateGetPropertyFunction(function, node, deployment.getAllNodes());
                    } else if (ToscaFunctionConstants.GET_ATTRIBUTE.equals(function.getFunction())) {
                        String resolvedKeyword = FunctionEvaluator.getPaaSTemplatesFromKeyword(node, function.getTemplateName(), deployment.getAllNodes())
                                .iterator().next().getId();
                        try {
                            Map<String, String> attributes = MapUtil.toString(runtimePropertiesService.evaluate(deployment.getDeploymentPaaSId(),
                                    resolvedKeyword, function.getElementNameToFetch()).get());
                            if (MapUtils.isEmpty(attributes)) {
                                throw new OperationExecutionException("Node " + node.getId() + " do not have any instance at this moment");
                            } else if (attributes.size() > 1) {
                                // TODO how to manage this use case
                                throw new OperationExecutionException("get_attribute for custom command is not supported for scaled node");
                            } else {
                                parameterValue = String.valueOf(attributes.values().iterator().next());
                            }
                        } catch (Exception e) {
                            throw new OperationExecutionException("Could not evaluate get_attribute function", e);
                        }
                    } else {
                        throw new OperationExecutionException("Only support get_property or get_attribute for custom command parameters");
                    }
                } else if (inputParameterEntry.getValue() instanceof ScalarPropertyValue) {
                    parameterValue = ((ScalarPropertyValue) inputParameterEntry.getValue()).getValue();
                }
                inputParameterValues.put(parameterName, parameterValue);
            }
            if (MapUtils.isNotEmpty(nodeOperationExecRequest.getParameters())) {
                inputParameterValues.putAll(nodeOperationExecRequest.getParameters());
            }
        }
        return workflowParameters;
    }

    public ListenableFuture<Map<String, String>> executeOperation(final CloudifyDeployment deployment, final NodeOperationExecRequest nodeOperationExecRequest) {
        BlueprintGenerationUtil util = new BlueprintGenerationUtil(mappingConfigurationHolder.getMappingConfiguration(), deployment,
                blueprintService.resolveBlueprintPath(deployment.getDeploymentPaaSId()), propertyEvaluatorService, deploymentPropertiesService);
        if (MapUtils.isEmpty(deployment.getAllNodes()) || !deployment.getAllNodes().containsKey(nodeOperationExecRequest.getNodeTemplateName())) {
            throw new OperationExecutionException("Node " + nodeOperationExecRequest.getNodeTemplateName() + " do not exist in the deployment");
        }
        PaaSNodeTemplate node = deployment.getAllNodes().get(nodeOperationExecRequest.getNodeTemplateName());
        Map<String, Interface> nodeInterfaces = util.getNonNative().getNodeInterfaces(node);
        if (MapUtils.isEmpty(nodeInterfaces) || !nodeInterfaces.containsKey(nodeOperationExecRequest.getInterfaceName())) {
            throw new OperationExecutionException("Interface " + nodeOperationExecRequest.getInterfaceName() + " do not exist for node "
                    + nodeOperationExecRequest.getNodeTemplateName());
        }
        Map<String, Operation> interfaceOperations = nodeInterfaces.get(nodeOperationExecRequest.getInterfaceName()).getOperations();
        if (MapUtils.isEmpty(interfaceOperations) || !interfaceOperations.containsKey(nodeOperationExecRequest.getOperationName())) {
            throw new OperationExecutionException("Operation " + nodeOperationExecRequest.getOperationName() + " do not exist for interface "
                    + nodeOperationExecRequest.getInterfaceName());
        }
        // Here we are safe, the node, the interface and the operation exists
        Operation operation = interfaceOperations.get(nodeOperationExecRequest.getOperationName());

        ListenableFuture<Deployment> operationExecutionFuture = waitForExecutionFinish(executionDAO.asyncStart(deployment.getDeploymentPaaSId(),
                Workflow.EXECUTE_OPERATION, buildWorkflowParameters(deployment, util, nodeOperationExecRequest, node, operation), true, false));
        AsyncFunction<Deployment, Map<String, String>> getOperationResultFunction = new AsyncFunction<Deployment, Map<String, String>>() {
            @Override
            public ListenableFuture<Map<String, String>> apply(Deployment input) throws Exception {
                ListenableFuture<NodeInstance[]> allInstances = nodeInstanceDAO.asyncList(deployment.getDeploymentPaaSId());
                Function<NodeInstance[], Map<String, String>> nodeInstanceToResultFunction = new Function<NodeInstance[], Map<String, String>>() {
                    @Override
                    public Map<String, String> apply(NodeInstance[] nodeInstances) {
                        Map<String, String> results = Maps.newHashMap();
                        for (NodeInstance nodeInstance : nodeInstances) {
                            if (StringUtils.isBlank(nodeOperationExecRequest.getInstanceId())) {
                                if (StringUtils.isNotBlank(nodeOperationExecRequest.getNodeTemplateName())
                                        && nodeOperationExecRequest.getNodeTemplateName().equals(nodeInstance.getNodeId())) {
                                    results.put(nodeInstance.getId(), fabricMessage(nodeOperationExecRequest, nodeInstance));
                                }
                            } else if (nodeOperationExecRequest.getInstanceId().equals(nodeInstance.getId())) {
                                results.put(nodeInstance.getId(), fabricMessage(nodeOperationExecRequest, nodeInstance));
                            }
                        }
                        return results;
                    }
                };
                return Futures.transform(allInstances, nodeInstanceToResultFunction);
            }
        };
        return Futures.transform(operationExecutionFuture, getOperationResultFunction);
    }

    private String fabricMessage(NodeOperationExecRequest request, NodeInstance nodeInstance) {
        return "Successfully executed " + request.getInterfaceName() + "." + request.getOperationName() + " on instance " + nodeInstance.getId() + " of node "
                + nodeInstance.getNodeId();
    }

    public ListenableFuture scale(String deploymentPaaSId, String nodeId, int delta) {
        Map<String, Object> scaleParameters = Maps.newHashMap();
        scaleParameters.put("node_id", nodeId);
        scaleParameters.put("delta", delta);
        scaleParameters.put("scale_compute", true);
        return waitForExecutionFinish(executionDAO.asyncStart(deploymentPaaSId, Workflow.SCALE, scaleParameters, true, false));
    }

    public ListenableFuture launchWorkflow(String deploymentPaaSId, String workflowName, Map<String, Object> workflowParameters) {
        return waitForExecutionFinish(executionDAO.asyncStart(deploymentPaaSId, Workflow.A4C_PREFIX + workflowName, workflowParameters, true, false));
    }

}
