package com.smartqa.generation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic repairs for AI-generated Playwright Java before the quality gate compiles it.
 * Does not invent locators or steps.
 */
public final class GeneratedCodeSanitizer {

    private static final Pattern FENCE = Pattern.compile("(?s)```(?:java)?\\s*(.*?)```");
    private static final Map<String, String> LITERALS = new LinkedHashMap<>();

    static {
        LITERALS.put("LoadState.DOM_CONTENT_LOADED", "LoadState.DOMCONTENTLOADED");
        LITERALS.put("LoadState.NETWORK_IDLE", "LoadState.NETWORKIDLE");
        LITERALS.put("AriaRole.TEXT_BOX", "AriaRole.TEXTBOX");
        LITERALS.put("AriaRole.SEARCH_BOX", "AriaRole.SEARCHBOX");
        LITERALS.put("AriaRole.COMBO_BOX", "AriaRole.COMBOBOX");
        LITERALS.put("AriaRole.LIST_ITEM", "AriaRole.LISTITEM");
        LITERALS.put("AriaRole.LIST_BOX", "AriaRole.LISTBOX");
        LITERALS.put("AriaRole.TAB_LIST", "AriaRole.TABLIST");
        LITERALS.put("AriaRole.MENU_ITEM", "AriaRole.MENUITEM");
        LITERALS.put("AriaRole.MENU_BAR", "AriaRole.MENUBAR");
        LITERALS.put("AriaRole.RADIO_GROUP", "AriaRole.RADIOGROUP");
        LITERALS.put("AriaRole.SPIN_BUTTON", "AriaRole.SPINBUTTON");
        LITERALS.put("AriaRole.TREE_ITEM", "AriaRole.TREEITEM");
        LITERALS.put("AriaRole.CHECK_BOX", "AriaRole.CHECKBOX");
    }

    private GeneratedCodeSanitizer() {
    }

    public static String sanitize(String source) {
        if (source == null) {
            return "";
        }
        String out = stripFence(source).trim();
        for (Map.Entry<String, String> entry : LITERALS.entrySet()) {
            out = out.replace(entry.getKey(), entry.getValue());
        }
        out = out.replace("Page.GetByLoadStateOptions", "Page.WaitForLoadStateOptions");
        return out;
    }

    private static String stripFence(String source) {
        Matcher matcher = FENCE.matcher(source);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return source.replace("```java", "").replace("```", "");
    }
}
