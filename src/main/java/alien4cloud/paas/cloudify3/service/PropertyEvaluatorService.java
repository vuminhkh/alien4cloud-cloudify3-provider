package alien4cloud.paas.cloudify3.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import alien4cloud.exception.InvalidArgumentException;
import alien4cloud.model.components.ConcatPropertyValue;
import alien4cloud.model.components.FunctionPropertyValue;
import alien4cloud.model.components.IValue;
import alien4cloud.model.components.Interface;
import alien4cloud.model.components.Operation;
import alien4cloud.model.components.ScalarPropertyValue;
import alien4cloud.paas.IPaaSTemplate;
import alien4cloud.paas.function.FunctionEvaluator;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.tosca.normative.ToscaFunctionConstants;

import com.google.common.collect.Lists;

@Component("property-evaluator-service")
public class PropertyEvaluatorService {

    private void processAttributes(Map<String, IValue> attributes, IPaaSTemplate node, Map<String, PaaSNodeTemplate> allNodes) {
        if (attributes != null) {
            for (Map.Entry<String, IValue> attributeEntry : attributes.entrySet()) {
                attributeEntry.setValue(process(attributeEntry.getValue(), node, allNodes));
            }
        }
    }

    private void processInterfaces(Map<String, Interface> interfaces, IPaaSTemplate node, Map<String, PaaSNodeTemplate> allNodes) {
        if (interfaces != null) {
            for (Interface interfazz : interfaces.values()) {
                Map<String, Operation> operations = interfazz.getOperations();
                if (operations != null) {
                    for (Operation operation : operations.values()) {
                        Map<String, IValue> inputs = operation.getInputParameters();
                        if (inputs != null) {
                            for (Map.Entry<String, IValue> inputEntry : inputs.entrySet()) {
                                inputEntry.setValue(process(inputEntry.getValue(), node, allNodes));
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Process the deployment topology, every get_property will be replaced by its own static value retrieved from the topology
     * 
     * @param deploymentContext the deployment context
     */
    public void processGetPropertyFunction(PaaSTopologyDeploymentContext deploymentContext) {
        Map<String, PaaSNodeTemplate> allNodes = deploymentContext.getPaaSTopology().getAllNodes();
        for (PaaSNodeTemplate node : allNodes.values()) {
            processAttributes(node.getNodeTemplate().getAttributes(), node, allNodes);
            processInterfaces(node.getNodeTemplate().getInterfaces(), node, allNodes);
            List<PaaSRelationshipTemplate> relationships = node.getRelationshipTemplates();
            if (relationships != null) {
                for (PaaSRelationshipTemplate relationship : relationships) {
                    processAttributes(relationship.getRelationshipTemplate().getAttributes(), relationship, allNodes);
                    processInterfaces(relationship.getRelationshipTemplate().getInterfaces(), relationship, allNodes);
                }
            }
        }
    }

    /**
     * Process an IValue (it can be a scalar value, a get_property, get_attribute, get_operation_output or a concat) and replace all get_property occurrence
     * with its value and return the new IValue with get_property replaced by its value
     * 
     * @param value the value to be processed
     * @return the new value with get_property processed
     */
    private IValue process(IValue value, IPaaSTemplate node, Map<String, PaaSNodeTemplate> allNodes) {
        if (value instanceof FunctionPropertyValue) {
            return processSimpleFunction((FunctionPropertyValue) value, node, allNodes);
        } else if (value instanceof ConcatPropertyValue) {
            return processConcatFunction((ConcatPropertyValue) value, node, allNodes);
        } else {
            return value;
        }
    }

    private IValue processSimpleFunction(FunctionPropertyValue value, IPaaSTemplate node, Map<String, PaaSNodeTemplate> allNodes) {
        if (ToscaFunctionConstants.GET_PROPERTY.equals(value.getFunction())) {
            return new ScalarPropertyValue(FunctionEvaluator.evaluateGetPropertyFunction(value, node, allNodes));
        } else {
            return value;
        }
    }

    private IValue processConcatFunction(ConcatPropertyValue concatPropertyValue, IPaaSTemplate node, Map<String, PaaSNodeTemplate> allNodes) {
        ConcatPropertyValue processedConcat = new ConcatPropertyValue();
        processedConcat.setFunction_concat(concatPropertyValue.getFunction_concat());
        processedConcat.setParameters(Lists.newArrayList(concatPropertyValue.getParameters()));
        if (concatPropertyValue.getParameters() == null || concatPropertyValue.getParameters().isEmpty()) {
            throw new InvalidArgumentException("Parameter list for concat function is empty");
        }
        for (int i = 0; i < concatPropertyValue.getParameters().size(); i++) {
            IValue concatParam = concatPropertyValue.getParameters().get(i);
            if (concatParam instanceof FunctionPropertyValue) {
                concatPropertyValue.getParameters().set(i, processSimpleFunction((FunctionPropertyValue) concatParam, node, allNodes));
            }
        }
        return processedConcat;
    }
}
