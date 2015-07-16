package alien4cloud.paas.cloudify3.util;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import alien4cloud.rest.utils.JsonUtil;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.Maps;

@Slf4j
public class MapUtil {

    private static <T, U> Map<T, U> getEmptyMapIfNull(Map<T, U> map) {
        return map != null ? map : Maps.<T, U> newHashMap();
    }

    public static <T, U> Map<T, U> merge(Map<T, U> l, Map<T, U> r) {
        if (l == null) {
            return getEmptyMapIfNull(r);
        } else if (r == null) {
            return getEmptyMapIfNull(l);
        } else {
            Map<T, U> copy = Maps.newHashMap(l);
            copy.putAll(r);
            return copy;
        }
    }

    public static Map<String, String> toString(Map<String, Object> stringObjectMap) {
        Map<String, String> stringStringMap = Maps.newHashMap();
        for (Map.Entry<String, Object> stringObjectEntry : stringObjectMap.entrySet()) {
            try {
                if (stringObjectEntry.getValue() != null) {
                    if (stringObjectEntry.getValue() instanceof String) {
                        stringStringMap.put(stringObjectEntry.getKey(), (String) stringObjectEntry.getValue());
                    } else {
                        stringStringMap.put(stringObjectEntry.getKey(), JsonUtil.toString(stringObjectEntry.getValue()));
                    }
                } else {
                    stringStringMap.put(stringObjectEntry.getKey(), null);
                }
            } catch (JsonProcessingException e) {
                log.error("Unable to serialize", e);
            }
        }
        return stringStringMap;
    }
}
