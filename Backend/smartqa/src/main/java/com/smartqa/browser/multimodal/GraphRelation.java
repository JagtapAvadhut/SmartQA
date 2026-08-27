package com.smartqa.browser.multimodal;

/**
 * Typed graph edges. Coordinates and strings are evidence, not locators.
 */
public enum GraphRelation {
    PARENT_OF,
    CHILD_OF,
    ANCESTOR_OF,
    DESCENDANT_OF,
    SIBLING_OF,
    PRECEDES,
    FOLLOWS,
    OWNS,
    CONTAINS,
    BELONGS_TO,
    LABELS,
    LABEL_FOR,
    DESCRIBES,
    DESCRIBED_BY,
    CONTROLS,
    CONTROLLED_BY,
    FORM_CONTROL,
    FILTER_OWNS,
    SEARCH_OWNS,
    MENU_OWNS,
    DIALOG_OWNS,
    CARD_OWNS,
    TABLE_OWNS,
    ROW_OWNS,
    CELL_OWNS,
    TRIGGERS,
    VISUALLY_NEAR,
    VISUALLY_CONTAINS,
    OVERLAPS,
    COVERS,
    FRAME_OF,
    INSIDE_FRAME,
    SHADOW_ROOT_OF,
    INSIDE_SHADOW_ROOT,
    ACCESSIBILITY_PARENT,
    ACCESSIBILITY_LABEL
}
