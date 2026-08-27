package com.smartqa.intent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = IntentFilterDeserializer.class)
public record IntentFilter(
        String field,
        String operator,
        String value,
        Double min,
        Double max
) {
    public String displayValue() {
        if ("between".equalsIgnoreCase(operator) && min != null && max != null) {
            return min.longValue() + "-" + max.longValue();
        }
        return value;
    }
}
