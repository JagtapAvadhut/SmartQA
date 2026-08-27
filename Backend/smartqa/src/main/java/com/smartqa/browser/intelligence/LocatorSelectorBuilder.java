package com.smartqa.browser.intelligence;

import java.util.Locale;
import java.util.Set;

public final class LocatorSelectorBuilder {

    private static final Set<String> VOLATILE_ID_MARKERS = Set.of(":r", "ember", "mui", "cdk", "ng-", "v-");

    private LocatorSelectorBuilder() {
    }

    public static java.util.List<RankedLocator> fromElement(ElementCandidate element, String action) {
        java.util.List<RankedLocator> locators = new java.util.ArrayList<>();
        if (notBlank(element.testId())) {
            addIfValid(locators, "css", "[data-testid='" + css(element.testId()) + "']", 0.98, "data-testid", 150);
        }
        String name = firstNonBlank(element.accessibleName(), element.text(), element.ariaLabel());
        String role = inferRole(element, action);
        boolean skipButtonRole = ("search".equalsIgnoreCase(action) || "input".equalsIgnoreCase(action))
                && "button".equalsIgnoreCase(role);
        if (notBlank(role) && notBlank(name) && !skipButtonRole) {
            addIfValid(locators, "role", role + "|" + name, 0.94, "role+name", 130);
        }
        if (notBlank(element.ariaLabel())) {
            addIfValid(locators, "label", element.ariaLabel(), 0.9, "aria-label", 110);
            addIfValid(locators, "css", "[aria-label='" + css(element.ariaLabel()) + "']", 0.87, "aria-label-css", 108);
        }
        if (notBlank(element.title())) {
            addIfValid(locators, "css", "[title='" + css(element.title()) + "']", 0.86, "title-css", 104);
        }
        if (notBlank(element.placeholder())) {
            addIfValid(locators, "placeholder", element.placeholder(), 0.88, "placeholder", 100);
        }
        if (notBlank(element.name())) {
            addIfValid(locators, "css", "[name='" + css(element.name()) + "']", 0.86, "name", 95);
            String formId = element.structureOrEmpty().formId();
            if (notBlank(formId)) {
                addIfValid(locators, "css",
                        "form#" + css(formId) + " [name='" + css(element.name()) + "']",
                        0.92, "form-scoped-name", 115);
            }
        }
        if (stableId(element.id())) {
            addIfValid(locators, "css", "#" + element.id(), 0.84, "id", 90);
        }
        if (notBlank(element.actionableSelector()) && isUsableCss(element.actionableSelector())) {
            // Prefer the clickable host over decorative icon leaves for click actions.
            double actionableConfidence = "click".equalsIgnoreCase(action) ? 0.91 : 0.83;
            double actionableStability = "click".equalsIgnoreCase(action) ? 118 : 89;
            addIfValid(locators, "css", element.actionableSelector(),
                    actionableConfidence, "actionable-ancestor", actionableStability);
            if (looksIdAnchored(element.actionableSelector())) {
                addIfValid(locators, "css", element.actionableSelector(), 0.93, "actionable-ancestor-id", 120);
            }
        }
        if (notBlank(element.targetPath()) && !isDecorativeLeafPath(element) && isUsableCss(element.targetPath())) {
            addIfValid(locators, "css", element.targetPath(), 0.8, "dom-path", 84);
            if (looksIdAnchored(element.targetPath())) {
                addIfValid(locators, "css", element.targetPath(), 0.89, "dom-path-id", 112);
            }
        } else if (notBlank(element.targetPath()) && notBlank(element.actionableSelector())
                && isUsableCss(element.targetPath())
                && !element.targetPath().equals(element.actionableSelector())) {
            // Keep a low-priority fallback only when no better path exists later.
            addIfValid(locators, "css", element.targetPath(), 0.55, "decorative-dom-path", 40);
        }
        if (notBlank(element.text()) && element.text().length() <= 80) {
            addIfValid(locators, "text", element.text(), 0.8, "visible-text", 85);
        }
        return locators;
    }

    private static void addIfValid(
            java.util.List<RankedLocator> locators,
            String type,
            String value,
            double confidence,
            String reason,
            double stability) {
        LocatorContract.Validation validation = LocatorContract.validate(type, value, "LocatorSelectorBuilder");
        if (!validation.valid()) {
            return;
        }
        locators.add(new RankedLocator(type, value, confidence, reason, stability));
    }

    static String inferRole(ElementCandidate element, String action) {
        String role = element.role() == null ? "" : element.role().toLowerCase(Locale.ROOT);
        if (!role.isBlank()) {
            return switch (role) {
                case "button", "link", "textbox", "searchbox", "combobox", "checkbox", "radio", "heading" -> role;
                default -> role;
            };
        }
        String tag = element.tag() == null ? "" : element.tag().toLowerCase(Locale.ROOT);
        String type = element.inputType() == null ? "" : element.inputType().toLowerCase(Locale.ROOT);
        if ("a".equals(tag)) {
            return "link";
        }
        if ("button".equals(tag) || "submit".equals(type) || "button".equals(type)) {
            return "button";
        }
        if ("input".equals(tag) || "textarea".equals(tag)) {
            if ("checkbox".equals(type)) {
                return "checkbox";
            }
            if ("search".equals(type) || "search".equalsIgnoreCase(action)) {
                return "searchbox";
            }
            return "textbox";
        }
        if ("h1".equals(tag) || "h2".equals(tag) || "h3".equals(tag)) {
            return "heading";
        }
        return "click".equals(action) || "hover".equals(action) ? "link" : "textbox";
    }

    static boolean stableId(String id) {
        if (!notBlank(id) || id.length() > 64) {
            return false;
        }
        String lower = id.toLowerCase(Locale.ROOT);
        if (id.matches(".*[0-9a-f]{8,}.*")) {
            return false;
        }
        return VOLATILE_ID_MARKERS.stream().noneMatch(lower::contains);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isUsableCss(String selector) {
        return LocatorContract.isUsableCss(selector);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (notBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private static String css(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private static boolean looksIdAnchored(String selector) {
        return selector != null && selector.contains("#");
    }

    /** Decorative leaves are poor click targets; prefer actionable ancestors instead. */
    private static boolean isDecorativeLeafPath(ElementCandidate element) {
        String tag = element.tag() == null ? "" : element.tag().toLowerCase(Locale.ROOT);
        if ("i".equals(tag) || "svg".equals(tag) || "path".equals(tag)) {
            return true;
        }
        String path = element.targetPath() == null ? "" : element.targetPath().toLowerCase(Locale.ROOT);
        return path.endsWith(" > i") || path.endsWith(" > svg") || path.endsWith(" > path");
    }
}
