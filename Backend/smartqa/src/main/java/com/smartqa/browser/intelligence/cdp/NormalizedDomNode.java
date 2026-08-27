package com.smartqa.browser.intelligence.cdp;

import java.util.List;
import java.util.Map;

/**
 * CDP/DOM node after string-table expansion. Not a Playwright locator.
 */
public record NormalizedDomNode(
        int nodeIndex,
        int backendNodeId,
        int nodeType,
        String nodeName,
        String nodeValue,
        Map<String, String> attributes,
        int parentIndex,
        List<Integer> childIndexes,
        double x,
        double y,
        double width,
        double height,
        String frameUrl
) {
    public String id() {
        return attributes.getOrDefault("id", "");
    }

    public String role() {
        return attributes.getOrDefault("role", "");
    }

    public String ariaLabel() {
        return firstNonBlank(attributes.get("aria-label"), attributes.get("aria-labelledby"));
    }

    public String nameAttr() {
        return attributes.getOrDefault("name", "");
    }

    public String type() {
        return attributes.getOrDefault("type", "");
    }

    public boolean visibleLayout() {
        return width > 0 && height > 0;
    }

    public String compact() {
        return nodeName
                + (id().isBlank() ? "" : "#" + id())
                + (role().isBlank() ? "" : " role=" + role())
                + (nodeValue == null || nodeValue.isBlank() ? "" : " \"" + trim(nodeValue, 40) + "\"");
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b == null ? "" : b;
    }

    private static String trim(String v, int max) {
        String t = v.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }
}
