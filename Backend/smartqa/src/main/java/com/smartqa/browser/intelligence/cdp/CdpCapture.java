package com.smartqa.browser.intelligence.cdp;

import java.time.Instant;
import java.util.List;

/**
 * One Chromium CDP evidence moment. Never used as a Playwright locator.
 */
public record CdpCapture(
        boolean captured,
        String engine,
        String documentUrl,
        String title,
        DomGraph graph,
        List<AccessibilityNode> accessibility,
        String fallbackReason,
        Instant capturedAt
) {
    public static CdpCapture unavailable(String reason) {
        return new CdpCapture(false, "unknown", "", "", new DomGraph(List.of()), List.of(), reason, Instant.now());
    }

    public int nodeCount() {
        return graph == null ? 0 : graph.nodes().size();
    }

    public String compactDom(int limit) {
        return graph == null ? "" : graph.compact(limit);
    }

    public String compactAccessibility(int limit) {
        if (accessibility == null || accessibility.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (AccessibilityNode node : accessibility) {
            if (node.ignored() || node.role() == null) {
                continue;
            }
            if ("none".equalsIgnoreCase(node.role()) || "generic".equalsIgnoreCase(node.role())
                    || "InlineTextBox".equalsIgnoreCase(node.role())) {
                continue;
            }
            sb.append(node.compact()).append('\n');
            n++;
            if (n >= Math.max(1, limit)) {
                break;
            }
        }
        return sb.toString();
    }
}
