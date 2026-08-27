package com.smartqa.browser.intelligence;

/**
 * Structural / AX / layout evidence attached to {@link ElementCandidate}.
 * Empty defaults keep compact constructors working.
 */
public record ElementStructure(
        String inventoryKind,
        String treePath,
        int treeDepth,
        String childIds,
        String previousSiblingId,
        String nextSiblingId,
        String formId,
        String backendNodeId,
        String axRole,
        String axName,
        String axStates,
        boolean covered,
        int zIndex,
        String controlType,
        String altText,
        String ariaDescribedBy,
        String labelledByIds,
        String describedByIds,
        String visualRegionId,
        boolean reconciled
) {
    public static ElementStructure empty() {
        return new ElementStructure(
                "INTERACTIVE", "", 0, "", "", "", "", "", "", "", "", false, 0, "", "", "", "", "", "", true);
    }

    public ElementStructure {
        inventoryKind = inventoryKind == null || inventoryKind.isBlank() ? "INTERACTIVE" : inventoryKind;
        treePath = treePath == null ? "" : treePath;
        childIds = childIds == null ? "" : childIds;
        previousSiblingId = previousSiblingId == null ? "" : previousSiblingId;
        nextSiblingId = nextSiblingId == null ? "" : nextSiblingId;
        formId = formId == null ? "" : formId;
        backendNodeId = backendNodeId == null ? "" : backendNodeId;
        axRole = axRole == null ? "" : axRole;
        axName = axName == null ? "" : axName;
        axStates = axStates == null ? "" : axStates;
        controlType = controlType == null ? "" : controlType;
        altText = altText == null ? "" : altText;
        ariaDescribedBy = ariaDescribedBy == null ? "" : ariaDescribedBy;
        labelledByIds = labelledByIds == null ? "" : labelledByIds;
        describedByIds = describedByIds == null ? "" : describedByIds;
        visualRegionId = visualRegionId == null ? "" : visualRegionId;
    }

    public static ElementStructure fromMap(java.util.Map<String, Object> raw) {
        if (raw == null) {
            return empty();
        }
        return new ElementStructure(
                str(raw.get("inventoryKind"), "INTERACTIVE"),
                str(raw.get("treePath"), ""),
                intVal(raw.get("treeDepth"), 0),
                str(raw.get("childIds"), ""),
                str(raw.get("previousSiblingId"), ""),
                str(raw.get("nextSiblingId"), ""),
                str(raw.get("formId"), ""),
                str(raw.get("backendNodeId"), ""),
                str(raw.get("axRole"), ""),
                str(raw.get("axName"), ""),
                str(raw.get("axStates"), ""),
                bool(raw.get("covered"), false),
                intVal(raw.get("zIndex"), 0),
                str(raw.get("controlType"), ""),
                str(raw.get("altText"), ""),
                str(raw.get("ariaDescribedBy"), ""),
                str(raw.get("labelledByIds"), ""),
                str(raw.get("describedByIds"), ""),
                str(raw.get("visualRegionId"), ""),
                bool(raw.get("reconciled"), true)
        );
    }

    public ElementStructure withTree(String path, int depth, String children) {
        return new ElementStructure(
                inventoryKind, path, depth, children, previousSiblingId, nextSiblingId, formId, backendNodeId,
                axRole, axName, axStates, covered, zIndex, controlType, altText, ariaDescribedBy,
                labelledByIds, describedByIds, visualRegionId, reconciled);
    }

    public ElementStructure withCdp(String backendId, String role, String name, String states) {
        return new ElementStructure(
                inventoryKind, treePath, treeDepth, childIds, previousSiblingId, nextSiblingId, formId,
                first(backendId, backendNodeId), first(role, axRole), first(name, axName), first(states, axStates),
                covered, zIndex, controlType, altText, ariaDescribedBy, labelledByIds, describedByIds,
                visualRegionId, reconciled);
    }

    public ElementStructure withControlType(String type) {
        return new ElementStructure(
                inventoryKind, treePath, treeDepth, childIds, previousSiblingId, nextSiblingId, formId,
                backendNodeId, axRole, axName, axStates, covered, zIndex, type == null ? controlType : type,
                altText, ariaDescribedBy, labelledByIds, describedByIds, visualRegionId, reconciled);
    }

    public ElementStructure withVisualRegion(String regionId) {
        return new ElementStructure(
                inventoryKind, treePath, treeDepth, childIds, previousSiblingId, nextSiblingId, formId,
                backendNodeId, axRole, axName, axStates, covered, zIndex, controlType, altText, ariaDescribedBy,
                labelledByIds, describedByIds, regionId == null ? visualRegionId : regionId, reconciled);
    }

    public ElementStructure withCovered(boolean isCovered, int z) {
        return new ElementStructure(
                inventoryKind, treePath, treeDepth, childIds, previousSiblingId, nextSiblingId, formId,
                backendNodeId, axRole, axName, axStates, isCovered, z, controlType, altText, ariaDescribedBy,
                labelledByIds, describedByIds, visualRegionId, reconciled);
    }

    public ElementStructure withReconciled(boolean ok) {
        return new ElementStructure(
                inventoryKind, treePath, treeDepth, childIds, previousSiblingId, nextSiblingId, formId,
                backendNodeId, axRole, axName, axStates, covered, zIndex, controlType, altText, ariaDescribedBy,
                labelledByIds, describedByIds, visualRegionId, ok);
    }

    public boolean isContainer() {
        return "CONTAINER".equalsIgnoreCase(inventoryKind) || "OVERLAY".equalsIgnoreCase(inventoryKind);
    }

    public boolean isActionableKind() {
        return "INTERACTIVE".equalsIgnoreCase(inventoryKind) || "LABEL".equalsIgnoreCase(inventoryKind);
    }

    public String axName() {
        return axName;
    }

    public String treePath() {
        return treePath;
    }

    private static String str(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value).trim();
    }

    private static int intVal(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static String first(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : (fallback == null ? "" : fallback);
    }
}
