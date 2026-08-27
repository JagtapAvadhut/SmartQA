package com.smartqa.browser.intelligence;

import com.smartqa.intent.SupportedActions;

import java.util.Locale;

/**
 * Evaluates non-overridable constraints before {@link LocatorRanker} soft scores.
 */
public final class HardConstraintChecker {

    private HardConstraintChecker() {
    }

    public static HardConstraint evaluate(ElementCandidate element, String action, String ownerHint) {
        return evaluate(element, action, ownerHint, null, null);
    }

    public static HardConstraint evaluate(
            ElementCandidate element,
            String action,
            String ownerHint,
            String expectedFrame,
            String expectedShadow
    ) {
        if (element == null) {
            return HardConstraint.STALE;
        }
        if (!element.visible()) {
            return HardConstraint.NOT_VISIBLE;
        }
        if (element.structureOrEmpty().covered()) {
            return HardConstraint.COVERED;
        }
        if (!element.structureOrEmpty().reconciled()) {
            return HardConstraint.TREE_GRAPH_INCONSISTENT;
        }
        if (element.disabled() || !element.enabled()) {
            return HardConstraint.DISABLED;
        }
        ControlType type = ControlClassifier.classifyFromCandidate(element);
        if (type != ControlType.LABEL
                && isStrictWidgetAction(action) && ActionCompatibility.isCapabilityMismatch(action, type)) {
            return HardConstraint.CAPABILITY_MISMATCH;
        }
        if (notBlank(expectedFrame) && notBlank(element.frameId())
                && !element.frameId().equalsIgnoreCase(expectedFrame)
                && !element.iframeContext().equalsIgnoreCase(expectedFrame)) {
            return HardConstraint.WRONG_FRAME;
        }
        if (notBlank(expectedShadow) && notBlank(element.shadowRootId())
                && !element.shadowRootId().toLowerCase(Locale.ROOT).contains(expectedShadow.toLowerCase(Locale.ROOT))
                && !element.shadowContext().toLowerCase(Locale.ROOT).contains(expectedShadow.toLowerCase(Locale.ROOT))) {
            return HardConstraint.WRONG_SHADOW_CONTEXT;
        }
        if (isContentAction(action) && looksLikeAuthChrome(element)) {
            return HardConstraint.WRONG_PAGE_STATE;
        }
        if (notBlank(ownerHint)
                && !DomEvidence.ownsContext(element, ownerHint)
                && !containsIgnoreCase(element.containerId(), ownerHint)
                && !containsIgnoreCase(element.parentId(), ownerHint)
                && !containsIgnoreCase(element.headingContext(), ownerHint)
                && !containsIgnoreCase(element.structureOrEmpty().treePath(), ownerHint)) {
            return HardConstraint.INVALID_OWNER;
        }
        if (isInteractAction(action)
                && (type == ControlType.HEADING || type == ControlType.TEXT)
                && !element.clickable()) {
            return HardConstraint.NON_ACTIONABLE;
        }
        return null;
    }

    private static boolean isStrictWidgetAction(String action) {
        if (action == null) {
            return false;
        }
        String lower = action.toLowerCase(Locale.ROOT);
        return SupportedActions.CHECKBOX.equals(lower)
                || SupportedActions.RADIO.equals(lower)
                || SupportedActions.SELECT.equals(lower)
                || SupportedActions.INPUT.equals(lower);
    }

    private static boolean isOwnershipRequired(String action) {
        if (action == null) {
            return false;
        }
        String lower = action.toLowerCase(Locale.ROOT);
        return SupportedActions.CHECKBOX.equals(lower)
                || SupportedActions.RADIO.equals(lower)
                || SupportedActions.FILTER.equals(lower);
    }

    private static boolean isInteractAction(String action) {
        if (action == null) {
            return false;
        }
        String lower = action.toLowerCase(Locale.ROOT);
        return SupportedActions.CLICK.equals(lower)
                || SupportedActions.CHECKBOX.equals(lower)
                || SupportedActions.RADIO.equals(lower)
                || SupportedActions.SELECT.equals(lower)
                || SupportedActions.EXPAND.equals(lower)
                || SupportedActions.COLLAPSE.equals(lower)
                || SupportedActions.ADD_TO_CART.equals(lower)
                || SupportedActions.QUANTITY.equals(lower);
    }

    private static boolean isContentAction(String action) {
        if (action == null) {
            return false;
        }
        String lower = action.toLowerCase(Locale.ROOT);
        return SupportedActions.FILTER.equals(lower)
                || SupportedActions.SEARCH.equals(lower)
                || SupportedActions.ADD_TO_CART.equals(lower)
                || SupportedActions.QUANTITY.equals(lower)
                || SupportedActions.SELECT.equals(lower);
    }

    private static boolean looksLikeAuthChrome(ElementCandidate element) {
        String blob = (element.region() + " " + element.headingContext() + " " + element.accessibleName()
                + " " + element.region()).toLowerCase(Locale.ROOT);
        return blob.contains("login") || blob.contains("sign in") || blob.contains("password");
    }

    private static boolean isFillable(ElementCandidate element, String action) {
        if (action == null) {
            return false;
        }
        String lower = action.toLowerCase(Locale.ROOT);
        if (!SupportedActions.INPUT.equals(lower) && !SupportedActions.SEARCH.equals(lower)) {
            return false;
        }
        String tag = element.tag() == null ? "" : element.tag().toLowerCase(Locale.ROOT);
        return "input".equals(tag) || "textarea".equals(tag);
    }

    private static boolean isVerify(String action) {
        return action != null && SupportedActions.VERIFY.equalsIgnoreCase(action);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        if (haystack == null || needle == null || haystack.isBlank() || needle.isBlank()) {
            return false;
        }
        return haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }
}
