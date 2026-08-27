package com.smartqa.browser.intelligence.cdp;

import java.util.List;

public record AccessibilityNode(
        String nodeId,
        String role,
        String name,
        int backendDomNodeId,
        boolean ignored,
        List<String> childIds,
        String states
) {
    public AccessibilityNode(
            String nodeId,
            String role,
            String name,
            int backendDomNodeId,
            boolean ignored,
            List<String> childIds
    ) {
        this(nodeId, role, name, backendDomNodeId, ignored, childIds, "");
    }

    public String compact() {
        return role + (name == null || name.isBlank() ? "" : " \"" + name + "\"");
    }
}
