package alien4cloud.paas.cloudify3.restclient;

import java.util.Date;
import java.util.Map;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import alien4cloud.paas.cloudify3.model.Event;
import alien4cloud.paas.cloudify3.model.GetEventsResult;
import alien4cloud.paas.cloudify3.util.FutureUtil;
import alien4cloud.rest.utils.JsonUtil;

import com.google.common.base.Function;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Base class for event access
 */
@Slf4j
public abstract class AbstractEventClient extends AbstractClient {

    public static final String EVENTS_PATH = "/events";

    protected abstract QueryBuilder createEventsQuery(String executionId, Date timestamp);

    @Override
    protected String getPath() {
        return EVENTS_PATH;
    }

    protected Map<String, Object>[] createSort() {
        Map<String, Object> sortByTimestampAsc = Maps.newHashMap();
        Map<String, Object>[] sorts = new Map[] { sortByTimestampAsc };
        Map<String, Object> ascendingSort = Maps.newHashMap();
        sortByTimestampAsc.put("@timestamp", ascendingSort);
        ascendingSort.put("order", "asc");
        ascendingSort.put("ignoreUnmapped", true);
        return sorts;
    }

    @SneakyThrows
    public ListenableFuture<Event[]> asyncGetBatch(String executionId, Date fromDate, int from, int batchSize) {
        Map<String, Object> request = Maps.newHashMap();
        request.put("from", from);
        request.put("size", batchSize);
        Map<String, Object>[] sorts = createSort();
        request.put("sort", sorts);
        QueryBuilder eventsQuery = createEventsQuery(executionId, fromDate);
        String eventsQueryText = new String(eventsQuery.buildAsBytes().toBytes());
        Map<String, Object> query = JsonUtil.toMap(eventsQueryText);
        if (log.isTraceEnabled()) {
            log.trace("Start get events for execution {} with offset {}, batch size {} and query {}", executionId, from, batchSize, eventsQueryText);
        }
        request.put("query", query);
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        ListenableFuture<GetEventsResult> eventsResultListenableFuture = FutureUtil.unwrapRestResponse(exchange(getBaseUrl(), HttpMethod.POST,
                new HttpEntity<>(request, headers), GetEventsResult.class));
        Function<GetEventsResult, Event[]> eventsAdapter = new Function<GetEventsResult, Event[]>() {
            @Override
            public Event[] apply(GetEventsResult input) {
                return input.getEvents();
            }
        };
        return Futures.transform(eventsResultListenableFuture, eventsAdapter);
    }

    @SneakyThrows
    public Event[] getBatch(String executionId, Date fromDate, int from, int batchSize) {
        return asyncGetBatch(executionId, fromDate, from, batchSize).get();
    }
}
