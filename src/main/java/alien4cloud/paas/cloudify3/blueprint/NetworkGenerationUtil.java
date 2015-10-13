package alien4cloud.paas.cloudify3.blueprint;

import java.nio.file.Path;
import java.util.List;

import com.google.common.collect.Lists;

import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.paas.cloudify3.configuration.MappingConfiguration;
import alien4cloud.paas.cloudify3.configuration.ProviderMappingConfiguration;
import alien4cloud.paas.cloudify3.service.PropertyEvaluatorService;
import alien4cloud.paas.cloudify3.service.model.CloudifyDeployment;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.tosca.ToscaUtils;

public class NetworkGenerationUtil extends NativeTypeGenerationUtil {

    public NetworkGenerationUtil(MappingConfiguration mappingConfiguration, ProviderMappingConfiguration providerMappingConfiguration,
                                 CloudifyDeployment alienDeployment, Path recipePath, PropertyEvaluatorService propertyEvaluatorService) {
        super(mappingConfiguration, providerMappingConfiguration, alienDeployment, recipePath, propertyEvaluatorService);
    }

    private List<PaaSNodeTemplate> getNetworksOfType(PaaSNodeTemplate compute, String networkType) {
        List<PaaSNodeTemplate> computeNetworks = compute.getNetworkNodes();
        List<PaaSNodeTemplate> selectedNetworks = Lists.newArrayList();
        for (PaaSNodeTemplate computeNetwork : computeNetworks) {
            if (ToscaUtils.isFromType(networkType, computeNetwork.getIndexedToscaElement())) {
                selectedNetworks.add(computeNetwork);
            }
        }
        return selectedNetworks;
    }

    public List<PaaSNodeTemplate> getExternalNetworks(PaaSNodeTemplate compute) {
        return getNetworksOfType(compute, "alien.nodes.PublicNetwork");
    }

    public List<PaaSNodeTemplate> getInternalNetworks(PaaSNodeTemplate compute) {
        return getNetworksOfType(compute, "alien.nodes.PrivateNetwork");
    }
}
