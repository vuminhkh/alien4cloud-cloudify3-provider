package alien4cloud.paas.cloudify3.blueprint;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.paas.cloudify3.configuration.MappingConfiguration;
import alien4cloud.paas.cloudify3.configuration.ProviderMappingConfiguration;
import alien4cloud.paas.cloudify3.service.PropertyEvaluatorService;
import alien4cloud.paas.cloudify3.service.model.CloudifyDeployment;
import alien4cloud.paas.cloudify3.service.model.NativeType;

@AllArgsConstructor
public abstract class AbstractGenerationUtil {

    protected MappingConfiguration mappingConfiguration;

    protected ProviderMappingConfiguration providerMappingConfiguration;

    protected CloudifyDeployment alienDeployment;

    protected Path recipePath;

    protected PropertyEvaluatorService propertyEvaluatorService;

    protected boolean typeMustBeMappedToCloudifyType(String toscaType) {
        return mappingConfiguration.getNormativeTypes().containsKey(toscaType);
    }

    protected IndexedNodeType getTypeFromName(String name, List<IndexedNodeType> types) {
        for (IndexedNodeType type : types) {
            if (name.equals(type.getId())) {
                return type;
            }
        }
        return null;
    }

    protected String getNativeType(String id) {
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

    public boolean collectionHasElement(Collection<?> list) {
        return list != null && !list.isEmpty();
    }

    public boolean mapHasEntries(Map<?, ?> map) {
        return map != null && !map.isEmpty();
    }

    public String tryToMapToCloudifyType(String toscaType) {
        String mappedType = mappingConfiguration.getNormativeTypes().get(toscaType);
        return mappedType != null ? mappedType : toscaType;
    }
}
