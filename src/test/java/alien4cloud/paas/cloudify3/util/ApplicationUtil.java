package alien4cloud.paas.cloudify3.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.annotation.Resource;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.stereotype.Component;

import alien4cloud.application.ApplicationService;
import alien4cloud.dao.ElasticSearchDAO;
import alien4cloud.model.application.Application;
import alien4cloud.model.topology.Topology;
import alien4cloud.tosca.ArchiveParser;
import alien4cloud.tosca.model.ArchiveRoot;
import alien4cloud.tosca.parser.ParsingException;
import alien4cloud.tosca.parser.ParsingResult;
import alien4cloud.utils.FileUtil;

@Component
@Slf4j
public class ApplicationUtil {
    private static final String TOPOLOGIES_PATH = "src/test/resources/topologies/" + LocationUtil.getType() + "/";

    @Resource
    private ApplicationService applicationService;

    @Resource
    protected ElasticSearchDAO alienDAO;

    @Resource
    private ArchiveParser parser;

    @SneakyThrows
    public Topology createAlienApplication(String applicationName, String topologyFileName) {
        Application application = alienDAO.customFind(Application.class, QueryBuilders.termQuery("name", applicationName));
        if (application != null) {
            applicationService.delete(application.getId());
        }
        Topology topology = parseYamlTopology(topologyFileName);
        String applicationId = applicationService.create("alien", applicationName, null, null);
        topology.setDelegateId(applicationId);
        topology.setDelegateType(Application.class.getSimpleName().toLowerCase());
        alienDAO.save(topology);
        return topology;
    }

    private Topology parseYamlTopology(String topologyFileName) throws IOException, ParsingException {
        Path zipPath = Files.createTempFile("csar", ".zip");
        FileUtil.zip(Paths.get(TOPOLOGIES_PATH + topologyFileName + ".yaml"), zipPath);
        ParsingResult<ArchiveRoot> parsingResult = parser.parse(zipPath);
        return parsingResult.getResult().getTopology();
    }
}
