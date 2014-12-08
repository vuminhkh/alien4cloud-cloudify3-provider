package alien4cloud.paas.cloudify3.dao;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NodeInstanceDAO extends AbstractDAO {

    public static final String NODE_INSTANCES_PATH = "node-instances";

    @Override
    protected String getPath() {
        return NODE_INSTANCES_PATH;
    }
}
