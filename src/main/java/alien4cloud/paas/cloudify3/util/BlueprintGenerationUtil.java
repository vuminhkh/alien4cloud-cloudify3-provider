package alien4cloud.paas.cloudify3.util;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import alien4cloud.model.components.FunctionPropertyValue;
import alien4cloud.model.components.IOperationParameter;
import alien4cloud.model.components.IndexedArtifactToscaElement;
import alien4cloud.model.components.Interface;
import alien4cloud.model.components.Operation;
import alien4cloud.model.components.ScalarPropertyValue;
import alien4cloud.paas.cloudify3.configuration.CloudConfiguration;
import alien4cloud.paas.cloudify3.configuration.Network;
import alien4cloud.paas.cloudify3.error.OperationNotSupportedException;
import alien4cloud.paas.cloudify3.service.model.MappingConfiguration;
import alien4cloud.paas.cloudify3.service.model.MatchedPaaSNativeComponentTemplate;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;
import alien4cloud.paas.plan.ToscaNodeLifecycleConstants;
import alien4cloud.paas.plan.ToscaRelationshipLifecycleConstants;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Some utilities method which help generating cloudify 3 blueprint
 *
 * @author Minh Khang VU
 */
@AllArgsConstructor
@Slf4j
public class BlueprintGenerationUtil {

    private static final Map<String, String> REL_SOURCE_MAPPING = Maps.newHashMap();
    private static final Map<String, String> REL_TARGET_MAPPING = Maps.newHashMap();
    private static final Map<String, String> ATTRIBUTE_MAPPING = Maps.newHashMap();

    static {
        REL_SOURCE_MAPPING.put(ToscaRelationshipLifecycleConstants.PRE_CONFIGURE_SOURCE, "preconfigure");
        REL_SOURCE_MAPPING.put(ToscaRelationshipLifecycleConstants.POST_CONFIGURE_SOURCE, "postconfigure");
        REL_SOURCE_MAPPING.put(ToscaRelationshipLifecycleConstants.ADD_TARGET, "establish");
        REL_SOURCE_MAPPING.put(ToscaRelationshipLifecycleConstants.REMOVE_TARGET, "unlink");

        REL_TARGET_MAPPING.put(ToscaRelationshipLifecycleConstants.PRE_CONFIGURE_TARGET, "preconfigure");
        REL_TARGET_MAPPING.put(ToscaRelationshipLifecycleConstants.POST_CONFIGURE_TARGET, "postconfigure");
        REL_TARGET_MAPPING.put(ToscaRelationshipLifecycleConstants.ADD_SOURCE, "establish");
        REL_TARGET_MAPPING.put(ToscaRelationshipLifecycleConstants.REMOVE_SOURCE, "unlink");

        ATTRIBUTE_MAPPING.put("ip_address", "ip");
    }

    private MappingConfiguration mapping;

    public boolean mapHasEntries(Map<?, ?> map) {
        return map != null && !map.isEmpty();
    }

    public boolean collectionHasElement(Collection<?> list) {
        return list != null && !list.isEmpty();
    }

    public Map<String, Interface> getRelationshipSourceInterfaces(Map<String, Interface> interfaces) {
        return getRelationshipInterfaces(interfaces, true);
    }

    public Map<String, Interface> getRelationshipTargetInterfaces(Map<String, Interface> interfaces) {
        return getRelationshipInterfaces(interfaces, false);
    }

    private Map<String, Interface> getRelationshipInterfaces(Map<String, Interface> interfaces, boolean isSource) {
        Map<String, Interface> relationshipInterfaces = Maps.newHashMap();
        for (Map.Entry<String, Interface> interfaceEntry : interfaces.entrySet()) {
            Map<String, Operation> operations = Maps.newHashMap();
            for (Map.Entry<String, Operation> operationEntry : interfaceEntry.getValue().getOperations().entrySet()) {
                if (!REL_SOURCE_MAPPING.containsKey(operationEntry.getKey()) && !REL_TARGET_MAPPING.containsKey(operationEntry.getKey())) {
                    log.warn("Operation {} on relationship is not managed", operationEntry.getKey());
                    continue;
                }
                if (isSource) {
                    if (REL_SOURCE_MAPPING.containsKey(operationEntry.getKey())) {
                        operations.put(operationEntry.getKey(), operationEntry.getValue());
                    }
                } else {
                    if (REL_TARGET_MAPPING.containsKey(operationEntry.getKey())) {
                        operations.put(operationEntry.getKey(), operationEntry.getValue());
                    }
                }
            }
            if (!operations.isEmpty()) {
                Interface inter = new Interface();
                inter.setDescription(interfaceEntry.getValue().getDescription());
                inter.setOperations(operations);
                relationshipInterfaces.put(interfaceEntry.getKey(), inter);
            }
        }
        return relationshipInterfaces;
    }

    public Network getNetwork(CloudConfiguration cloud, MatchedPaaSNativeComponentTemplate networkTemplate) {
        String networkId = networkTemplate.getPaaSResourceId();
        return cloud.getNetworkTemplates().get(networkId);
    }

    /**
     * Extract all operations, interfaces that has input or do not have input from the give type.
     *
     * @param type the tosca type
     * @param withParameter interfaces' operations with parameters
     * @return only interfaces' operations that have input or don't have input
     */
    public Map<String, Interface> getInterfaces(IndexedArtifactToscaElement type, boolean withParameter) {
        Map<String, Interface> allInterfaces = type.getInterfaces();
        Map<String, Interface> interfaces = Maps.newHashMap();
        for (Map.Entry<String, Interface> interfaceEntry : allInterfaces.entrySet()) {
            Map<String, Operation> operations = Maps.newHashMap();
            for (Map.Entry<String, Operation> operationEntry : interfaceEntry.getValue().getOperations().entrySet()) {
                Map<String, IOperationParameter> parameters = operationEntry.getValue().getInputParameters();
                if (parameters == null || parameters.isEmpty()) {
                    // No parameter
                    if (!withParameter) {
                        operations.put(operationEntry.getKey(), operationEntry.getValue());
                    }
                } else if (withParameter) {
                    // With parameter
                    operations.put(operationEntry.getKey(), operationEntry.getValue());
                }
            }
            if (!operations.isEmpty()) {
                // At least one operation fulfill the criteria
                Interface inter = new Interface();
                inter.setDescription(interfaceEntry.getValue().getDescription());
                inter.setOperations(operations);
                interfaces.put(interfaceEntry.getKey(), inter);
            }
        }
        return interfaces;
    }

    public String formatRelationshipOperationInput(PaaSRelationshipTemplate relationship, IOperationParameter input, Map<String, PaaSNodeTemplate> allNodes) {
        if (input instanceof FunctionPropertyValue) {
            FunctionPropertyValue functionPropertyValue = (FunctionPropertyValue) input;
            List<String> parameters = functionPropertyValue.getParameters();
            String nodeName = parameters.get(0);
            String attribute = parameters.get(parameters.size() - 1);
            if ("SOURCE".equals(nodeName)) {
                nodeName = relationship.getSource();
            } else if ("TARGET".equals(nodeName)) {
                nodeName = relationship.getRelationshipTemplate().getTarget();
            }
            PaaSNodeTemplate node = allNodes.get(nodeName);
            String resolvedNodeName = getNodeNameHasPropertyOrAttribute(relationship.getSource(), node, attribute, functionPropertyValue.getFunction());
            return formatNodeOperationResolvedInput(functionPropertyValue, resolvedNodeName);
        } else {
            return formatOperationInput(input);
        }
    }

    public String formatNodeOperationInput(PaaSNodeTemplate node, IOperationParameter input, Map<String, PaaSNodeTemplate> allNodes) {
        if (input instanceof FunctionPropertyValue) {
            FunctionPropertyValue functionPropertyValue = (FunctionPropertyValue) input;
            List<String> parameters = functionPropertyValue.getParameters();
            String nodeName = parameters.get(0);
            String attribute = parameters.get(parameters.size() - 1);
            if ("HOST".equals(nodeName)) {
                // Resolve HOST
                PaaSNodeTemplate host = node.getParent();
                while (host.getParent() != null) {
                    host = host.getParent();
                }
                nodeName = host.getId();
            } else if ("SELF".equals(nodeName)) {
                nodeName = node.getId();
            }
            String resolvedNodeName = getNodeNameHasPropertyOrAttribute(node.getId(), allNodes.get(nodeName), attribute, functionPropertyValue.getFunction());
            return formatNodeOperationResolvedInput(functionPropertyValue, resolvedNodeName);
        } else {
            return formatOperationInput(input);
        }
    }

    private String formatNodeOperationResolvedInput(FunctionPropertyValue functionPropertyValue, String resolvedNodeName) {
        FunctionPropertyValue filteredFunctionPropertyValue = new FunctionPropertyValue();
        filteredFunctionPropertyValue.setFunction(functionPropertyValue.getFunction());
        List<String> newParameters = Lists.newArrayList(functionPropertyValue.getParameters());
        newParameters.set(0, resolvedNodeName);
        filteredFunctionPropertyValue.setParameters(newParameters);
        return formatOperationInput(filteredFunctionPropertyValue);
    }

    private String getNodeNameHasPropertyOrAttribute(String parentNodeName, PaaSNodeTemplate node, String attributeName, String functionName) {
        String nodeName = doGetNodeNameHasPropertyOrAttribute(node, attributeName, functionName);
        if (nodeName == null) {
            // Not found just take the initial value and emit warning
            log.warn("Node {} ask for attribute of node {} but it's not found", parentNodeName, node.getId(), attributeName);
            return node.getId();
        } else {
            return nodeName;
        }
    }

    private String doGetNodeNameHasPropertyOrAttribute(PaaSNodeTemplate node, String attributeName, String functionName) {
        Set<String> propertiesOrAttributes = null;
        if ("get_property".equals(functionName)) {
            if (node.getIndexedToscaElement().getProperties() != null) {
                propertiesOrAttributes = node.getIndexedToscaElement().getProperties().keySet();
            }
        } else {
            if (node.getIndexedToscaElement().getAttributes() != null) {
                propertiesOrAttributes = node.getIndexedToscaElement().getAttributes().keySet();
            }
        }
        if (propertiesOrAttributes == null || !propertiesOrAttributes.contains(attributeName)) {
            if (node.getParent() != null) {
                return doGetNodeNameHasPropertyOrAttribute(node.getParent(), attributeName, functionName);
            } else {
                return null;
            }
        } else {
            return node.getId();
        }
    }

    public String formatOperationInput(IOperationParameter input) {
        if (input instanceof FunctionPropertyValue) {
            FunctionPropertyValue functionPropertyValue = (FunctionPropertyValue) input;
            StringBuilder formattedInput = new StringBuilder("{ ").append(functionPropertyValue.getFunction()).append(": [");
            for (int i = 0; i < functionPropertyValue.getParameters().size(); i++) {
                String attributeName = functionPropertyValue.getParameters().get(i);
                if (ATTRIBUTE_MAPPING.containsKey(attributeName)) {
                    attributeName = ATTRIBUTE_MAPPING.get(attributeName);
                }
                formattedInput.append(attributeName).append(", ");
            }
            // Remove the last ','
            formattedInput.setLength(formattedInput.length() - 2);
            formattedInput.append("] }");
            return formattedInput.toString();
        } else if (input instanceof ScalarPropertyValue) {
            return ((ScalarPropertyValue) input).getValue();
        } else {
            throw new OperationNotSupportedException("Type of operation parameter not supported <" + input.getClass().getName() + ">");
        }
    }

    public boolean hasMatchedNetwork(List<PaaSNodeTemplate> allComputeNetworks, List<MatchedPaaSNativeComponentTemplate> externalMatchedNetworks) {
        if (allComputeNetworks == null || externalMatchedNetworks == null) {
            return false;
        }
        for (PaaSNodeTemplate network : allComputeNetworks) {
            if (isMatched(network, externalMatchedNetworks)) {
                return true;
            }
        }
        return false;
    }

    public String getExternalNetworkName(List<PaaSNodeTemplate> allComputeNetworks, List<MatchedPaaSNativeComponentTemplate> externalMatchedNetworks) {
        for (PaaSNodeTemplate network : allComputeNetworks) {
            MatchedPaaSNativeComponentTemplate externalMatchedNetwork = getMatchedNetwork(network, externalMatchedNetworks);
            if (externalMatchedNetwork != null) {
                return externalMatchedNetwork.getPaaSResourceId();
            }
        }
        return null;
    }

    public List<PaaSNodeTemplate> getInternalNetworks(List<PaaSNodeTemplate> allComputeNetworks,
            List<MatchedPaaSNativeComponentTemplate> internalMatchedNetworks) {
        List<PaaSNodeTemplate> internalNetworksNodes = Lists.newArrayList();
        for (PaaSNodeTemplate network : allComputeNetworks) {
            MatchedPaaSNativeComponentTemplate internalMatchedNetwork = getMatchedNetwork(network, internalMatchedNetworks);
            if (internalMatchedNetwork != null) {
                internalNetworksNodes.add(network);
            }
        }
        return internalNetworksNodes;
    }

    private boolean isMatched(PaaSNodeTemplate network, List<MatchedPaaSNativeComponentTemplate> matchedNetworks) {
        for (MatchedPaaSNativeComponentTemplate externalMatchedNetwork : matchedNetworks) {
            if (externalMatchedNetwork.getPaaSNodeTemplate().getId().equals(network.getId())) {
                return true;
            }
        }
        return false;
    }

    private MatchedPaaSNativeComponentTemplate getMatchedNetwork(PaaSNodeTemplate network, List<MatchedPaaSNativeComponentTemplate> matchedNetworks) {
        for (MatchedPaaSNativeComponentTemplate externalMatchedNetwork : matchedNetworks) {
            if (externalMatchedNetwork.getPaaSNodeTemplate().getId().equals(network.getId())) {
                return externalMatchedNetwork;
            }
        }
        return null;
    }

    public List<PaaSRelationshipTemplate> getSourceRelationships(PaaSNodeTemplate nodeTemplate) {
        List<PaaSRelationshipTemplate> relationshipTemplates = nodeTemplate.getRelationshipTemplates();
        List<PaaSRelationshipTemplate> sourceRelationshipTemplates = Lists.newArrayList();
        for (PaaSRelationshipTemplate relationshipTemplate : relationshipTemplates) {
            if (relationshipTemplate.getSource().equals(nodeTemplate.getId())) {
                sourceRelationshipTemplates.add(relationshipTemplate);
            }
        }
        return sourceRelationshipTemplates;
    }

    public String tryToMapToCloudifyType(String toscaType) {
        String mappedType = mapping.getNormativeTypes().get(toscaType);
        return mappedType != null ? mappedType : toscaType;
    }

    public boolean typeMustBeMappedToCloudifyType(String toscaType) {
        return mapping.getNormativeTypes().containsKey(toscaType);
    }

    public String tryToMapToCloudifyInterface(String interfaceName) {
        if (ToscaNodeLifecycleConstants.STANDARD.equals(interfaceName) || ToscaNodeLifecycleConstants.STANDARD_SHORT.equals(interfaceName)) {
            return "cloudify.interfaces.lifecycle";
        } else {
            return interfaceName;
        }
    }

    public String tryToMapToCloudifyRelationshipInterface(String interfaceName) {
        if (ToscaRelationshipLifecycleConstants.CONFIGURE.equals(interfaceName) || ToscaRelationshipLifecycleConstants.CONFIGURE_SHORT.equals(interfaceName)) {
            return "cloudify.interfaces.relationship_lifecycle";
        } else {
            return interfaceName;
        }
    }

    public String tryToMapToCloudifyRelationshipInterfaceOperation(String operationName, boolean isSource) {
        Map<String, String> mapping;
        if (isSource) {
            mapping = REL_SOURCE_MAPPING;
        } else {
            mapping = REL_TARGET_MAPPING;
        }
        String mappedName = mapping.get(operationName);
        return mappedName != null ? mappedName : operationName;
    }

    public String tryToMapToCloudifyRelationshipSourceInterfaceOperation(String operationName) {
        return tryToMapToCloudifyRelationshipInterfaceOperation(operationName, true);
    }

    public String tryToMapToCloudifyRelationshipTargetInterfaceOperation(String operationName) {
        return tryToMapToCloudifyRelationshipInterfaceOperation(operationName, false);
    }

    public String getDerivedFromType(List<String> allDerivedFromsTypes) {
        for (String derivedFromType : allDerivedFromsTypes) {
            if (typeMustBeMappedToCloudifyType(derivedFromType)) {
                return tryToMapToCloudifyType(derivedFromType);
            }
        }
        // This must never happens
        return allDerivedFromsTypes.get(0);
    }
}
