package alien4cloud.paas.cloudify3.blueprint;

import java.nio.file.Path;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.paas.cloudify3.configuration.MappingConfiguration;
import alien4cloud.paas.cloudify3.configuration.ProviderMappingConfiguration;
import alien4cloud.paas.cloudify3.service.PropertyEvaluatorService;
import alien4cloud.paas.cloudify3.service.model.CloudifyDeployment;
import alien4cloud.paas.cloudify3.service.model.MatchedPaaSTemplate;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.tosca.normative.NormativeNetworkConstants;

import com.google.common.collect.Lists;

public class NetworkGenerationUtil extends NativeTypeGenerationUtil {

    public NetworkGenerationUtil(MappingConfiguration mappingConfiguration, ProviderMappingConfiguration providerMappingConfiguration,
            CloudifyDeployment alienDeployment, Path recipePath, PropertyEvaluatorService propertyEvaluatorService) {
        super(mappingConfiguration, providerMappingConfiguration, alienDeployment, recipePath, propertyEvaluatorService);
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
                    return "\"\"";
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

    public String tryToMapNetworkType(IndexedNodeType type, String defaultType) {
        return getMappedNativeType(type, NormativeNetworkConstants.NETWORK_TYPE, providerMappingConfiguration.getNativeTypes().getNetworkType(),
                alienDeployment.getNetworkTypes(), defaultType);
    }

    public String tryToMapNetworkTypeDerivedFrom(IndexedNodeType type) {
        return getMappedNativeDerivedFromType(type, NormativeNetworkConstants.NETWORK_TYPE, providerMappingConfiguration.getNativeTypes().getNetworkType(),
                alienDeployment.getNetworkTypes());
    }
}
