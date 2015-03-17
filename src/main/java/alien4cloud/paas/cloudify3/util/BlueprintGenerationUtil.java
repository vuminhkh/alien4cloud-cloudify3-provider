package alien4cloud.paas.cloudify3.util;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;

import alien4cloud.model.cloud.StorageTemplate;
import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.components.DeploymentArtifact;
import alien4cloud.model.components.FunctionPropertyValue;
import alien4cloud.model.components.IOperationParameter;
import alien4cloud.model.components.IndexedArtifactToscaElement;
import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.model.components.Interface;
import alien4cloud.model.components.Operation;
import alien4cloud.model.components.ScalarPropertyValue;
import alien4cloud.paas.cloudify3.configuration.MappingConfiguration;
import alien4cloud.paas.cloudify3.configuration.ProviderMappingConfiguration;
import alien4cloud.paas.cloudify3.error.BadConfigurationException;
import alien4cloud.paas.cloudify3.service.model.CloudifyDeployment;
import alien4cloud.paas.cloudify3.service.model.MatchedPaaSTemplate;
import alien4cloud.paas.cloudify3.service.model.NativeType;
import alien4cloud.paas.exception.NotSupportedException;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;
import alien4cloud.paas.plan.ToscaNodeLifecycleConstants;
import alien4cloud.paas.plan.ToscaRelationshipLifecycleConstants;
import alien4cloud.tosca.ToscaUtils;
import alien4cloud.tosca.normative.AlienCustomTypes;
import alien4cloud.tosca.normative.NormativeBlockStorageConstants;
import alien4cloud.tosca.normative.NormativeComputeConstants;
import alien4cloud.tosca.normative.NormativeNetworkConstants;
import alien4cloud.tosca.normative.ToscaFunctionConstants;

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

    private CloudifyDeployment alienDeployment;

    public boolean mapHasEntries(Map<?, ?> map) {
        return map != null && !map.isEmpty();
    }

    public boolean collectionHasElement(Collection<?> list) {
        return list != null && !list.isEmpty();
    }

    public Map<String, Interface> getRelationshipSourceInterfaces(Map<String, Interface> interfaces) {
        Map<String, Interface> sourceInterfaces = getRelationshipInterfaces(interfaces, true);
        return sourceInterfaces;
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

    public Map<String, Interface> getRelationshipInterfaces(PaaSRelationshipTemplate relationship) {
        String source = relationship.getSource();
        String target = relationship.getRelationshipTemplate().getTarget();
        Map<String, DeploymentArtifact> sourceArtifacts = alienDeployment.getAllNodes().get(source).getIndexedToscaElement().getArtifacts();
        Map<String, DeploymentArtifact> targetArtifacts = alienDeployment.getAllNodes().get(target).getIndexedToscaElement().getArtifacts();
        Map<String, Interface> allInterfaces = relationship.getIndexedToscaElement().getInterfaces();
        Map<String, Interface> sourceInterfaces = getRelationshipSourceInterfaces(allInterfaces);
        Map<String, Interface> targetInterfaces = getRelationshipTargetInterfaces(allInterfaces);
        enrichInterfaceOperationsWithDeploymentArtifacts("SOURCE", sourceArtifacts, sourceInterfaces);
        enrichInterfaceOperationsWithDeploymentArtifacts("TARGET", targetArtifacts, targetInterfaces);
        allInterfaces = Maps.newHashMap(sourceInterfaces);
        for (Map.Entry<String, Interface> targetInterface : targetInterfaces.entrySet()) {
            if (!allInterfaces.containsKey(targetInterface.getKey())) {
                allInterfaces.put(targetInterface.getKey(), targetInterface.getValue());
            } else {
                allInterfaces.get(targetInterface.getKey()).getOperations().putAll(targetInterface.getValue().getOperations());
            }
        }
        return getInterfaces(allInterfaces, relationship.getIndexedToscaElement());
    }

    public boolean operationHasInputParameters(Operation operation) {
        Map<String, IOperationParameter> inputParameters = operation.getInputParameters();
        return inputParameters != null && !inputParameters.isEmpty();
    }

    private void enrichInterfaceOperationsWithDeploymentArtifacts(String artifactOwner, Map<String, DeploymentArtifact> artifacts,
            Map<String, Interface> interfaces) {
        if (artifacts == null || artifacts.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Interface> interfaceEntry : interfaces.entrySet()) {
            for (Map.Entry<String, Operation> operationEntry : interfaceEntry.getValue().getOperations().entrySet()) {
                Map<String, IOperationParameter> parameters = operationEntry.getValue().getInputParameters();
                if (parameters == null) {
                    parameters = Maps.newHashMap();
                    operationEntry.getValue().setInputParameters(parameters);
                }
                for (Map.Entry<String, DeploymentArtifact> artifactEntry : artifacts.entrySet()) {
                    if (!parameters.containsKey(artifactEntry.getKey())) {
                        // TODO must not generate this kind of information
                        // TODO instead it's the responsibility of the people which build the recipe to do so
                        FunctionPropertyValue getArtifactFunction = new FunctionPropertyValue();
                        getArtifactFunction.setFunction("get_artifact");
                        getArtifactFunction.setParameters(Lists.newArrayList(artifactOwner, artifactEntry.getKey()));
                        parameters.put(artifactEntry.getKey(), getArtifactFunction);
                    }
                }
            }
        }
    }

    public Map<String, Interface> getNodeInterfaces(PaaSNodeTemplate node) {
        return getInterfaces(node.getIndexedToscaElement().getInterfaces(), node.getIndexedToscaElement());
    }

    /**
     * Extract all operations, interfaces that has input or do not have input from the give type.
     *
     * @param allInterfaces all interfaces
     * @param type the tosca type
     * @return only interfaces' operations that have input or don't have input
     */
    private Map<String, Interface> getInterfaces(Map<String, Interface> allInterfaces, IndexedArtifactToscaElement type) {
        enrichInterfaceOperationsWithDeploymentArtifacts("SELF", type.getArtifacts(), allInterfaces);
        Map<String, Interface> interfaces = Maps.newHashMap();
        for (Map.Entry<String, Interface> interfaceEntry : allInterfaces.entrySet()) {
            Map<String, Operation> operations = Maps.newHashMap();
            for (Map.Entry<String, Operation> operationEntry : interfaceEntry.getValue().getOperations().entrySet()) {
                if (operationEntry.getValue().getImplementationArtifact() == null) {
                    // Don't consider operation which do not have any implementation artifact
                    continue;
                }
                operations.put(operationEntry.getKey(), operationEntry.getValue());
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

    public String formatRelationshipOperationInput(PaaSRelationshipTemplate relationship, IOperationParameter input, boolean isSource) {
        if (input instanceof FunctionPropertyValue) {
            FunctionPropertyValue functionPropertyValue = (FunctionPropertyValue) input;
            String nodeName = functionPropertyValue.getTemplateName();
            String attribute = functionPropertyValue.getPropertyOrAttributeName();
            // SOURCE and TARGET
            if (ToscaFunctionConstants.SOURCE.equals(nodeName)) {
                nodeName = relationship.getSource();
            } else if (ToscaFunctionConstants.TARGET.equals(nodeName)) {
                nodeName = relationship.getRelationshipTemplate().getTarget();
            }
            // SELF only work for get_artifact
            if ("get_artifact".equals(functionPropertyValue.getFunction())) {
                String resolvedNodeName;
                if (ToscaFunctionConstants.SELF.equals(nodeName)) {
                    if (isSource) {
                        resolvedNodeName = mappingConfiguration.getGeneratedNodePrefix() + "_artifacts_for_relationship_" + relationship.getId() + "_from_"
                                + relationship.getSource() + "_on_source";
                    } else {
                        resolvedNodeName = mappingConfiguration.getGeneratedNodePrefix() + "_artifacts_for_relationship_" + relationship.getId() + "_from_"
                                + relationship.getSource() + "_on_target";
                    }
                } else {
                    resolvedNodeName = mappingConfiguration.getGeneratedNodePrefix() + "_artifacts_for_" + nodeName;
                }
                FunctionPropertyValue newFunctionPropertyValue = new FunctionPropertyValue();
                newFunctionPropertyValue.setFunction(ToscaFunctionConstants.GET_ATTRIBUTE);
                newFunctionPropertyValue.setParameters(functionPropertyValue.getParameters());
                return formatFunctionPropertyInputValue(newFunctionPropertyValue, resolvedNodeName);
            } else {
                String resolvedNodeName = getNodeNameHasPropertyOrAttribute(relationship.getSource(), alienDeployment.getAllNodes().get(nodeName), attribute,
                        functionPropertyValue.getFunction());
                return formatFunctionPropertyInputValue(functionPropertyValue, resolvedNodeName);
            }
        } else if (input instanceof ScalarPropertyValue) {
            return ((ScalarPropertyValue) input).getValue();
        } else {
            throw new NotSupportedException("Type of operation parameter not supported <" + input.getClass().getName() + ">");
        }
    }

    /**
     * Format operation parameter of a particular node
     *
     * @param node the node
     * @param input the input which can be a function or a scalar
     * @return the formatted parameter understandable by Cloudify 3
     */
    public String formatNodeOperationInput(PaaSNodeTemplate node, IOperationParameter input) {
        if (input instanceof FunctionPropertyValue) {
            FunctionPropertyValue functionPropertyValue = (FunctionPropertyValue) input;
            String nodeName = functionPropertyValue.getTemplateName();
            String attribute = functionPropertyValue.getPropertyOrAttributeName();
            if (ToscaFunctionConstants.HOST.equals(nodeName)) {
                // Resolve HOST
                PaaSNodeTemplate host = node.getParent() != null ? node.getParent() : node;
                nodeName = host.getId();
            } else if (ToscaFunctionConstants.SELF.equals(nodeName)) {
                nodeName = node.getId();
            }
            if ("get_artifact".equals(functionPropertyValue.getFunction())) {
                String resolvedNodeName = mappingConfiguration.getGeneratedNodePrefix() + "_artifacts_for_" + nodeName;
                FunctionPropertyValue newFunctionPropertyValue = new FunctionPropertyValue();
                newFunctionPropertyValue.setFunction(ToscaFunctionConstants.GET_ATTRIBUTE);
                newFunctionPropertyValue.setParameters(functionPropertyValue.getParameters());
                return formatFunctionPropertyInputValue(newFunctionPropertyValue, resolvedNodeName);
            } else {
                String resolvedNodeName = getNodeNameHasPropertyOrAttribute(node.getId(), alienDeployment.getAllNodes().get(nodeName), attribute,
                        functionPropertyValue.getFunction());
                return formatFunctionPropertyInputValue(functionPropertyValue, resolvedNodeName);
            }
        } else if (input instanceof ScalarPropertyValue) {
            return ((ScalarPropertyValue) input).getValue();
        } else {
            throw new NotSupportedException("Type of operation parameter not supported <" + input.getClass().getName() + ">");
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
        if (ToscaFunctionConstants.GET_PROPERTY.equals(functionName)) {
            if (node.getIndexedToscaElement().getProperties() != null) {
                propertiesOrAttributes = node.getIndexedToscaElement().getProperties().keySet();
            }
        } else if (ToscaFunctionConstants.GET_ATTRIBUTE.equals(functionName)) {
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

    private String formatFunctionPropertyInputValue(FunctionPropertyValue functionPropertyValue, String resolvedNodeName) {
        FunctionPropertyValue filteredFunctionPropertyValue = new FunctionPropertyValue();
        String functionName = functionPropertyValue.getFunction();
        filteredFunctionPropertyValue.setFunction(functionName);
        List<String> newParameters = Lists.newArrayList(functionPropertyValue.getParameters());
        newParameters.set(0, resolvedNodeName);
        filteredFunctionPropertyValue.setParameters(newParameters);
        String nativeType = getNativeType(resolvedNodeName);
        StringBuilder formattedInput = new StringBuilder("{ ").append(filteredFunctionPropertyValue.getFunction()).append(": [");
        for (int i = 0; i < filteredFunctionPropertyValue.getParameters().size(); i++) {
            String attributeName = filteredFunctionPropertyValue.getParameters().get(i);
            attributeName = mapPropAttName(functionPropertyValue.getFunction(), attributeName, nativeType);
            formattedInput.append(attributeName).append(", ");
        }
        // Remove the last ','
        formattedInput.setLength(formattedInput.length() - 2);
        formattedInput.append("] }");
        return formattedInput.toString();
    }

    private String mapPropAttName(String function, String propAttName, String nativeType) {
        if (nativeType == null) {
            // Non native type do not have property or attribute name mapping as it's not cloudify dependent
            return propAttName;
        } else if (ToscaFunctionConstants.GET_ATTRIBUTE.equals(function)) {
            // Get attribute mapping from provider configuration
            Map<String, String> attributeMapping = providerMappingConfiguration.getAttributes().get(nativeType);
            if (attributeMapping != null) {
                String mappedAttributeName = attributeMapping.get(propAttName);
                if (mappedAttributeName != null) {
                    return mappedAttributeName;
                } else {
                    return propAttName;
                }
            } else {
                return propAttName;
            }
        } else if (ToscaFunctionConstants.GET_PROPERTY.equals(function)) {
            // property for native type is prefixed
            return mappingConfiguration.getNativePropertyParent() + "." + propAttName;
        } else {
            return propAttName;
        }
    }

    private String getNativeType(String id) {
        if (alienDeployment.getComputesMap().containsKey(id)) {
            return NativeType.COMPUTE;
        } else if (alienDeployment.getVolumesMap().containsKey(id)) {
            return NativeType.VOLUME;
        } else if (alienDeployment.getExternalNetworksMap().containsKey(id) || alienDeployment.getInternalNetworksMap().containsKey(id)) {
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
        Map<String, AbstractPropertyValue> volumeProperties = volumeTemplate.getNodeTemplate().getProperties();
        return volumeProperties != null
                && (volumeProperties.containsKey(NormativeBlockStorageConstants.LOCATION) || volumeProperties
                        .containsKey(NormativeBlockStorageConstants.FILE_SYSTEM));
    }

    public boolean isDeletableVolumeType(IndexedNodeType volumeType) {
        return ToscaUtils.isFromType(AlienCustomTypes.DELETABLE_BLOCKSTORAGE_TYPE, volumeType);
    }

    public String getExternalVolumeId(MatchedPaaSTemplate<StorageTemplate> matchedVolumeTemplate) {
        String volumeId = matchedVolumeTemplate.getPaaSResourceId();
        if (!StringUtils.isEmpty(volumeId)) {
            return volumeId;
        } else {
            Map<String, AbstractPropertyValue> volumeProperties = matchedVolumeTemplate.getPaaSNodeTemplate().getNodeTemplate().getProperties();
            if (volumeProperties != null) {
                AbstractPropertyValue volumeIdValue = volumeProperties.get(NormativeBlockStorageConstants.VOLUME_ID);
                if (volumeIdValue != null) {
                    return formatNodeOperationInput(matchedVolumeTemplate.getPaaSNodeTemplate(), volumeIdValue);
                } else {
                    return null;
                }
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

    public String tryToMapComputeType(IndexedNodeType type, String defaultType) {
        return getMappedNativeType(type, NormativeComputeConstants.COMPUTE_TYPE, providerMappingConfiguration.getNativeTypes().getComputeType(),
                alienDeployment.getComputeTypes(), defaultType);
    }

    public String tryToMapComputeTypeDerivedFrom(IndexedNodeType type) {
        return getMappedNativeDerivedFromType(type, NormativeComputeConstants.COMPUTE_TYPE, providerMappingConfiguration.getNativeTypes().getComputeType(),
                alienDeployment.getComputeTypes());
    }

    public String tryToMapVolumeType(IndexedNodeType type, String defaultType) {
        return getMappedNativeType(type, NormativeBlockStorageConstants.BLOCKSTORAGE_TYPE, providerMappingConfiguration.getNativeTypes().getVolumeType(),
                alienDeployment.getVolumeTypes(), defaultType);
    }

    public String tryToMapVolumeTypeDerivedFrom(IndexedNodeType type) {
        return getMappedNativeDerivedFromType(type, NormativeBlockStorageConstants.BLOCKSTORAGE_TYPE, providerMappingConfiguration.getNativeTypes()
                .getVolumeType(), alienDeployment.getVolumeTypes());
    }

    public String tryToMapNetworkType(IndexedNodeType type, String defaultType) {
        return getMappedNativeType(type, NormativeNetworkConstants.NETWORK_TYPE, providerMappingConfiguration.getNativeTypes().getNetworkType(),
                alienDeployment.getNetworkTypes(), defaultType);
    }

    public String tryToMapNetworkTypeDerivedFrom(IndexedNodeType type) {
        return getMappedNativeDerivedFromType(type, NormativeNetworkConstants.NETWORK_TYPE, providerMappingConfiguration.getNativeTypes().getNetworkType(),
                alienDeployment.getNetworkTypes());
    }

    private String getMappedNativeType(IndexedNodeType type, String alienBaseType, String providerBaseType, List<IndexedNodeType> allDeploymentNativeTypes,
            String defaultType) {
        String nativeDerivedFrom = getMappedNativeDerivedFromType(type, alienBaseType, providerBaseType, allDeploymentNativeTypes);
        // If the native derive from is the provider base type, it means we should get the given default type
        if (providerBaseType.equals(nativeDerivedFrom)) {
            return defaultType;
        } else {
            return type.getElementId();
        }
    }

    private String getMappedNativeDerivedFromType(IndexedNodeType typeToMap, String alienBaseType, String providerBaseType,
            List<IndexedNodeType> allDeploymentNativeTypes) {
        if (alienBaseType.equals(typeToMap.getElementId())) {
            return providerBaseType;
        }
        List<String> derivedFroms = typeToMap.getDerivedFrom();
        for (String derivedFrom : derivedFroms) {
            if (alienBaseType.equals(derivedFrom)) {
                return providerBaseType;
            }
            IndexedNodeType mostSuitableType = getTypeFromName(derivedFrom, allDeploymentNativeTypes);
            if (mostSuitableType != null) {
                return mostSuitableType.getElementId();
            }
        }
        return typeToMap.getElementId();
    }

    private IndexedNodeType getTypeFromName(String name, List<IndexedNodeType> types) {
        for (IndexedNodeType type : types) {
            if (name.equals(type.getId())) {
                return type;
            }
        }
        return null;
    }

    public PaaSNodeTemplate getHost(PaaSNodeTemplate node) {
        return ToscaUtils.getMandatoryHostTemplate(node);
    }

    public boolean relationshipHasDeploymentArtifacts(PaaSRelationshipTemplate relationshipTemplate) {
        return alienDeployment.getAllRelationshipDeploymentArtifacts().containsKey(
                new CloudifyDeployment.Relationship(relationshipTemplate.getId(), relationshipTemplate.getSource(), relationshipTemplate
                        .getRelationshipTemplate().getTarget()));
    }

    public List<CloudifyDeployment.Relationship> getAllRelationshipWithDeploymentArtifacts(PaaSNodeTemplate nodeTemplate) {
        List<CloudifyDeployment.Relationship> relationships = Lists.newArrayList();
        for (CloudifyDeployment.Relationship relationship : alienDeployment.getAllRelationshipDeploymentArtifacts().keySet()) {
            if (relationship.getTarget().equals(nodeTemplate.getId())) {
                relationships.add(relationship);
            }
        }
        return relationships;
    }
}
