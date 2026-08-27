package com.smartqa.browser.intelligence;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Interactive DOM candidate with compact structural context.
 * Parent/child/sibling relationships are carried as text/id context (not a full tree dump).
 */
public record ElementCandidate(
        String candidateId,
        String tag,
        String role,
        String accessibleName,
        String text,
        String label,
        String ariaLabel,
        String ariaLabelledBy,
        String placeholder,
        String title,
        String name,
        String id,
        String className,
        String testId,
        String href,
        String inputType,
        String value,
        boolean visible,
        boolean enabled,
        boolean disabled,
        boolean checked,
        boolean selected,
        boolean readOnly,
        boolean required,
        String boundingBox,
        String iframeContext,
        String frameUrl,
        String frameName,
        String parentFrameContext,
        String shadowContext,
        String targetPath,
        String parentContext,
        String nearbyText,
        String associatedControlSelector,
        String associatedControlTag,
        String associatedControlRole,
        boolean clickable,
        boolean inHeaderRegion,
        boolean hasIcon,
        String actionableSelector,
        String actionableTag,
        String actionableRole,
        /** HEADER | NAVIGATION | SIDEBAR | FILTER_PANEL | SEARCH_AREA | MAIN | CONTENT | FOOTER | DIALOG */
        String region,
        /** Nearest heading / section title text. */
        String headingContext,
        /** Compact ancestor chain texts for ownership reasoning. */
        String ancestorContext,
        /** Compact sibling texts. */
        String siblingContext,
        String parentTag,
        boolean ariaExpanded,
        double evidenceQuality,
        String parentId,
        String containerId,
        String ancestorIds,
        String siblingIds,
        String frameId,
        String shadowRootId,
        ElementStructure structure
) {
    public ElementCandidate {
        parentId = parentId == null ? "" : parentId;
        containerId = containerId == null ? "" : containerId;
        ancestorIds = ancestorIds == null ? "" : ancestorIds;
        siblingIds = siblingIds == null ? "" : siblingIds;
        frameId = frameId == null ? "" : frameId;
        shadowRootId = shadowRootId == null ? "" : shadowRootId;
        structure = structure == null ? ElementStructure.empty() : structure;
    }

    public ElementCandidate(
            String candidateId, String tag, String role, String accessibleName, String text, String label,
            String ariaLabel, String ariaLabelledBy, String placeholder, String title, String name, String id,
            String className, String testId, String href, String inputType, String value,
            boolean visible, boolean enabled, boolean disabled, boolean checked, boolean selected,
            boolean readOnly, boolean required, String boundingBox, String iframeContext, String frameUrl,
            String frameName, String parentFrameContext, String shadowContext, String targetPath,
            String parentContext, String nearbyText, String associatedControlSelector,
            String associatedControlTag, String associatedControlRole, boolean clickable, boolean inHeaderRegion,
            boolean hasIcon, String actionableSelector, String actionableTag, String actionableRole,
            String region, String headingContext, String ancestorContext, String siblingContext,
            String parentTag, boolean ariaExpanded, double evidenceQuality
    ) {
        this(candidateId, tag, role, accessibleName, text, label, ariaLabel, ariaLabelledBy, placeholder, title,
                name, id, className, testId, href, inputType, value, visible, enabled, disabled, checked, selected,
                readOnly, required, boundingBox, iframeContext, frameUrl, frameName, parentFrameContext, shadowContext,
                targetPath, parentContext, nearbyText, associatedControlSelector, associatedControlTag,
                associatedControlRole, clickable, inHeaderRegion, hasIcon, actionableSelector, actionableTag,
                actionableRole, region, headingContext, ancestorContext, siblingContext, parentTag, ariaExpanded,
                evidenceQuality, "", "", "", "", iframeContext == null ? "" : iframeContext,
                shadowContext == null ? "" : shadowContext, ElementStructure.empty());
    }
    public static ElementCandidate fromMap(Map<String, Object> raw, int index) {
        ElementCandidate base = new ElementCandidate(
                string(raw.get("candidateId"), "el-" + index),
                string(raw.get("tag"), ""),
                string(raw.get("role"), ""),
                string(raw.get("accessibleName"), ""),
                string(raw.get("text"), ""),
                string(raw.get("label"), ""),
                string(raw.get("ariaLabel"), ""),
                string(raw.get("ariaLabelledBy"), ""),
                string(raw.get("placeholder"), ""),
                string(raw.get("title"), ""),
                string(raw.get("name"), ""),
                string(raw.get("id"), ""),
                string(raw.get("className"), ""),
                string(raw.get("testId"), ""),
                string(raw.get("href"), ""),
                string(raw.get("inputType"), ""),
                string(raw.get("value"), ""),
                bool(raw.get("visible"), true),
                bool(raw.get("enabled"), true),
                bool(raw.get("disabled"), false),
                bool(raw.get("checked"), false),
                bool(raw.get("selected"), false),
                bool(raw.get("readOnly"), false),
                bool(raw.get("required"), false),
                string(raw.get("boundingBox"), ""),
                string(raw.get("iframeContext"), ""),
                string(raw.get("frameUrl"), ""),
                string(raw.get("frameName"), ""),
                string(raw.get("parentFrameContext"), ""),
                string(raw.get("shadowContext"), ""),
                string(raw.get("targetPath"), ""),
                string(raw.get("parentContext"), ""),
                string(raw.get("nearbyText"), ""),
                string(raw.get("associatedControlSelector"), ""),
                string(raw.get("associatedControlTag"), ""),
                string(raw.get("associatedControlRole"), ""),
                bool(raw.get("clickable"), false),
                bool(raw.get("inHeaderRegion"), false),
                bool(raw.get("hasIcon"), false),
                string(raw.get("actionableSelector"), ""),
                string(raw.get("actionableTag"), ""),
                string(raw.get("actionableRole"), ""),
                string(raw.get("region"), ""),
                string(raw.get("headingContext"), ""),
                string(raw.get("ancestorContext"), ""),
                string(raw.get("siblingContext"), ""),
                string(raw.get("parentTag"), ""),
                bool(raw.get("ariaExpanded"), false),
                0,
                string(raw.get("parentId"), ""),
                string(raw.get("containerId"), ""),
                string(raw.get("ancestorIds"), ""),
                string(raw.get("siblingIds"), ""),
                string(raw.get("frameId"), string(raw.get("iframeContext"), "")),
                string(raw.get("shadowRootId"), ""),
                ElementStructure.fromMap(raw)
        );
        return base.withEvidenceQuality(DomEvidence.quality(base));
    }

    public ElementCandidate withEvidenceQuality(double quality) {
        return new ElementCandidate(
                candidateId, tag, role, accessibleName, text, label, ariaLabel, ariaLabelledBy,
                placeholder, title, name, id, className, testId, href, inputType, value,
                visible, enabled, disabled, checked, selected, readOnly, required, boundingBox,
                iframeContext, frameUrl, frameName, parentFrameContext, shadowContext, targetPath,
                parentContext, nearbyText, associatedControlSelector, associatedControlTag,
                associatedControlRole, clickable, inHeaderRegion, hasIcon, actionableSelector,
                actionableTag, actionableRole, region, headingContext, ancestorContext, siblingContext,
                parentTag, ariaExpanded, quality, parentId, containerId, ancestorIds, siblingIds,
                frameId, shadowRootId, structure
        );
    }

    public ElementCandidate withRelationshipIds(
            String newParentId,
            String newContainerId,
            String newAncestorIds,
            String newSiblingIds,
            String newFrameId,
            String newShadowRootId
    ) {
        return new ElementCandidate(
                candidateId, tag, role, accessibleName, text, label, ariaLabel, ariaLabelledBy,
                placeholder, title, name, id, className, testId, href, inputType, value,
                visible, enabled, disabled, checked, selected, readOnly, required, boundingBox,
                iframeContext, frameUrl, frameName, parentFrameContext, shadowContext, targetPath,
                parentContext, nearbyText, associatedControlSelector, associatedControlTag,
                associatedControlRole, clickable, inHeaderRegion, hasIcon, actionableSelector,
                actionableTag, actionableRole, region, headingContext, ancestorContext, siblingContext,
                parentTag, ariaExpanded, evidenceQuality,
                firstNonBlank(newParentId, parentId),
                firstNonBlank(newContainerId, containerId),
                firstNonBlank(newAncestorIds, ancestorIds),
                firstNonBlank(newSiblingIds, siblingIds),
                firstNonBlank(newFrameId, frameId),
                firstNonBlank(newShadowRootId, shadowRootId),
                structure
        );
    }

    public ElementCandidate withRoleAndBox(String newRole, String newBoundingBox) {
        return new ElementCandidate(
                candidateId, tag, firstNonBlank(newRole, role), accessibleName, text, label, ariaLabel, ariaLabelledBy,
                placeholder, title, name, id, className, testId, href, inputType, value,
                visible, enabled, disabled, checked, selected, readOnly, required,
                firstNonBlank(newBoundingBox, boundingBox),
                iframeContext, frameUrl, frameName, parentFrameContext, shadowContext, targetPath,
                parentContext, nearbyText, associatedControlSelector, associatedControlTag,
                associatedControlRole, clickable, inHeaderRegion, hasIcon, actionableSelector,
                actionableTag, actionableRole, region, headingContext, ancestorContext, siblingContext,
                parentTag, ariaExpanded, evidenceQuality,
                parentId, containerId, ancestorIds, siblingIds, frameId, shadowRootId, structure
        );
    }

    public ElementCandidate withStructure(ElementStructure newStructure) {
        return new ElementCandidate(
                candidateId, tag, role, accessibleName, text, label, ariaLabel, ariaLabelledBy,
                placeholder, title, name, id, className, testId, href, inputType, value,
                visible, enabled, disabled, checked, selected, readOnly, required, boundingBox,
                iframeContext, frameUrl, frameName, parentFrameContext, shadowContext, targetPath,
                parentContext, nearbyText, associatedControlSelector, associatedControlTag,
                associatedControlRole, clickable, inHeaderRegion, hasIcon, actionableSelector,
                actionableTag, actionableRole, region, headingContext, ancestorContext, siblingContext,
                parentTag, ariaExpanded, evidenceQuality, parentId, containerId, ancestorIds, siblingIds,
                frameId, shadowRootId, newStructure == null ? ElementStructure.empty() : newStructure
        );
    }

    public ElementStructure structureOrEmpty() {
        return structure == null ? ElementStructure.empty() : structure;
    }

    public String headingContext() {
        return headingContext;
    }

    public String accessibleName() {
        return accessibleName;
    }

    public String containerId() {
        return containerId;
    }

    public boolean isContainer() {
        return structureOrEmpty().isContainer()
                || "form".equalsIgnoreCase(tag)
                || "fieldset".equalsIgnoreCase(tag)
                || "section".equalsIgnoreCase(tag)
                || "aside".equalsIgnoreCase(tag)
                || "nav".equalsIgnoreCase(tag)
                || "dialog".equalsIgnoreCase(tag);
    }

    public ControlType declaredControlType() {
        String name = structureOrEmpty().controlType();
        if (name == null || name.isBlank()) {
            return ControlClassifier.classifyFromCandidate(this);
        }
        try {
            return ControlType.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return ControlClassifier.classifyFromCandidate(this);
        }
    }

    public boolean isLabel() {
        return "label".equalsIgnoreCase(tag) || "label".equalsIgnoreCase(role);
    }

    public boolean isButtonLike() {
        String t = tag == null ? "" : tag.toLowerCase(Locale.ROOT);
        String r = role == null ? "" : role.toLowerCase(Locale.ROOT);
        String type = inputType == null ? "" : inputType.toLowerCase(Locale.ROOT);
        return "button".equals(t) || "button".equals(r) || "submit".equals(type);
    }

    public boolean isTabularChrome() {
        String r = role == null ? "" : role.toLowerCase();
        return "columnheader".equals(r) || "rowheader".equals(r) || "row".equals(r)
                || "rowgroup".equals(r) || "table".equals(r) || "gridcell".equals(r) || "cell".equals(r);
    }

    public boolean hasAssociatedControl() {
        return associatedControlSelector != null && !associatedControlSelector.isBlank();
    }

    public List<String> semanticTokens() {
        return List.of(accessibleName, text, label, ariaLabel, ariaLabelledBy, placeholder, title, name, id, value,
                        testId, nearbyText, parentContext, headingContext, ancestorContext, siblingContext,
                        className, actionableTag, actionableRole, region)
                .stream()
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    /** Ownership blob used for filter option → filter section matching. */
    public String ownershipContext() {
        return String.join(" ",
                nullToEmpty(headingContext),
                nullToEmpty(ancestorContext),
                nullToEmpty(parentContext),
                nullToEmpty(nearbyText),
                nullToEmpty(label),
                nullToEmpty(region)).toLowerCase(Locale.ROOT);
    }

    public List<String> ancestorIdList() {
        return splitIds(ancestorIds);
    }

    public List<String> siblingIdList() {
        return splitIds(siblingIds);
    }

    private static List<String> splitIds(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private static String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback == null ? "" : fallback;
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }

    private static String string(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value).trim();
    }

    private static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
