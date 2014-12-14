package alien4cloud.paas.cloudify3.dao;

import java.util.Date;
import java.util.Map;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import org.apache.http.HttpHeaders;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import alien4cloud.paas.cloudify3.model.Event;
import alien4cloud.paas.cloudify3.model.GetEventsResult;
import alien4cloud.paas.cloudify3.util.FutureUtil;
import alien4cloud.rest.utils.JsonUtil;

import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ListenableFuture;

@Component
@Slf4j
public class EventDAO extends AbstractDAO {

    public static final String EVENTS_PATH = "/events";

    @Override
    protected String getPath() {
        return EVENTS_PATH;
    }

    private static QueryBuilder createEventsQuery(String executionId, Date timestamp) {
        BoolQueryBuilder eventsQuery = QueryBuilders.boolQuery();
        if (executionId != null && !executionId.isEmpty()) {
            eventsQuery.must(QueryBuilders.matchQuery("context.execution_id", executionId));
        }
        if (timestamp != null) {
            eventsQuery.must(QueryBuilders.rangeQuery("@timestamp").gt(timestamp.getTime()));
        }
        eventsQuery.must(QueryBuilders.matchQuery("type", "cloudify_event"));
        return eventsQuery;
    }

    private static Map<String, Object>[] createSort() {
        Map<String, Object> sortByTimestampAsc = Maps.newHashMap();
        Map<String, Object>[] sorts = new Map[] { sortByTimestampAsc };
        Map<String, Object> ascendingSort = Maps.newHashMap();
        sortByTimestampAsc.put("@timestamp", ascendingSort);
        ascendingSort.put("order", "asc");
        return sorts;
    }

    @SneakyThrows
    public ListenableFuture<GetEventsResult> asyncGetBatch(String executionId, Date fromDate, int from, int batchSize) {
        Map<String, Object> request = Maps.newHashMap();
        request.put("from", from);
        request.put("size", batchSize);
        Map<String, Object>[] sorts = createSort();
        request.put("sort", sorts);
        QueryBuilder eventsQuery = createEventsQuery(executionId, fromDate);
        String eventsQueryText = new String(eventsQuery.buildAsBytes().toBytes());
        Map<String, Object> query = JsonUtil.toMap(eventsQueryText);
        log.info("Start get events for execution {} with offset {}, batch size {} and query {}", executionId, from, batchSize, eventsQueryText);
        request.put("query", query);
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        return FutureUtil.unwrapRestResponse(getRestTemplate().exchange(getBaseUrl(), HttpMethod.POST, new HttpEntity<>(request, headers),
                GetEventsResult.class));
    }

    @SneakyThrows
    public Event[] getBatch(String executionId, Date fromDate, int from, int batchSize) {
        return asyncGetBatch(executionId, fromDate, from, batchSize).get().getEvents();
    }
}
