package alien4cloud.paas.cloudify3.restclient;

import java.util.Calendar;
import java.util.Date;

import javax.xml.bind.DatatypeConverter;

import lombok.extern.slf4j.Slf4j;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.stereotype.Component;

import alien4cloud.paas.cloudify3.model.CloudifyLifeCycle;
import alien4cloud.paas.cloudify3.model.EventType;
import alien4cloud.paas.cloudify3.model.Workflow;

@Component
@Slf4j
public class DeploymentEventClient extends AbstractEventClient {

    protected QueryBuilder createEventsQuery(String executionId, Date timestamp) {
        BoolQueryBuilder eventsQuery = QueryBuilders.boolQuery();
        if (executionId != null && !executionId.isEmpty()) {
            eventsQuery.must(QueryBuilders.matchQuery("context.execution_id", executionId));
        }
        if (timestamp != null) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(timestamp);
            eventsQuery.must(QueryBuilders.rangeQuery("@timestamp").gt(DatatypeConverter.printDateTime(calendar)));
        }
        eventsQuery.must(QueryBuilders.matchQuery("type", "cloudify_event"));

        // instance state query
        BoolQueryBuilder instanceStateQuery = QueryBuilders
                .boolQuery()
                .must(QueryBuilders.boolQuery()
                        .should(QueryBuilders.matchQuery("context.operation", CloudifyLifeCycle.START))
                        .should(QueryBuilders.matchQuery("context.operation", CloudifyLifeCycle.CONFIGURE))
                        .should(QueryBuilders.matchQuery("context.operation", CloudifyLifeCycle.CREATE))
                        .should(QueryBuilders.matchQuery("context.operation", CloudifyLifeCycle.DELETE))
                        .should(QueryBuilders.matchQuery("context.operation", CloudifyLifeCycle.STOP)))
                .must(QueryBuilders.matchQuery("event_type", EventType.TASK_SUCCEEDED));

        // Workflow query
        BoolQueryBuilder workflowQuery = QueryBuilders.boolQuery()
                .mustNot(QueryBuilders.matchQuery("workflow_id", Workflow.CREATE_DEPLOYMENT_ENVIRONMENT))
                .mustNot(QueryBuilders.matchQuery("workflow_id", Workflow.EXECUTE_OPERATION))
                .mustNot(QueryBuilders.matchQuery("workflow_id", Workflow.UNINSTALL))
                .must(QueryBuilders.boolQuery()
                        .should(QueryBuilders.matchQuery("event_type", EventType.WORKFLOW_SUCCEEDED))
                        .should(QueryBuilders.matchQuery("event_type", EventType.WORKFLOW_FAILED))
                        .should(QueryBuilders.boolQuery()
                                .must(QueryBuilders.matchQuery("event_type", EventType.TASK_SUCCEEDED))
                                .must(QueryBuilders.matchQuery("context.workflow_id", Workflow.DELETE_DEPLOYMENT_ENVIRONMENT))
                                .must(QueryBuilders.matchQuery("context.task_name", "riemann_controller.tasks.delete"))));

        // Or instance or workflow query
        eventsQuery.must(QueryBuilders.boolQuery().should(instanceStateQuery).should(workflowQuery));
        return eventsQuery;
    }
}
