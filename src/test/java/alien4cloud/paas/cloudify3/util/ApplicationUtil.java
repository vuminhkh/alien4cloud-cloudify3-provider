package alien4cloud.paas.cloudify3.util;

import java.io.IOException;
import java.nio.file.Paths;

import javax.annotation.Resource;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import alien4cloud.application.ApplicationService;
import alien4cloud.dao.ElasticSearchDAO;
import alien4cloud.model.application.Application;
import alien4cloud.model.topology.Topology;
import alien4cloud.tosca.ArchiveParser;
import alien4cloud.tosca.ArchivePostProcessor;
import alien4cloud.tosca.model.ArchiveRoot;
import alien4cloud.tosca.parser.ParsingException;
import alien4cloud.tosca.parser.ParsingResult;

@Component
@Slf4j
public class ApplicationUtil {

    private static final String TOPOLOGIES_PATH = "src/test/resources/topologies/";

    @Resource
    private ApplicationService applicationService;

    @Resource
    protected ElasticSearchDAO alienDAO;

    @Resource
    private ArchiveParser parser;

    @Resource
    private ArchivePostProcessor postProcessor;

    @SneakyThrows
    public Topology createAlienApplication(String applicationName, String topologyFileName) {
        Topology topology = parseYamlTopology(topologyFileName);
        String applicationId = applicationService.create("alien", applicationName, null, null);
        topology.setDelegateId(applicationId);
        topology.setDelegateType(Application.class.getSimpleName().toLowerCase());
        alienDAO.save(topology);
        return topology;
    }

    private Topology parseYamlTopology(String topologyFileName) throws IOException, ParsingException {
        ParsingResult<ArchiveRoot> parsingResult = parser.parse(Paths.get(TOPOLOGIES_PATH + topologyFileName + ".yaml"));
        postProcessor.postProcess(parsingResult);
        return parsingResult.getResult().getTopology();
    }
}
