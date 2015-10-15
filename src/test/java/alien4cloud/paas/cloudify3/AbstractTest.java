package alien4cloud.paas.cloudify3;

import java.io.IOException;
import java.util.List;

import javax.annotation.Resource;

import org.junit.Before;
import org.junit.BeforeClass;
import org.springframework.beans.factory.annotation.Value;

import alien4cloud.it.Context;
import alien4cloud.model.deployment.DeploymentTopology;
import alien4cloud.model.topology.Topology;
import alien4cloud.orchestrators.plugin.model.PluginArchive;
import alien4cloud.paas.cloudify3.configuration.CloudConfiguration;
import alien4cloud.paas.cloudify3.configuration.CloudConfigurationHolder;
import alien4cloud.paas.cloudify3.location.OpenstackLocationConfigurator;
import alien4cloud.paas.cloudify3.util.CSARUtil;
import alien4cloud.tosca.ArchiveIndexer;
import alien4cloud.tosca.parser.ParsingError;
import alien4cloud.utils.FileUtil;

import com.google.common.collect.Lists;

public class AbstractTest {

    public static final String SINGLE_COMPUTE_TOPOLOGY = "single_compute";

    public static final String NETWORK_TOPOLOGY = "network";

    public static final String STORAGE_TOPOLOGY = "storage";

    @Value("${cloudify3.externalNetworkName}")
    private String externalNetworkName;

    @Value("${cloudify3.imageId}")
    private String imageId;

    @Resource
    private CloudConfigurationHolder cloudConfigurationHolder;

    private static boolean isInitialized = false;

    @Resource
    private CSARUtil csarUtil;

    @Resource
    private OpenstackLocationConfigurator openstackLocationConfigurator;

    @Resource
    private ArchiveIndexer archiveIndexer;

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
            cloudifyURL = Context.getInstance().getCloudify3ManagerUrl();
        }
        cloudConfiguration.setUrl(cloudifyURL);
        cloudConfigurationHolder.setConfiguration(cloudConfiguration);
        csarUtil.uploadAll();
        // Reload in order to be sure that the archive is constructed once all dependencies have been uploaded
        openstackLocationConfigurator.postConstruct();
        List<ParsingError> parsingErrors = Lists.newArrayList();
        for (PluginArchive pluginArchive : openstackLocationConfigurator.pluginArchives()) {
            // index the archive in alien catalog
            archiveIndexer.importArchive(pluginArchive.getArchive(), pluginArchive.getArchiveFilePath(), parsingErrors);
        }
    }

    protected DeploymentTopology generateDeploymentSetup(Topology topology) {
        DeploymentTopology deploymentTopology = new DeploymentTopology();
        return deploymentTopology;
    }
}
