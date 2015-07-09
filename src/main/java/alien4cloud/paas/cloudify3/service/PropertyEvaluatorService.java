package alien4cloud.paas.cloudify3.service;

import java.util.Map;

import org.springframework.stereotype.Component;

import alien4cloud.exception.InvalidArgumentException;
import alien4cloud.model.components.ConcatPropertyValue;
import alien4cloud.model.components.FunctionPropertyValue;
import alien4cloud.model.components.IValue;
import alien4cloud.model.components.ScalarPropertyValue;
import alien4cloud.paas.IPaaSTemplate;
import alien4cloud.paas.function.FunctionEvaluator;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.tosca.normative.ToscaFunctionConstants;

import com.google.common.collect.Lists;

@Component("property-evaluator-service")
public class PropertyEvaluatorService {

    /**
     * Process an IValue (it can be a scalar value, a get_property, get_attribute, get_operation_output or a concat) and replace all get_property occurrence
     * with its value and return the new IValue with get_property replaced by its value
     * 
     * @param value the value to be processed
     * @return the new value with get_property processed
     */
    public IValue process(IValue value, IPaaSTemplate node, Map<String, PaaSNodeTemplate> allNodes) {
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
