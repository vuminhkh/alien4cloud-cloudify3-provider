package alien4cloud.paas.cloudify3.util;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import alien4cloud.rest.utils.JsonUtil;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.Maps;

@Slf4j
public class MapUtil {

    public static Map<String, String> toString(Map<String, Object> stringObjectMap) {
        Map<String, String> stringStringMap = Maps.newHashMap();
        for (Map.Entry<String, Object> stringObjectEntry : stringObjectMap.entrySet()) {
            try {
                stringStringMap.put(stringObjectEntry.getKey(), JsonUtil.toString(stringObjectEntry.getValue()));
            } catch (JsonProcessingException e) {
                log.error("Unable to serialize", e);
            }
        }
        return stringStringMap;
    }
}
