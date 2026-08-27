package com.smartqa.browser.multimodal;

/**
 * Generic target kinds. Not website-specific.
 */
public final class TargetType {

    public static final String FILTER_OPTION = "FILTER_OPTION";
    public static final String GENERIC = "GENERIC";
    public static final String TEXT_TARGET = "TEXT_TARGET";
    public static final String IMAGE_TARGET = "IMAGE_TARGET";
    public static final String IMAGE_TEXT_TARGET = "IMAGE_TEXT_TARGET";
    public static final String IMAGE_LINK = "IMAGE_LINK";
    public static final String VISUAL_TEXT = "VISUAL_TEXT";
    public static final String VISUAL_CARD = "VISUAL_CARD";
    public static final String BANNER = "BANNER";
    public static final String ICON = "ICON";
    public static final String BUTTON = "BUTTON";
    public static final String LINK = "LINK";
    public static final String CHECKBOX = "CHECKBOX";
    public static final String RADIO = "RADIO";
    public static final String DROPDOWN = "DROPDOWN";
    public static final String TAB = "TAB";
    public static final String PRODUCT = "PRODUCT";
    public static final String PRODUCT_CARD = "PRODUCT_CARD";
    public static final String MENU_ITEM = "MENU_ITEM";

    private TargetType() {
    }

    public static boolean isVisual(String type) {
        if (type == null) {
            return false;
        }
        return switch (type) {
            case IMAGE_TARGET, IMAGE_TEXT_TARGET, IMAGE_LINK, VISUAL_TEXT, VISUAL_CARD, BANNER, ICON, PRODUCT_CARD -> true;
            default -> false;
        };
    }

    public static boolean isFilterOption(String type) {
        return FILTER_OPTION.equals(type) || CHECKBOX.equals(type) || RADIO.equals(type);
    }
}
