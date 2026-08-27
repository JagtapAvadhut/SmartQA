package com.smartqa.browser.intelligence;

/**
 * Hard rejects applied before soft scoring. Soft similarity cannot revive a rejected candidate.
 */
public enum HardConstraint {
    NOT_VISIBLE,
    DISABLED,
    STALE,
    CAPABILITY_MISMATCH,
    WRONG_FRAME,
    WRONG_SHADOW_CONTEXT,
    INVALID_OWNER,
    WRONG_PAGE_STATE,
    NON_ACTIONABLE,
    COVERED,
    TREE_GRAPH_INCONSISTENT
}
