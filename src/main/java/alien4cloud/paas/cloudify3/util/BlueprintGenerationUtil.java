package alien4cloud.paas.cloudify3.util;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;

import alien4cloud.model.cloud.StorageTemplate;
import alien4cloud.model.components.FunctionPropertyValue;
import alien4cloud.model.components.IOperationParameter;
import alien4cloud.model.components.IndexedArtifactToscaElement;
import alien4cloud.model.components.Interface;
import alien4cloud.model.components.Operation;
import alien4cloud.model.components.ScalarPropertyValue;
import alien4cloud.paas.cloudify3.configuration.MappingConfiguration;
import alien4cloud.paas.cloudify3.configuration.ProviderMappingConfiguration;
import alien4cloud.paas.cloudify3.error.BadConfigurationException;
import alien4cloud.paas.cloudify3.error.OperationNotSupportedException;
import alien4cloud.paas.cloudify3.service.model.CloudifyDeployment;
import alien4cloud.paas.cloudify3.service.model.MatchedPaaSTemplate;
import alien4cloud.paas.cloudify3.service.model.NativeType;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;
import alien4cloud.paas.plan.ToscaNodeLifecycleConstants;
import alien4cloud.paas.plan.ToscaRelationshipLifecycleConstants;
import alien4cloud.tosca.ToscaUtils;
import alien4cloud.tosca.normative.AlienCustomTypes;
import alien4cloud.tosca.normative.NormativeBlockStorageConstants;

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

    private MappingConfiguration mappingConfiguration;

    private ProviderMappingConfiguration providerMappingConfiguration;

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
            Map<String, String> relationshipSourceMapping = mappingConfiguration.getRelationships().getLifeCycle().getSource();
            Map<String, String> relationshipTargetMapping = mappingConfiguration.getRelationships().getLifeCycle().getTarget();
            for (Map.Entry<String, Operation> operationEntry : interfaceEntry.getValue().getOperations().entrySet()) {
                if (!relationshipSourceMapping.containsKey(operationEntry.getKey()) && !relationshipTargetMapping.containsKey(operationEntry.getKey())) {
                    log.warn("Operation {} on relationship is not managed", operationEntry.getKey());
                    continue;
                }
                if (isSource) {
                    if (relationshipSourceMapping.containsKey(operationEntry.getKey())) {
                        operations.put(operationEntry.getKey(), operationEntry.getValue());
                    }
                } else {
                    if (relationshipTargetMapping.containsKey(operationEntry.getKey())) {
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

    public String formatRelationshipOperationInput(PaaSRelationshipTemplate relationship, IOperationParameter input, CloudifyDeployment cloudifyDeployment) {
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
            PaaSNodeTemplate node = cloudifyDeployment.getAllNodes().get(nodeName);
            String resolvedNodeName = getNodeNameHasPropertyOrAttribute(relationship.getSource(), node, attribute, functionPropertyValue.getFunction());
            return formatFunctionPropertyInputValue(nodeName, cloudifyDeployment, functionPropertyValue, resolvedNodeName);
        } else if (input instanceof ScalarPropertyValue) {
            return ((ScalarPropertyValue) input).getValue();
        } else {
            throw new OperationNotSupportedException("Type of operation parameter not supported <" + input.getClass().getName() + ">");
        }
    }

    public String formatNodeOperationInput(PaaSNodeTemplate node, IOperationParameter input, CloudifyDeployment cloudifyDeployment) {
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
            String resolvedNodeName = getNodeNameHasPropertyOrAttribute(node.getId(), cloudifyDeployment.getAllNodes().get(nodeName), attribute,
                    functionPropertyValue.getFunction());
            return formatFunctionPropertyInputValue(nodeName, cloudifyDeployment, functionPropertyValue, resolvedNodeName);
        } else if (input instanceof ScalarPropertyValue) {
            return ((ScalarPropertyValue) input).getValue();
        } else {
            throw new OperationNotSupportedException("Type of operation parameter not supported <" + input.getClass().getName() + ">");
        }
    }

    private String getNodeNameHasPropertyOrAttribute(String parentNodeName, PaaSNodeTemplate node, String attributeName, String functionName) {
        String nodeName = doGetNodeNameHasPropertyOrAttribute(node, attributeName, functionName);
        if (nodeName == null) {
            // Not found just take the initial value and emit warning
            log.warn("Node {} ask for property/attribute {} of node {} but it's not found", parentNodeName, attributeName, node.getId());
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

    private String formatFunctionPropertyInputValue(String nodeName, CloudifyDeployment cloudifyDeployment, FunctionPropertyValue functionPropertyValue,
            String resolvedNodeName) {
        FunctionPropertyValue filteredFunctionPropertyValue = new FunctionPropertyValue();
        filteredFunctionPropertyValue.setFunction(functionPropertyValue.getFunction());
        List<String> newParameters = Lists.newArrayList(functionPropertyValue.getParameters());
        newParameters.set(0, resolvedNodeName);
        filteredFunctionPropertyValue.setParameters(newParameters);
        String nativeType = getNativeType(cloudifyDeployment, resolvedNodeName);
        Map<String, String> attributeMapping = null;
        if (nativeType != null) {
            attributeMapping = providerMappingConfiguration.getAttributes().get(nativeType);
        }
        StringBuilder formattedInput = new StringBuilder("{ ").append(filteredFunctionPropertyValue.getFunction()).append(": [");
        for (int i = 0; i < filteredFunctionPropertyValue.getParameters().size(); i++) {
            String attributeName = filteredFunctionPropertyValue.getParameters().get(i);
            if (attributeMapping != null && attributeMapping.containsKey(attributeName)) {
                attributeName = attributeMapping.get(attributeName);
            }
            formattedInput.append(attributeName).append(", ");
        }
        // Remove the last ','
        formattedInput.setLength(formattedInput.length() - 2);
        formattedInput.append("] }");
        return formattedInput.toString();
    }

    private String getNativeType(CloudifyDeployment deployment, String id) {
        if (deployment.getComputesMap().containsKey(id)) {
            return NativeType.COMPUTE;
        } else if (deployment.getVolumesMap().containsKey(id)) {
            return NativeType.VOLUME;
        } else if (deployment.getExternalNetworksMap().containsKey(id) || deployment.getInternalNetworksMap().containsKey(id)) {
            return NativeType.NETWORK;
        } else {
            return null;
        }
    }

    public boolean hasMatchedNetwork(List<PaaSNodeTemplate> allComputeNetworks, List<MatchedPaaSTemplate> externalMatchedNetworks) {
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

    public String getExternalNetworkName(List<PaaSNodeTemplate> allComputeNetworks, List<MatchedPaaSTemplate> externalMatchedNetworks) {
        for (PaaSNodeTemplate network : allComputeNetworks) {
            MatchedPaaSTemplate externalMatchedNetwork = getMatchedNetwork(network, externalMatchedNetworks);
            if (externalMatchedNetwork != null) {
                String externalNetworkId = externalMatchedNetwork.getPaaSResourceId();
                if (StringUtils.isEmpty(externalNetworkId)) {
                    throw new BadConfigurationException("External network id must be configured");
                } else {
                    return externalNetworkId;
                }
            }
        }
        return null;
    }

    public List<PaaSNodeTemplate> getInternalNetworks(List<PaaSNodeTemplate> allComputeNetworks, List<MatchedPaaSTemplate> internalMatchedNetworks) {
        List<PaaSNodeTemplate> internalNetworksNodes = Lists.newArrayList();
        for (PaaSNodeTemplate network : allComputeNetworks) {
            MatchedPaaSTemplate internalMatchedNetwork = getMatchedNetwork(network, internalMatchedNetworks);
            if (internalMatchedNetwork != null) {
                internalNetworksNodes.add(network);
            }
        }
        return internalNetworksNodes;
    }

    private boolean isMatched(PaaSNodeTemplate network, List<MatchedPaaSTemplate> matchedNetworks) {
        for (MatchedPaaSTemplate externalMatchedNetwork : matchedNetworks) {
            if (externalMatchedNetwork.getPaaSNodeTemplate().getId().equals(network.getId())) {
                return true;
            }
        }
        return false;
    }

    private MatchedPaaSTemplate getMatchedNetwork(PaaSNodeTemplate network, List<MatchedPaaSTemplate> matchedNetworks) {
        for (MatchedPaaSTemplate externalMatchedNetwork : matchedNetworks) {
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
        String mappedType = mappingConfiguration.getNormativeTypes().get(toscaType);
        return mappedType != null ? mappedType : toscaType;
    }

    private boolean typeMustBeMappedToCloudifyType(String toscaType) {
        return mappingConfiguration.getNormativeTypes().containsKey(toscaType);
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

    private String tryToMapToCloudifyRelationshipInterfaceOperation(String operationName, boolean isSource) {
        Map<String, String> mapping;
        if (isSource) {
            mapping = mappingConfiguration.getRelationships().getLifeCycle().getSource();
        } else {
            mapping = mappingConfiguration.getRelationships().getLifeCycle().getTarget();
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

    public boolean hasConfiguredVolume(List<MatchedPaaSTemplate<StorageTemplate>> volumes) {
        if (volumes != null && !volumes.isEmpty()) {
            for (MatchedPaaSTemplate<StorageTemplate> volume : volumes) {
                if (isConfiguredVolume(volume.getPaaSNodeTemplate())) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isConfiguredVolume(PaaSNodeTemplate volumeTemplate) {
        Map<String, String> volumeProperties = volumeTemplate.getNodeTemplate().getProperties();
        return volumeProperties != null
                && (!StringUtils.isEmpty(volumeProperties.get(NormativeBlockStorageConstants.LOCATION)) || !StringUtils.isEmpty(volumeProperties
                        .get(NormativeBlockStorageConstants.FILE_SYSTEM)));
    }

    public boolean isDeletableVolume(PaaSNodeTemplate volumeTemplate) {
        return ToscaUtils.isFromType(AlienCustomTypes.DELETABLE_BLOCKSTORAGE_TYPE, volumeTemplate.getIndexedToscaElement());
    }

    public String getExternalVolumeId(MatchedPaaSTemplate<StorageTemplate> matchedVolumeTemplate) {
        String volumeId = matchedVolumeTemplate.getPaaSResourceId();
        if (!StringUtils.isEmpty(volumeId)) {
            return volumeId;
        } else {
            Map<String, String> volumeProperties = matchedVolumeTemplate.getPaaSNodeTemplate().getNodeTemplate().getProperties();
            if (volumeProperties != null) {
                return volumeProperties.get(NormativeBlockStorageConstants.VOLUME_ID);
            } else {
                return null;
            }
        }
    }

    public PaaSNodeTemplate getConfiguredAttachedVolume(PaaSNodeTemplate node) {
        PaaSNodeTemplate host = node.getParent();
        while (host.getParent() != null) {
            host = host.getParent();
        }
        PaaSNodeTemplate volume = host.getAttachedNode();
        if (volume == null) {
            return null;
        }
        if (isConfiguredVolume(volume)) {
            return volume;
        } else {
            return null;
        }
    }
}
