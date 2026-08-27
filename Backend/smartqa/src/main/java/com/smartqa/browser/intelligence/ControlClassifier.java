package com.smartqa.browser.intelligence;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;

import java.util.Locale;
import java.util.Map;

public final class ControlClassifier {

    private ControlClassifier() {
    }

    public static ControlType classify(Locator locator) {
        try {
            Map<String, String> attrs = evaluateAttributes(locator);
            return classifyFromAttributes(attrs);
        } catch (RuntimeException ex) {
            TraceLogger.warn("CONTROL", "CLASSIFY_FAILED", "Could not classify control: " + ex.getMessage(),
                    TraceMeta.of("error", ex.getMessage()));
            return ControlType.OTHER;
        }
    }

    public static ControlType classifyFromAttributes(Map<String, String> attrs) {
        String tag = lower(attrs.get("tag"));
        String role = lower(attrs.get("role"));
        String type = lower(attrs.get("type"));
        String ariaHasPopup = lower(attrs.get("ariaHasPopup"));
        String ariaExpanded = attrs.get("ariaExpanded");
        String ariaAutocomplete = lower(attrs.get("ariaAutocomplete"));
        boolean hasListbox = "true".equals(attrs.get("hasListbox"));
        boolean hasOptions = "true".equals(attrs.get("hasOptions"));
        String ariaLabel = lower(attrs.get("ariaLabel"));
        String title = lower(attrs.get("title"));
        String className = lower(attrs.getOrDefault("className", ""));
        String text = lower(attrs.get("text"));
        boolean hasSvg = "true".equals(attrs.get("hasSvg"));

        if ("select".equals(tag)) {
            return ControlType.NATIVE_SELECT;
        }

        if ("combobox".equals(role) || "combobox".equals(tag)) {
            return ControlType.COMBOBOX;
        }

        if ("listbox".equals(role)) {
            return ControlType.LISTBOX;
        }

        if ("option".equals(role) || "option".equals(tag)) {
            return ControlType.OPTION;
        }

        if ("input".equals(tag) || "textarea".equals(tag)) {
            if ("checkbox".equals(type)) return ControlType.CHECKBOX;
            if ("radio".equals(type)) return ControlType.RADIO;
            if ("date".equals(type) || "datetime-local".equals(type)) return ControlType.DATE_PICKER;
            if ("range".equals(type)) return ControlType.RANGE_INPUT;
            if ("list".equals(ariaAutocomplete) || "both".equals(ariaAutocomplete)) return ControlType.COMBOBOX;
            return ControlType.TEXTBOX;
        }

        if ("true".equals(attrs.get("hasNestedCheckbox"))) return ControlType.CHECKBOX;
        if ("checkbox".equals(role)) return ControlType.CHECKBOX;
        if ("radio".equals(role)) return ControlType.RADIO;
        if ("tab".equals(role)) return ControlType.TAB;
        if ("menu".equals(role) || "menubar".equals(role)) return ControlType.MENU;
        if ("button".equals(tag) || "button".equals(role)) {
            if ("listbox".equals(ariaHasPopup) || "true".equals(ariaHasPopup)
                    || ariaExpanded != null || hasListbox || hasOptions) {
                return ControlType.CUSTOM_DROPDOWN;
            }
            ControlType iconIntent = iconIntent(ariaLabel + " " + title + " " + className + " " + text, hasSvg);
            if (iconIntent != null) {
                return iconIntent;
            }
            return ControlType.BUTTON;
        }

        if ("a".equals(tag) || "link".equals(role)) return ControlType.LINK;

        if ("label".equals(tag)) return ControlType.LABEL;

        if (tag.matches("h[1-6]") || "heading".equals(role)) return ControlType.HEADING;

        if (!ariaHasPopup.isEmpty() || ariaExpanded != null || hasListbox || hasOptions) {
            return ControlType.CUSTOM_DROPDOWN;
        }

        if (className.contains("select") || className.contains("dropdown") || className.contains("combobox")) {
            return ControlType.CUSTOM_DROPDOWN;
        }

        return ControlType.OTHER;
    }

    @SuppressWarnings("unchecked")
    static Map<String, String> evaluateAttributes(Locator locator) {
        Object result = locator.evaluate("""
                el => {
                  const tag = (el.tagName || '').toLowerCase();
                  const role = el.getAttribute('role') || '';
                  const type = el.getAttribute('type') || '';
                  const ariaHasPopup = el.getAttribute('aria-haspopup') || '';
                  const ariaExpanded = el.getAttribute('aria-expanded');
                  const ariaAutocomplete = el.getAttribute('aria-autocomplete') || '';
                  const className = el.className || '';
                  const text = (el.innerText || '').trim().slice(0, 80);
                  const ariaLabel = el.getAttribute('aria-label') || '';
                  const title = el.getAttribute('title') || '';
                  const parent = el.parentElement;
                  const container = el.closest('[role="listbox"], [role="combobox"]');
                  const hasListbox = !!(container || (parent && parent.querySelector('[role="listbox"]'))
                      || el.querySelector('[role="listbox"]'));
                  const hasSvg = !!(el.querySelector('svg') || el.closest('svg'));
                  const siblingOrChild = parent
                      ? parent.querySelector('[role="option"], [role="listbox"], select')
                      : null;
                  const hasOptions = !!(siblingOrChild || el.querySelector('[role="option"]'));
                  const nestedCheckbox = el.matches('input[type="checkbox"], [role="checkbox"]')
                      || el.querySelector('input[type="checkbox"], [role="checkbox"]')
                      || (el.closest('label') && el.closest('label').querySelector('input[type="checkbox"], [role="checkbox"]'));
                  return {
                    tag,
                    role,
                    type,
                    ariaHasPopup,
                    ariaExpanded: ariaExpanded,
                    ariaAutocomplete,
                    ariaLabel,
                    title,
                    text,
                    className: typeof className === 'string' ? className : '',
                    hasSvg: hasSvg ? 'true' : 'false',
                    hasListbox: hasListbox ? 'true' : 'false',
                    hasOptions: hasOptions ? 'true' : 'false',
                    hasNestedCheckbox: nestedCheckbox ? 'true' : 'false'
                  };
                }
                """);
        if (result instanceof Map<?, ?> map) {
            return (Map<String, String>) (Map<?, ?>) map;
        }
        return Map.of("tag", "", "role", "");
    }

    public static ControlType classifyFromCandidate(ElementCandidate candidate) {
        String tag = lower(candidate.tag());
        String role = lower(candidate.role());
        String type = lower(candidate.inputType());

        if ("select".equals(tag)) return ControlType.NATIVE_SELECT;
        if ("combobox".equals(role)) return ControlType.COMBOBOX;
        if ("listbox".equals(role)) return ControlType.LISTBOX;
        if ("option".equals(role)) return ControlType.OPTION;
        if ("label".equals(tag)) return ControlType.LABEL;
        if (tag.matches("h[1-6]") || "heading".equals(role)) return ControlType.HEADING;
        if ("a".equals(tag) || "link".equals(role)) return ControlType.LINK;
        if ("button".equals(tag) || "button".equals(role)) {
            String semantic = lower(String.join(" ",
                    candidate.accessibleName(), candidate.ariaLabel(), candidate.title(),
                    candidate.className(), candidate.text(), candidate.name(), candidate.id()));
            ControlType iconIntent = iconIntent(semantic, candidate.hasIcon());
            return iconIntent == null ? ControlType.BUTTON : iconIntent;
        }
        if ("checkbox".equals(role) || "checkbox".equals(type)) return ControlType.CHECKBOX;
        if ("radio".equals(role) || "radio".equals(type)) return ControlType.RADIO;
        if ("tab".equals(role)) return ControlType.TAB;
        if ("menu".equals(role) || "menubar".equals(role)) return ControlType.MENU;
        if ("input".equals(tag) || "textarea".equals(tag)) return ControlType.TEXTBOX;

        return ControlType.OTHER;
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private static ControlType iconIntent(String text, boolean hasIcon) {
        String semantic = lower(text);
        if (semantic.contains("profile") || semantic.contains("account") || semantic.contains("my account")
                || semantic.contains("sign in") || semantic.contains("login") || semantic.contains("user")) {
            return ControlType.PROFILE_BUTTON;
        }
        if (semantic.contains("cart") || semantic.contains("bag") || semantic.contains("basket")) {
            return ControlType.CART_BUTTON;
        }
        if (semantic.contains("search") || semantic.contains("find")) {
            return ControlType.SEARCH_BUTTON;
        }
        if (semantic.contains("menu") || semantic.contains("hamburger") || semantic.contains("drawer")) {
            return ControlType.MENU_BUTTON;
        }
        if (semantic.contains("close") || semantic.contains("dismiss")) {
            return ControlType.CLOSE_BUTTON;
        }
        if (semantic.contains("back") || semantic.contains("previous")) {
            return ControlType.BACK_BUTTON;
        }
        if (hasIcon) {
            return ControlType.ICON_BUTTON;
        }
        return null;
    }
}
