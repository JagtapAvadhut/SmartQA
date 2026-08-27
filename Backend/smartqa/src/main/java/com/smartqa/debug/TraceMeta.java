package com.smartqa.debug;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TraceMeta {

    private TraceMeta() {
    }

    public static Map<String, Object> of(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (keyValues == null) {
            return map;
        }
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            Object key = keyValues[i];
            if (key != null) {
                map.put(String.valueOf(key), keyValues[i + 1]);
            }
        }
        return map;
    }
}
