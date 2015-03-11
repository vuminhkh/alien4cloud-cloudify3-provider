package alien4cloud.paas.cloudify3;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.junit.Before;
import org.junit.BeforeClass;

import alien4cloud.model.application.DeploymentSetup;
import alien4cloud.model.cloud.CloudImage;
import alien4cloud.model.cloud.CloudImageFlavor;
import alien4cloud.model.cloud.CloudResourceMatcherConfig;
import alien4cloud.model.cloud.ComputeTemplate;
import alien4cloud.model.cloud.NetworkTemplate;
import alien4cloud.model.cloud.StorageTemplate;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.Topology;
import alien4cloud.paas.cloudify3.configuration.CloudConfiguration;
import alien4cloud.paas.cloudify3.configuration.CloudConfigurationHolder;
import alien4cloud.paas.cloudify3.service.ComputeTemplateMatcherService;
import alien4cloud.paas.cloudify3.service.NetworkMatcherService;
import alien4cloud.paas.cloudify3.service.StorageTemplateMatcherService;
import alien4cloud.paas.cloudify3.util.CSARUtil;
import alien4cloud.tosca.normative.AlienCustomTypes;
import alien4cloud.tosca.normative.NormativeBlockStorageConstants;
import alien4cloud.tosca.normative.NormativeComputeConstants;
import alien4cloud.tosca.normative.NormativeNetworkConstants;
import alien4cloud.utils.FileUtil;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class AbstractTest {

    public static final String SINGLE_COMPUTE_TOPOLOGY = "single_compute";

    public static final String LAMP_TOPOLOGY = "lamp";

    public static final String NETWORK_TOPOLOGY = "network";

    public static final String STORAGE_TOPOLOGY = "storage";

    public static final String DELETABLE_STORAGE_TOPOLOGY = "deletable_storage";

    public static final String TOMCAT_TOPOLOGY = "tomcat";

    private ComputeTemplate computeTemplate = new ComputeTemplate("alien_image", "alien_flavor");

    private NetworkTemplate network = new NetworkTemplate("net-pub", 4, true, null, null);

    private NetworkTemplate internalNetwork = new NetworkTemplate("internal-network", 4, false, "192.168.1.0/24", "192.168.1.1");

    private StorageTemplate storageTemplate = new StorageTemplate("small", 1L, "/dev/vdb");

    @Resource
    private CloudConfigurationHolder cloudConfigurationHolder;

    @Resource
    private ComputeTemplateMatcherService computeTemplateMatcherService;

    @Resource
    private NetworkMatcherService networkMatcherService;

    @Resource
    private StorageTemplateMatcherService storageTemplateMatcherService;

    private static boolean isInitialized = false;

    @Resource
    private CSARUtil csarUtil;

    @BeforeClass
    public static void cleanup() throws IOException {
        FileUtil.delete(CSARUtil.ARTIFACTS_DIRECTORY);
    }

    @Before
    public void before() throws Exception {
        if (!isInitialized) {
            isInitialized = true;
        } else {
            return;
        }
        CloudConfiguration cloudConfiguration = new CloudConfiguration();
        String cloudifyURL = System.getenv("CLOUDIFY_URL");
        if (cloudifyURL == null) {
            cloudifyURL = "http://129.185.67.114:8100";
        }
        cloudConfiguration.setUrl(cloudifyURL);
        cloudConfigurationHolder.setConfiguration(cloudConfiguration);
        CloudResourceMatcherConfig matcherConfig = new CloudResourceMatcherConfig();

        Map<CloudImage, String> imageMapping = Maps.newHashMap();
        CloudImage cloudImage = new CloudImage();
        cloudImage.setId("alien_image");
        imageMapping.put(cloudImage, "727df994-2e1b-404e-9276-b248223a835d");
        matcherConfig.setImageMapping(imageMapping);

        Map<CloudImageFlavor, String> flavorMapping = Maps.newHashMap();
        flavorMapping.put(new CloudImageFlavor("alien_flavor", 1, 2L, 3L), "2");
        matcherConfig.setFlavorMapping(flavorMapping);

        Map<NetworkTemplate, String> networkMapping = Maps.newHashMap();
        networkMapping.put(network, "net-pub");
        networkMapping.put(internalNetwork, "internal-network");
        matcherConfig.setNetworkMapping(networkMapping);

        Map<StorageTemplate, String> storageMapping = Maps.newHashMap();
        storageMapping.put(storageTemplate, null);
        matcherConfig.setStorageMapping(storageMapping);

        computeTemplateMatcherService.configure(matcherConfig.getImageMapping(), matcherConfig.getFlavorMapping());
        networkMatcherService.configure(matcherConfig.getNetworkMapping());
        storageTemplateMatcherService.configure(storageMapping);
        csarUtil.uploadAll();
    }

    protected DeploymentSetup generateDeploymentSetup(Topology topology) {
        List<String> nodeIds = Lists.newArrayList();
        List<String> networkIds = Lists.newArrayList();
        List<String> blockStorageIds = Lists.newArrayList();
        for (Map.Entry<String, NodeTemplate> nodeTemplateEntry : topology.getNodeTemplates().entrySet()) {
            if (NormativeComputeConstants.COMPUTE_TYPE.equals(nodeTemplateEntry.getValue().getType())) {
                nodeIds.add(nodeTemplateEntry.getKey());
            }
            if (NormativeNetworkConstants.NETWORK_TYPE.equals(nodeTemplateEntry.getValue().getType())) {
                networkIds.add(nodeTemplateEntry.getKey());
            }
            if (NormativeBlockStorageConstants.BLOCKSTORAGE_TYPE.equals(nodeTemplateEntry.getValue().getType())
                    || AlienCustomTypes.DELETABLE_BLOCKSTORAGE_TYPE.equals(nodeTemplateEntry.getValue().getType())) {
                blockStorageIds.add(nodeTemplateEntry.getKey());
            }
        }
        DeploymentSetup deploymentSetup = new DeploymentSetup();

        Map<String, ComputeTemplate> resourcesMapping = Maps.newHashMap();
        deploymentSetup.setCloudResourcesMapping(resourcesMapping);
        for (String nodeId : nodeIds) {
            resourcesMapping.put(nodeId, computeTemplate);
        }

        Map<String, NetworkTemplate> networkMapping = Maps.newHashMap();
        deploymentSetup.setNetworkMapping(networkMapping);
        for (String networkId : networkIds) {
            switch (networkId) {
            case "NetPub":
                networkMapping.put(networkId, network);
                break;
            case "InternalNetwork":
                networkMapping.put(networkId, internalNetwork);
                break;
            default:
                throw new RuntimeException("Not configured " + networkId);
            }
        }

        Map<String, StorageTemplate> storageMapping = Maps.newHashMap();
        for (String storageId : blockStorageIds) {
            storageMapping.put(storageId, storageTemplate);
        }
        deploymentSetup.setStorageMapping(storageMapping);
        return deploymentSetup;
    }
}
