package alien4cloud.paas.cloudify3;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import javax.annotation.Resource;

import org.junit.Before;
import org.junit.BeforeClass;
import org.springframework.beans.factory.annotation.Value;

import com.google.common.collect.Lists;

import alien4cloud.orchestrators.plugin.model.PluginArchive;
import alien4cloud.paas.cloudify3.configuration.CloudConfigurationHolder;
import alien4cloud.paas.cloudify3.location.AmazonLocationConfigurator;
import alien4cloud.paas.cloudify3.location.ByonLocationConfigurator;
import alien4cloud.paas.cloudify3.location.OpenstackLocationConfigurator;
import alien4cloud.paas.cloudify3.util.CSARUtil;
import alien4cloud.tosca.ArchiveIndexer;
import alien4cloud.tosca.parser.ParsingError;
import alien4cloud.utils.FileUtil;

public class AbstractTest {

    public static final String SCALABLE_COMPUTE_TOPOLOGY = "scalable_compute";

    public static final String SINGLE_COMPUTE_TOPOLOGY = "single_compute";

    public static final String SINGLE_WINDOWS_COMPUTE_TOPOLOGY = "single_windows_compute";

    public static final String NETWORK_TOPOLOGY = "network";

    public static final String STORAGE_TOPOLOGY = "storage";

    public static final String LAMP_TOPOLOGY = "lamp";

    public static final String TOMCAT_TOPOLOGY = "tomcat";

    public static final String ARTIFACT_TEST_TOPOLOGY = "artifact_test";

    @Value("${cloudify3.externalNetworkName}")
    private String externalNetworkName;

    @Value("${cloudify3.imageId}")
    private String imageId;

    @Value("${directories.alien}/${directories.csar_repository}")
    private String repositoryCsarDirectory;

    private static boolean isInitialized = false;

    @Resource
    private CSARUtil csarUtil;

    @Resource
    private OpenstackLocationConfigurator openstackLocationConfigurator;

    @Resource
    private AmazonLocationConfigurator amazonLocationConfigurator;

    @Resource
    private ByonLocationConfigurator byonLocationConfigurator;

    @Resource
    private ArchiveIndexer archiveIndexer;

    @Resource
    private CloudifyOrchestrator cloudifyOrchestrator;

    @Resource
    protected CloudConfigurationHolder cloudConfigurationHolder;

    @BeforeClass
    public static void cleanup() throws IOException {
        FileUtil.delete(CSARUtil.ARTIFACTS_DIRECTORY);
        Path tempPluginDataPath = Paths.get("target/alien/plugin");
        FileUtil.delete(tempPluginDataPath);
        FileUtil.copy(Paths.get("src/main/resources"), tempPluginDataPath);
    }

    @Before
    public void before() throws Exception {
        if (!isInitialized) {
            isInitialized = true;
        } else {
            return;
        }
        FileUtil.delete(Paths.get(repositoryCsarDirectory));
        csarUtil.uploadAll();
        // Reload in order to be sure that the archive is constructed once all dependencies have been uploaded
        List<ParsingError> parsingErrors = Lists.newArrayList();
        for (PluginArchive pluginArchive : cloudifyOrchestrator.pluginArchives()) {
            // index the archive in alien catalog
            archiveIndexer.importArchive(pluginArchive.getArchive(), pluginArchive.getArchiveFilePath(), parsingErrors);
        }
        for (PluginArchive pluginArchive : openstackLocationConfigurator.pluginArchives()) {
            // index the archive in alien catalog
            archiveIndexer.importArchive(pluginArchive.getArchive(), pluginArchive.getArchiveFilePath(), parsingErrors);
        }
        for (PluginArchive pluginArchive : amazonLocationConfigurator.pluginArchives()) {
            // index the archive in alien catalog
            archiveIndexer.importArchive(pluginArchive.getArchive(), pluginArchive.getArchiveFilePath(), parsingErrors);
        }

        for (PluginArchive pluginArchive : byonLocationConfigurator.pluginArchives()) {
            // index the archive in alien catalog
            archiveIndexer.importArchive(pluginArchive.getArchive(), pluginArchive.getArchiveFilePath(), parsingErrors);
        }
        cloudConfigurationHolder.setConfiguration(new CloudifyOrchestratorFactory().getDefaultConfiguration());
    }
}
