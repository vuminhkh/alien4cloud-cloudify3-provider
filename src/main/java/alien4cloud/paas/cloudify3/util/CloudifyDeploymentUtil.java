package alien4cloud.paas.cloudify3.util;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import alien4cloud.common.AlienConstants;
import alien4cloud.component.repository.ArtifactRepositoryConstants;
import alien4cloud.exception.InvalidArgumentException;
import alien4cloud.model.cloud.StorageTemplate;
import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.components.ConcatPropertyValue;
import alien4cloud.model.components.DeploymentArtifact;
import alien4cloud.model.components.FunctionPropertyValue;
import alien4cloud.model.components.IArtifact;
import alien4cloud.model.components.IValue;
import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.model.components.Interface;
import alien4cloud.model.components.Operation;
import alien4cloud.model.components.OperationOutput;
import alien4cloud.model.components.PropertyDefinition;
import alien4cloud.model.components.ScalarPropertyValue;
import alien4cloud.paas.IPaaSTemplate;
import alien4cloud.paas.cloudify3.configuration.MappingConfiguration;
import alien4cloud.paas.cloudify3.configuration.ProviderMappingConfiguration;
import alien4cloud.paas.cloudify3.error.BadConfigurationException;
import alien4cloud.paas.cloudify3.service.model.CloudifyDeployment;
import alien4cloud.paas.cloudify3.service.model.MatchedPaaSComputeTemplate;
import alien4cloud.paas.cloudify3.service.model.MatchedPaaSTemplate;
import alien4cloud.paas.cloudify3.service.model.NativeType;
import alien4cloud.paas.cloudify3.service.model.OperationWrapper;
import alien4cloud.paas.cloudify3.service.model.Relationship;
import alien4cloud.paas.exception.NotSupportedException;
import alien4cloud.paas.function.FunctionEvaluator;
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
import alien4cloud.utils.FileUtil;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Some utilities method which help transforming an alien deployment to a cloudify deployment
 *
 * @author Minh Khang VU
 */
@AllArgsConstructor
@Slf4j
public class CloudifyDeploymentUtil {

    private MappingConfiguration mappingConfiguration;

    private ProviderMappingConfiguration providerMappingConfiguration;

    private CloudifyDeployment alienDeployment;

    private Path recipePath;

    public boolean mapHasEntries(Map<?, ?> map) {
        return map != null && !map.isEmpty();
    }

    public boolean collectionHasElement(Collection<?> list) {
        return list != null && !list.isEmpty();
    }

    public Map<String, Interface> filterRelationshipSourceInterfaces(Map<String, Interface> interfaces) {
        return filterRelationshipInterfaces(interfaces, true);
    }

    public Map<String, Interface> filterRelationshipTargetInterfaces(Map<String, Interface> interfaces) {
        return filterRelationshipInterfaces(interfaces, false);
    }

    private Map<String, Interface> filterRelationshipInterfaces(Map<String, Interface> interfaces, boolean isSource) {
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

    public boolean operationHasInputParameters(Operation operation) {
        Map<String, IValue> inputParameters = operation.getInputParameters();
        return inputParameters != null && !inputParameters.isEmpty();
    }

    public boolean operationHasInputParameters(String interfaceName, Operation operation) {
        return isStandardLifecycleInterface(interfaceName) && operationHasInputParameters(operation);
    }

    public Map<String, Interface> getNodeInterfaces(PaaSNodeTemplate node) {
        return getInterfaces(node.getIndexedToscaElement().getInterfaces());
    }

    public Map<String, Interface> getRelationshipInterfaces(PaaSRelationshipTemplate relationship) {
        return getInterfaces(relationship.getIndexedToscaElement().getInterfaces());
    }

    /**
     * Extract interfaces that have implemented operations
     *
     * @param allInterfaces all interfaces
     * @return interfaces that have implemented operations
     */
    private Map<String, Interface> getInterfaces(Map<String, Interface> allInterfaces) {
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

    private String resolveKeyWordInNodeFunction(PaaSNodeTemplate node, FunctionPropertyValue functionPropertyValue) {
        String nodeName = functionPropertyValue.getTemplateName();
        if (ToscaFunctionConstants.HOST.equals(nodeName)) {
            // Resolve HOST
            PaaSNodeTemplate host = node.getParent() != null ? node.getParent() : node;
            nodeName = host.getId();
        } else if (ToscaFunctionConstants.SELF.equals(nodeName)) {
            nodeName = node.getId();
        }
        return nodeName;
    }

    private String resolveKeyWordInRelationshipFunction(PaaSRelationshipTemplate relationship, FunctionPropertyValue functionPropertyValue) {
        String nodeName = functionPropertyValue.getTemplateName();
        // SOURCE and TARGET
        if (ToscaFunctionConstants.SOURCE.equals(nodeName)) {
            nodeName = relationship.getSource();
        } else if (ToscaFunctionConstants.TARGET.equals(nodeName)) {
            nodeName = relationship.getRelationshipTemplate().getTarget();
        }
        return nodeName;
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

    public Map<String, IValue> getNodeAttributes(PaaSNodeTemplate nodeTemplate) {
        if (MapUtils.isEmpty(nodeTemplate.getIndexedToscaElement().getAttributes())) {
            return null;
        }
        Map<String, IValue> attributesThatCanBeSet = Maps.newHashMap();
        for (Map.Entry<String, IValue> attributeEntry : nodeTemplate.getIndexedToscaElement().getAttributes().entrySet()) {
            if (attributeEntry.getValue() instanceof ScalarPropertyValue || attributeEntry.getValue() instanceof FunctionPropertyValue
                    || attributeEntry.getValue() instanceof ConcatPropertyValue) {
                attributesThatCanBeSet.put(attributeEntry.getKey(), attributeEntry.getValue());
            }
        }
        return attributesThatCanBeSet;
    }

    public PaaSNodeTemplate getSourceNode(PaaSRelationshipTemplate relationshipTemplate) {
        return alienDeployment.getAllNodes().get(relationshipTemplate.getSource());
    }

    public PaaSNodeTemplate getTargetNode(PaaSRelationshipTemplate relationshipTemplate) {
        return alienDeployment.getAllNodes().get(relationshipTemplate.getRelationshipTemplate().getTarget());
    }

    public Map<String, IValue> getSourceRelationshipAttributes(PaaSRelationshipTemplate owner) {
        return getNodeAttributes(getSourceNode(owner));
    }

    public Map<String, IValue> getTargetRelationshipAttributes(PaaSRelationshipTemplate owner) {
        return getNodeAttributes(getTargetNode(owner));
    }

    public boolean isFunctionPropertyValue(IValue input) {
        return input instanceof FunctionPropertyValue;
    }

    public boolean isConcatPropertyValue(IValue input) {
        return input instanceof ConcatPropertyValue;
    }

    public String formatConcatPropertyValue(IPaaSTemplate<?> owner, ConcatPropertyValue concatPropertyValue) {
        return formatConcatPropertyValue("", owner, concatPropertyValue);
    }

    public String formatConcatPropertyValue(String context, IPaaSTemplate<?> owner, ConcatPropertyValue concatPropertyValue) {
        StringBuilder pythonCall = new StringBuilder();
        if (concatPropertyValue.getParameters() == null || concatPropertyValue.getParameters().isEmpty()) {
            throw new InvalidArgumentException("Parameter list for concat function is empty");
        }
        for (IValue concatParam : concatPropertyValue.getParameters()) {
            // scalar type
            if (concatParam instanceof ScalarPropertyValue) {
                // scalar case
                String value = ((ScalarPropertyValue) concatParam).getValue();
                if (StringUtils.isNotEmpty(value)) {
                    pythonCall.append("\"").append(value).append("\" + ");
                }
            } else if (concatParam instanceof PropertyDefinition) {
                throw new NotSupportedException("Do not support property definition in a concat");
            } else if (concatParam instanceof FunctionPropertyValue) {
                // Function case
                FunctionPropertyValue functionPropertyValue = (FunctionPropertyValue) concatParam;
                switch (functionPropertyValue.getFunction()) {
                case ToscaFunctionConstants.GET_ATTRIBUTE:
                    pythonCall.append(formatFunctionPropertyValue(context, owner, functionPropertyValue)).append(" + ");
                    break;
                case ToscaFunctionConstants.GET_PROPERTY:
                    pythonCall.append(formatFunctionPropertyValue(context, owner, functionPropertyValue)).append(" + ");
                    break;
                case ToscaFunctionConstants.GET_OPERATION_OUTPUT:
                    pythonCall.append(formatFunctionPropertyValue(context, owner, functionPropertyValue)).append(" + ");
                    break;
                default:
                    throw new NotSupportedException("Function " + functionPropertyValue.getFunction() + " is not yet supported");
                }
            }
        }
        // Remove the last " + "
        pythonCall.setLength(pythonCall.length() - 3);
        return pythonCall.toString();
    }

    public String formatFunctionPropertyValue(IPaaSTemplate<?> owner, FunctionPropertyValue functionPropertyValue) {
        return formatFunctionPropertyValue("", owner, functionPropertyValue);
    }

    public String formatFunctionPropertyValue(String context, IPaaSTemplate<?> owner, FunctionPropertyValue functionPropertyValue) {
        if (owner instanceof PaaSNodeTemplate) {
            return formatNodeFunctionPropertyValue(context, (PaaSNodeTemplate) owner, functionPropertyValue);
        } else if (owner instanceof PaaSRelationshipTemplate) {
            return formatRelationshipFunctionPropertyValue(context, (PaaSRelationshipTemplate) owner, functionPropertyValue);
        } else {
            throw new NotSupportedException("Un-managed paaS template type " + owner.getClass().getSimpleName());
        }
    }

    public FunctionPropertyValue processNodeOperationInputFunction(PaaSNodeTemplate node, FunctionPropertyValue functionPropertyValue) {
        String concreteNode = resolveKeyWordInNodeFunction(node, functionPropertyValue);
        String concreteNodeWithAttribute = getNodeNameHasPropertyOrAttribute(node.getId(), alienDeployment.getAllNodes().get(concreteNode),
                functionPropertyValue.getElementNameToFetch(), functionPropertyValue.getFunction());
        return resolvePropertyMappingInFunction(concreteNodeWithAttribute, functionPropertyValue);
    }

    public String formatNodeFunctionPropertyValue(PaaSNodeTemplate node, FunctionPropertyValue functionPropertyValue) {
        return formatNodeFunctionPropertyValue("", node, functionPropertyValue);
    }

    /**
     * Format operation parameter of a node
     *
     * @param node the node which contains the function property value
     * @param functionPropertyValue the input which can be a function or a scalar
     * @return the formatted parameter understandable by Cloudify 3
     */
    public String formatNodeFunctionPropertyValue(String context, PaaSNodeTemplate node, FunctionPropertyValue functionPropertyValue) {
        String concreteNode = resolveKeyWordInNodeFunction(node, functionPropertyValue);
        String concreteNodeWithAttribute = getNodeNameHasPropertyOrAttribute(node.getId(), alienDeployment.getAllNodes().get(concreteNode),
                functionPropertyValue.getElementNameToFetch(), functionPropertyValue.getFunction());
        functionPropertyValue = resolvePropertyMappingInFunction(concreteNodeWithAttribute, functionPropertyValue);
        if (ToscaFunctionConstants.GET_ATTRIBUTE.equals(functionPropertyValue.getFunction())) {
            return "get_attribute(ctx" + context + ", '" + functionPropertyValue.getElementNameToFetch() + "')";
        } else if (ToscaFunctionConstants.GET_PROPERTY.equals(functionPropertyValue.getFunction())) {
            return "get_property(ctx" + context + ", '" + functionPropertyValue.getElementNameToFetch() + "')";
        } else if (ToscaFunctionConstants.GET_OPERATION_OUTPUT.equals(functionPropertyValue.getFunction())) {
            // a fake attribute is used in order to handle Operation Outputs
            return "get_attribute(ctx" + context + ", '_a4c_OO:" + functionPropertyValue.getInterfaceName() + ':' + functionPropertyValue.getOperationName()
                    + ":"
                    + functionPropertyValue.getElementNameToFetch() + "')";
        } else {
            // throw new NotSupportedException("Function " + functionPropertyValue.getFunction() + " is not supported");
            log.warn(("Function " + functionPropertyValue.getFunction() + " is not supported"));
            return "'Not supported'";
        }
    }

    public String formatRelationshipFunctionPropertyValue(PaaSRelationshipTemplate relationship, FunctionPropertyValue functionPropertyValue) {
        return formatRelationshipFunctionPropertyValue("", relationship, functionPropertyValue);
    }

    /**
     * Format operation parameter of a node
     *
     * @param relationship the relationship which contains the function property value
     * @param functionPropertyValue the input which can be a function or a scalar
     * @return the formatted parameter understandable by Cloudify 3
     */
    public String formatRelationshipFunctionPropertyValue(String context, PaaSRelationshipTemplate relationship, FunctionPropertyValue functionPropertyValue) {
        String concreteNode = resolveKeyWordInRelationshipFunction(relationship, functionPropertyValue);
        String concreteNodeWithAttribute = getNodeNameHasPropertyOrAttribute(relationship.getId(), alienDeployment.getAllNodes().get(concreteNode),
                functionPropertyValue.getElementNameToFetch(), functionPropertyValue.getFunction());
        functionPropertyValue = resolvePropertyMappingInFunction(concreteNodeWithAttribute, functionPropertyValue);
        if (ToscaFunctionConstants.GET_ATTRIBUTE.equals(functionPropertyValue.getFunction())) {
            return "get_attribute(ctx." + functionPropertyValue.getTemplateName().toLowerCase() + context + ", '"
                    + functionPropertyValue.getElementNameToFetch() + "')";
        } else if (ToscaFunctionConstants.GET_PROPERTY.equals(functionPropertyValue.getFunction())) {
            return "get_property(ctx." + functionPropertyValue.getTemplateName().toLowerCase() + context + ", '"
                    + functionPropertyValue.getElementNameToFetch() + "')";
        } else if (ToscaFunctionConstants.GET_OPERATION_OUTPUT.equals(functionPropertyValue.getFunction())) {
            return "get_attribute(ctx" + functionPropertyValue.getTemplateName().toLowerCase() + context + ", '_a4c_OO:"
                    + functionPropertyValue.getInterfaceName() + ':' + functionPropertyValue.getOperationName() + ":"
                    + functionPropertyValue.getElementNameToFetch() + "')";
        } else {
            // throw new NotSupportedException("Function " + functionPropertyValue.getFunction() + " is not supported");
            log.warn(("Function " + functionPropertyValue.getFunction() + " is not supported"));
            return "'Not supported'";
        }
    }

    private FunctionPropertyValue resolvePropertyMappingInFunction(String realNodeName, FunctionPropertyValue functionPropertyValue) {
        FunctionPropertyValue resolved = new FunctionPropertyValue(functionPropertyValue.getFunction(), Lists.newArrayList(functionPropertyValue
                .getParameters()));
        String nativeType = getNativeType(realNodeName);
        for (int i = 1; i < resolved.getParameters().size(); i++) {
            String attributeName = resolved.getParameters().get(i);
            resolved.getParameters().set(i, mapPropAttName(functionPropertyValue.getFunction(), attributeName, nativeType));
        }
        return resolved;
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

    public boolean isStandardLifecycleInterface(String interfaceName) {
        return ToscaNodeLifecycleConstants.STANDARD.equals(interfaceName) || ToscaNodeLifecycleConstants.STANDARD_SHORT.equals(interfaceName);
    }

    public String tryToMapToCloudifyInterface(String interfaceName) {
        if (isStandardLifecycleInterface(interfaceName)) {
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
                String volumeIdValue = FunctionEvaluator.getScalarValue(volumeProperties.get(NormativeBlockStorageConstants.VOLUME_ID));
                if (volumeIdValue != null) {
                    if (volumeIdValue.contains(AlienConstants.STORAGE_AZ_VOLUMEID_SEPARATOR)) {
                        String[] volumeIdValueTokens = volumeIdValue.split(AlienConstants.STORAGE_AZ_VOLUMEID_SEPARATOR);
                        if (volumeIdValueTokens.length != 2) {
                            // TODO Manage the case when we want to reuse a volume, must take into account the fact it can contain also availability zone
                            // TODO And it can have multiple volumes if it's scaled
                            throw new InvalidArgumentException("Volume id is not in good format");
                        } else {
                            return volumeIdValueTokens[1];
                        }
                    } else {
                        return volumeIdValue;
                    }
                }
            }
            return null;
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
                new Relationship(relationshipTemplate.getId(), relationshipTemplate.getSource(), relationshipTemplate.getRelationshipTemplate().getTarget()));
    }

    public List<Relationship> getAllRelationshipWithDeploymentArtifacts(PaaSNodeTemplate nodeTemplate) {
        List<Relationship> relationships = Lists.newArrayList();
        for (Relationship relationship : alienDeployment.getAllRelationshipDeploymentArtifacts().keySet()) {
            if (relationship.getTarget().equals(nodeTemplate.getId())) {
                relationships.add(relationship);
            }
        }
        return relationships;
    }

    public String getArtifactPath(String nodeId, String artifactId, IArtifact artifact) {
        Map<String, DeploymentArtifact> topologyArtifacts = alienDeployment.getAllNodes().get(nodeId).getNodeTemplate().getArtifacts();
        IArtifact topologyArtifact = topologyArtifacts != null ? topologyArtifacts.get(artifactId) : null;
        if (topologyArtifact != null && ArtifactRepositoryConstants.ALIEN_ARTIFACT_REPOSITORY.equals(topologyArtifact.getArtifactRepository())) {
            // Overidden in the topology
            return mappingConfiguration.getTopologyArtifactDirectoryName() + "/" + nodeId + "/" + artifact.getArchiveName() + "/" + artifact.getArtifactRef();
        } else {
            return getArtifactRelativePath(artifact);
        }
    }

    public boolean isArtifactDirectory(String artifactPath) {
        return Files.isDirectory(recipePath.resolve(artifactPath));
    }

    public Map<String, String> listArtifactDirectory(final String artifactPath) throws IOException {
        final Map<String, String> children = Maps.newHashMap();
        final Path realArtifactPath = recipePath.resolve(artifactPath);
        Files.walkFileTree(realArtifactPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String relativePath = FileUtil.getChildEntryRelativePath(realArtifactPath, file, true);
                String absolutePath = FileUtil.getChildEntryRelativePath(recipePath, file, true);
                children.put(relativePath, absolutePath);
                return super.visitFile(file, attrs);
            }
        });
        return children;
    }

    public String getRelationshipArtifactPath(String sourceId, String relationshipId, String artifactId, IArtifact artifact) {
        Map<String, DeploymentArtifact> topologyArtifacts = alienDeployment.getAllNodes().get(sourceId).getRelationshipTemplate(relationshipId, sourceId)
                .getRelationshipTemplate().getArtifacts();
        IArtifact topologyArtifact = topologyArtifacts != null ? topologyArtifacts.get(artifactId) : null;
        if (topologyArtifact != null && ArtifactRepositoryConstants.ALIEN_ARTIFACT_REPOSITORY.equals(topologyArtifact.getArtifactRepository())) {
            // Overridden in the topology
            return mappingConfiguration.getTopologyArtifactDirectoryName() + "/" + sourceId + "/" + artifact.getArchiveName() + "/" + artifact.getArtifactRef();
        } else {
            return getArtifactRelativePath(artifact);
        }
    }

    public String formatVolumeSize(Long size) {
        if (size == null) {
            throw new IllegalArgumentException("Volume size is required");
        }
        long sizeInGib = size / (1024L * 1024L * 1024L);
        if (sizeInGib <= 0) {
            sizeInGib = 1;
        }
        return String.valueOf(sizeInGib);
    }

    /**
     * Get the volume's availability zone from the compute (in the same zone)
     * 
     * @param matchedVolume the matched volume
     * @return the availability zone, null if not defined in the parent compute
     */
    public String getVolumeAvailabilityZone(MatchedPaaSTemplate<StorageTemplate> matchedVolume) {
        PaaSNodeTemplate compute = matchedVolume.getPaaSNodeTemplate().getParent();
        if (compute == null) {
            return null;
        }
        MatchedPaaSComputeTemplate matchedPaaSComputeTemplate = alienDeployment.getComputesMap().get(compute.getId());
        if (matchedPaaSComputeTemplate == null) {
            return null;
        }
        return matchedPaaSComputeTemplate.getPaaSComputeTemplate().getAvailabilityZone();
    }

    public String getArtifactRelativePath(IArtifact artifact) {
        return artifact.getArchiveName() + "/" + artifact.getArtifactRef();
    }

    public String getArtifactWrapperPath(IPaaSTemplate<?> owner, String interfaceName, String operationName, IArtifact artifact) {
        String artifactCopiedPath = getArtifactRelativePath(artifact);
        int lastSlashIndex = artifactCopiedPath.lastIndexOf('/');
        String fileName = artifactCopiedPath.substring(lastSlashIndex + 1);
        String parent = artifactCopiedPath.substring(0, lastSlashIndex);
        String wrapperPath = parent + "/" + mappingConfiguration.getGeneratedArtifactPrefix() + "_" + fileName.substring(0, fileName.lastIndexOf('.')) + ".py";
        if (owner instanceof PaaSNodeTemplate) {
            PaaSNodeTemplate ownerNode = (PaaSNodeTemplate) owner;
            return ownerNode.getId() + "/" + interfaceName + "/" + operationName + "/" + wrapperPath;
        } else if (owner instanceof PaaSRelationshipTemplate) {
            PaaSRelationshipTemplate ownerRelationship = (PaaSRelationshipTemplate) owner;
            return ownerRelationship.getSource() + "_" + ownerRelationship.getRelationshipTemplate().getTarget() + "/" + ownerRelationship.getId() + "/"
                    + wrapperPath;
        } else {
            throw new NotSupportedException("Not supported template type " + owner.getId());
        }
    }

    public boolean operationHasDeploymentArtifacts(OperationWrapper operationWrapper) {
        return MapUtils.isNotEmpty(operationWrapper.getAllDeploymentArtifacts())
                || MapUtils.isNotEmpty(operationWrapper.getAllRelationshipDeploymentArtifacts());
    }

    public String formatTextWithIndentation(int spaceNumber, String text) {
        String[] lines = text.split("\n");
        StringBuilder formattedTextBuffer = new StringBuilder();
        StringBuilder indentationBuffer = new StringBuilder();
        for (int i = 0; i < spaceNumber; i++) {
            indentationBuffer.append(" ");
        }
        String indentation = indentationBuffer.toString();
        for (String line : lines) {
            formattedTextBuffer.append(indentation).append(line).append("\n");
        }
        return formattedTextBuffer.toString();
    }

    public String getOperationOutputNames(Operation operation) {
        if (operation.getOutputs() != null && !operation.getOutputs().isEmpty()) {
            StringBuilder result = new StringBuilder();
            for (OperationOutput operationOutput : operation.getOutputs()) {
                if (result.length() > 0) {
                    result.append(";");
                }
                result.append(operationOutput.getName());
            }
            return result.toString();
        } else {
            return null;
        }
    }
}
