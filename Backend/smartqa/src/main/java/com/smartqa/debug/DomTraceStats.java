package com.smartqa.debug;

import com.smartqa.browser.intelligence.ElementCandidate;

import java.util.List;
import java.util.Locale;

public final class DomTraceStats {

    private DomTraceStats() {
    }

    public static java.util.Map<String, Object> summarize(String url, List<ElementCandidate> elements) {
        int buttons = 0;
        int inputs = 0;
        int links = 0;
        int selects = 0;
        int checkboxes = 0;
        int visible = 0;
        int enabled = 0;
        List<ElementCandidate> list = elements == null ? List.of() : elements;
        for (ElementCandidate element : list) {
            if (element.visible()) {
                visible++;
            }
            if (element.enabled()) {
                enabled++;
            }
            String tag = safe(element.tag());
            String role = safe(element.role());
            String type = safe(element.inputType());
            if ("button".equals(tag) || "button".equals(role)) {
                buttons++;
            } else if ("a".equals(tag) || "link".equals(role)) {
                links++;
            } else if ("select".equals(tag) || "combobox".equals(role)) {
                selects++;
            } else if ("checkbox".equals(type) || "checkbox".equals(role)) {
                checkboxes++;
            } else if ("input".equals(tag) || "textarea".equals(tag) || "textbox".equals(role) || "searchbox".equals(role)) {
                inputs++;
            }
        }
        return TraceMeta.of(
                "url", url,
                "totalNodes", list.size(),
                "interactiveElements", list.size(),
                "visibleElements", visible,
                "enabledElements", enabled,
                "buttons", buttons,
                "inputs", inputs,
                "links", links,
                "selects", selects,
                "checkboxes", checkboxes
        );
    }

    private static String safe(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
