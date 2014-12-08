package alien4cloud.paas.cloudify3.model;

import java.util.Collection;
import java.util.List;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.Lists;

@Getter
@Setter
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
@SuppressWarnings("PMD.UnusedPrivateField")
public class GetEventsResult extends AbstractCloudifyModel {

    private GetEventsHits hits;

    @Getter
    @Setter
    @EqualsAndHashCode
    @JsonIgnoreProperties(ignoreUnknown = true)
    @SuppressWarnings("PMD.UnusedPrivateField")
    private static class GetEventsHits extends AbstractCloudifyModel {
        private GetEventsHit[] hits;
    }

    @Getter
    @Setter
    @EqualsAndHashCode
    @JsonIgnoreProperties(ignoreUnknown = true)
    @SuppressWarnings("PMD.UnusedPrivateField")
    private static class GetEventsHit extends AbstractCloudifyModel {
        private Event __source;
    }

    @JsonIgnore
    public Collection<Event> getEvents() {
        List<Event> events = Lists.newArrayList();
        if (hits == null) {
            return events;
        }
        GetEventsHit[] internalHits = hits.getHits();
        if (internalHits == null || internalHits.length == 0) {
            return events;
        }
        for (GetEventsHit internalHit : internalHits) {
            if (internalHit != null && internalHit.get__source() != null) {
                events.add(internalHit.get__source());
            }
        }
        return events;
    }
}