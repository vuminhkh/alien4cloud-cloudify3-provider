package alien4cloud.paas.cloudify3.util;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import alien4cloud.model.components.IndexedArtifactToscaElement;
import alien4cloud.model.components.Interface;
import alien4cloud.model.components.Operation;
import alien4cloud.paas.cloudify3.service.model.ProviderMappingConfiguration;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;
import alien4cloud.paas.plan.ToscaNodeLifecycleConstants;
import alien4cloud.paas.plan.ToscaRelationshipLifecycleConstants;

import com.google.common.collect.Lists;

/**
 * Some utilities method which help generating cloudify 3 blueprint
 * 
 * @author Minh Khang VU
 */
@AllArgsConstructor
public class BlueprintGenerationUtil {

    private ProviderMappingConfiguration mappingConfiguration;

    public boolean mapHasEntries(Map<?, ?> map) {
        return map != null && !map.isEmpty();
    }

    public boolean collectionHasElement(Collection<?> list) {
        return list != null && !list.isEmpty();
    }

    public boolean typeHasInterfaces(IndexedArtifactToscaElement type) {
        Map<String, Interface> interfaces = type.getInterfaces();
        if (interfaces == null || interfaces.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, Interface> interfaceEntry : interfaces.entrySet()) {
            if (interfaceEntry.getValue() != null && interfaceEntry.getValue().getOperations() != null && !interfaceEntry.getValue().getOperations().isEmpty()) {
                Map<String, Operation> operations = interfaceEntry.getValue().getOperations();
                for (Map.Entry<String, Operation> operationEntry : operations.entrySet()) {
                    if (operationEntry.getValue() != null && operationEntry.getValue().getImplementationArtifact() != null
                            && operationEntry.getValue().getImplementationArtifact().getArtifactRef() != null) {
                        return true;
                    }
                }
            }
        }
        return false;
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

    public boolean typeMustBeMappedToCloudifyType(String toscaType) {
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

    public String tryToMapToCloudifyRelationshipInterfaceOperation(String operationName) {
        if (ToscaRelationshipLifecycleConstants.POST_CONFIGURE_SOURCE.equals(operationName)) {
            return "postconfigure";
        }
        if (ToscaRelationshipLifecycleConstants.PRE_CONFIGURE_SOURCE.equals(operationName)) {
            return "preconfigure";
        }
        return operationName;
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
