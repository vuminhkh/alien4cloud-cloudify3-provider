package alien4cloud.paas.cloudify3.service;

import java.util.Map;

import javax.annotation.Resource;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import alien4cloud.model.components.FunctionPropertyValue;
import alien4cloud.model.components.IOperationParameter;
import alien4cloud.model.components.Interface;
import alien4cloud.model.components.Operation;
import alien4cloud.model.components.ScalarPropertyValue;
import alien4cloud.paas.cloudify3.configuration.MappingConfigurationHolder;
import alien4cloud.paas.cloudify3.dao.ExecutionDAO;
import alien4cloud.paas.cloudify3.dao.WorkflowResultDAO;
import alien4cloud.paas.cloudify3.model.Execution;
import alien4cloud.paas.cloudify3.model.Workflow;
import alien4cloud.paas.cloudify3.service.model.CloudifyDeployment;
import alien4cloud.paas.cloudify3.util.CloudifyDeploymentUtil;
import alien4cloud.paas.exception.OperationExecutionException;
import alien4cloud.paas.model.NodeOperationExecRequest;
import alien4cloud.paas.model.PaaSNodeTemplate;

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
    private ExecutionDAO executionDAO;

    @Resource
    private WorkflowResultDAO workflowResultDAO;

    @Resource
    private MappingConfigurationHolder mappingConfigurationHolder;

    public ListenableFuture<Map<String, String>> executeOperation(CloudifyDeployment deployment, NodeOperationExecRequest nodeOperationExecRequest) {
        CloudifyDeploymentUtil util = new CloudifyDeploymentUtil(mappingConfigurationHolder.getMappingConfiguration(),
                mappingConfigurationHolder.getProviderMappingConfiguration(), deployment);
        Map<String, Object> workflowParameters = Maps.newHashMap();
        if (MapUtils.isEmpty(deployment.getAllNodes()) || !deployment.getAllNodes().containsKey(nodeOperationExecRequest.getNodeTemplateName())) {
            throw new OperationExecutionException("Node " + nodeOperationExecRequest.getNodeTemplateName() + " do not exist in the deployment");
        }
        PaaSNodeTemplate node = deployment.getAllNodes().get(nodeOperationExecRequest.getNodeTemplateName());
        Map<String, Interface> nodeInterfaces = util.getNodeInterfaces(node);
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
            Map<String, IOperationParameter> inputParameters = operation.getInputParameters();
            for (Map.Entry<String, IOperationParameter> inputParameterEntry : inputParameters.entrySet()) {
                if (inputParameterEntry.getValue() instanceof FunctionPropertyValue || inputParameterEntry.getValue() instanceof ScalarPropertyValue) {
                    String parameterName = inputParameterEntry.getKey();
                    String parameterValue = util.formatNodeOperationInput(node, inputParameterEntry.getValue());
                    inputParameterValues.put(parameterName, parameterValue);
                }
            }
            if (MapUtils.isNotEmpty(nodeOperationExecRequest.getParameters())) {
                inputParameterValues.putAll(nodeOperationExecRequest.getParameters());
            }
        }
        ListenableFuture<Execution> operationExecutionFuture = waitForExecutionFinish(executionDAO.asyncStart(deployment.getDeploymentId(),
                Workflow.EXECUTE_OPERATION, workflowParameters, true, false));
        AsyncFunction<Execution, Map<String, String>> getOperationResultFunction = new AsyncFunction<Execution, Map<String, String>>() {
            @Override
            public ListenableFuture<Map<String, String>> apply(Execution input) throws Exception {
                return workflowResultDAO.getOperationResult();
            }
        };
        return Futures.transform(operationExecutionFuture, getOperationResultFunction);
    }
}
