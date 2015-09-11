package alien4cloud.paas.cloudify3.blueprint;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import alien4cloud.common.AlienConstants;
import alien4cloud.exception.InvalidArgumentException;
import alien4cloud.model.cloud.StorageTemplate;
import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.paas.cloudify3.CloudifyPaaSProviderFactory;
import alien4cloud.paas.cloudify3.configuration.MappingConfiguration;
import alien4cloud.paas.cloudify3.configuration.ProviderMappingConfiguration;
import alien4cloud.paas.cloudify3.service.PropertyEvaluatorService;
import alien4cloud.paas.cloudify3.service.model.CloudifyDeployment;
import alien4cloud.paas.cloudify3.service.model.MatchedPaaSComputeTemplate;
import alien4cloud.paas.cloudify3.service.model.MatchedPaaSTemplate;
import alien4cloud.paas.function.FunctionEvaluator;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.tosca.ToscaUtils;
import alien4cloud.tosca.normative.AlienCustomTypes;
import alien4cloud.tosca.normative.NormativeBlockStorageConstants;

import com.google.common.collect.Lists;

public class VolumeGenerationUtil extends NativeTypeGenerationUtil {

    public VolumeGenerationUtil(MappingConfiguration mappingConfiguration, ProviderMappingConfiguration providerMappingConfiguration,
            CloudifyDeployment alienDeployment, Path recipePath, PropertyEvaluatorService propertyEvaluatorService) {
        super(mappingConfiguration, providerMappingConfiguration, alienDeployment, recipePath, propertyEvaluatorService);
    }

    public String tryToMapVolumeType(IndexedNodeType type, String defaultType) {
        return getMappedNativeType(type, NormativeBlockStorageConstants.BLOCKSTORAGE_TYPE, providerMappingConfiguration.getNativeTypes().getVolumeType(),
                alienDeployment.getVolumeTypes(), defaultType);
    }

    public String tryToMapVolumeTypeDerivedFrom(IndexedNodeType type) {
        return getMappedNativeDerivedFromType(type, NormativeBlockStorageConstants.BLOCKSTORAGE_TYPE, providerMappingConfiguration.getNativeTypes()
                .getVolumeType(), alienDeployment.getVolumeTypes());
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
        return _isConfiguredVolume(volumeTemplate);
    }

    public static boolean _isConfiguredVolume(PaaSNodeTemplate volumeTemplate) {
        Map<String, AbstractPropertyValue> volumeProperties = volumeTemplate.getNodeTemplate().getProperties();
        return volumeProperties != null
                && (volumeProperties.containsKey(NormativeBlockStorageConstants.LOCATION) || volumeProperties
                        .containsKey(NormativeBlockStorageConstants.FILE_SYSTEM));
    }

    public boolean isDeletableVolumeType(IndexedNodeType volumeType) {
        return ToscaUtils.isFromType(AlienCustomTypes.DELETABLE_BLOCKSTORAGE_TYPE, volumeType);
    }

    public static String getExternalVolumeId(MatchedPaaSTemplate<StorageTemplate> matchedVolumeTemplate) {
        return _getExternalVolumeId(matchedVolumeTemplate);
    }

    public static String _getExternalVolumeId(MatchedPaaSTemplate<StorageTemplate> matchedVolumeTemplate) {
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

    public PaaSNodeTemplate[] getConfiguredAttachedVolumes(PaaSNodeTemplate node) {
        return _getConfiguredAttachedVolumes(node);
    }

    public static PaaSNodeTemplate[] _getConfiguredAttachedVolumes(PaaSNodeTemplate node) {
        PaaSNodeTemplate host = node.getParent();
        while (host.getParent() != null) {
            host = host.getParent();
        }
        if (CollectionUtils.isEmpty(host.getStorageNodes())) {
            return null;
        }
        List<PaaSNodeTemplate> volumes = Lists.newArrayList();
        for (PaaSNodeTemplate volume : host.getStorageNodes()) {
            if (_isConfiguredVolume(volume)) {
                volumes.add(volume);
            }
        }
        return volumes.isEmpty() ? null : volumes.toArray(new PaaSNodeTemplate[volumes.size()]);
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

    /**
     * @return true if the provider deployment property 'deletable_blockstorage' is true.
     */
    public boolean hasDeletableBlockstorageOptionEnabled(CloudifyDeployment cloudifyDeployment) {
        if (cloudifyDeployment.getProviderDeploymentProperties() != null) {
            String value = cloudifyDeployment.getProviderDeploymentProperties().get(CloudifyPaaSProviderFactory.DELETABLE_BLOCKSTORAGE);
            if (value != null) {
                return Boolean.parseBoolean(value);
            }
        }
        return false;
    }
}
