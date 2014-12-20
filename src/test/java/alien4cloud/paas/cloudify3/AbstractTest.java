package alien4cloud.paas.cloudify3;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;

import javax.annotation.Resource;

import org.junit.Before;
import org.junit.BeforeClass;

import alien4cloud.model.application.DeploymentSetup;
import alien4cloud.model.cloud.CloudResourceMatcherConfig;
import alien4cloud.model.cloud.ComputeTemplate;
import alien4cloud.model.cloud.MatchedComputeTemplate;
import alien4cloud.paas.cloudify3.configuration.CloudConfigurationHolder;
import alien4cloud.paas.cloudify3.configuration.Flavor;
import alien4cloud.paas.cloudify3.configuration.Image;
import alien4cloud.paas.cloudify3.service.ComputeTemplateMatcherService;
import alien4cloud.paas.cloudify3.util.CSARUtil;
import alien4cloud.utils.FileUtil;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class AbstractTest {

    public static final String SINGLE_COMPUTE_TOPOLOGY = "single_compute";

    public static final String SINGLE_COMPUTE_TOPOLOGY_WITH_APACHE = "single_compute_with_apache";

    private ComputeTemplate computeTemplate = new ComputeTemplate("alien_image", "alien_flavor");

    @Resource
    private CloudConfigurationHolder cloudConfigurationHolder;

    @Resource
    private ComputeTemplateMatcherService computeTemplateMatcherService;

    @Resource
    private CSARUtil csarUtil;

    @BeforeClass
    public static void cleanup() throws IOException {
        FileUtil.delete(CSARUtil.ARTIFACTS_DIRECTORY);
        FileUtil.delete(Paths.get("target/alien"));
    }

    @Before
    public void before() throws Exception {
        cloudConfigurationHolder.getConfiguration().setUrl("http://129.185.67.87:8100");
        cloudConfigurationHolder.getConfiguration().setImages(Sets.newHashSet(new Image("727df994-2e1b-404e-9276-b248223a835d", "Ubuntu Precise")));
        cloudConfigurationHolder.getConfiguration().setFlavors(Sets.newHashSet(new Flavor("2", "Medium")));

        CloudResourceMatcherConfig matcherConfig = new CloudResourceMatcherConfig();
        matcherConfig.setMatchedComputeTemplates(Lists.newArrayList(new MatchedComputeTemplate(computeTemplate, "Medium_Ubuntu_Precise")));
        computeTemplateMatcherService.configure(matcherConfig);

        csarUtil.uploadNormativeTypes();
        csarUtil.uploadApacheTypes();
    }

    protected DeploymentSetup generateDeploymentSetup(Collection<String> nodeIds) {
        DeploymentSetup deploymentSetup = new DeploymentSetup();
        Map<String, ComputeTemplate> resourcesMapping = Maps.newHashMap();
        deploymentSetup.setCloudResourcesMapping(resourcesMapping);
        for (String nodeId : nodeIds) {
            resourcesMapping.put(nodeId, computeTemplate);
        }
        return deploymentSetup;
    }
}
