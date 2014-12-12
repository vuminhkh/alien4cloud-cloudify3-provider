package alien4cloud.paas.cloudify3.service;

import java.util.Date;

import org.springframework.stereotype.Component;

import alien4cloud.paas.model.AbstractMonitorEvent;

/**
 * Handle cloudify 3 events request
 *
 * @author Minh Khang VU
 */
@Component("cloudify-event-service")
public class EventService {

    public AbstractMonitorEvent[] getEventsSince(Date lastTimestamp, int batchSize) {
        return new AbstractMonitorEvent[0];
    }

}