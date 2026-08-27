package com.smartqa.browser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Replays verified locator-cloud alternatives when the primary locator is stale.
 * Does not invent selectors.
 */
public final class LocatorHealingResolver {

    private LocatorHealingResolver() {
    }

    public record Alternative(String type, String value, double confidence) {
    }

    public static List<Alternative> parseCloud(String locatorCloud) {
        List<Alternative> out = new ArrayList<>();
        if (locatorCloud == null || locatorCloud.isBlank()) {
            return List.of();
        }
        for (String part : locatorCloud.split(" \\| ")) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            int at = token.lastIndexOf('@');
            String body = at > 0 ? token.substring(0, at).trim() : token;
            double confidence = 0.5;
            if (at > 0) {
                try {
                    confidence = Double.parseDouble(token.substring(at + 1).trim());
                } catch (NumberFormatException ignored) {
                }
            }
            int colon = body.indexOf(':');
            if (colon <= 0 || colon == body.length() - 1) {
                continue;
            }
            String type = body.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = body.substring(colon + 1).trim();
            if (!value.isEmpty()) {
                out.add(new Alternative(type, value, confidence));
            }
        }
        return out;
    }

    public record Hit(String type, String value, Locator locator, double confidence) {
    }

    public static Optional<Locator> firstLive(Page page, String locatorCloud) {
        return firstHit(page, locatorCloud).map(Hit::locator);
    }

    public static Optional<Hit> firstHit(Page page, String locatorCloud) {
        if (page == null) {
            return Optional.empty();
        }
        for (Alternative alternative : parseCloud(locatorCloud)) {
            Locator locator = toLocator(page, alternative);
            Locator visible = VisibleLocatorPicker.firstVisibleOrControl(locator);
            if (visible != null) {
                TraceLogger.info("LOCATOR", "LOCATOR_CLOUD_HIT", "Healed via locator cloud alternative", TraceMeta.of(
                        "locatorType", alternative.type(),
                        "confidence", alternative.confidence()
                ));
                return Optional.of(new Hit(alternative.type(), alternative.value(), visible, alternative.confidence()));
            }
        }
        return Optional.empty();
    }

    static Locator toLocator(Page page, Alternative alternative) {
        if (page == null || alternative == null) {
            return null;
        }
        String type = alternative.type();
        String value = alternative.value();
        return switch (type) {
            case "text" -> page.getByText(value);
            case "label" -> page.getByLabel(value);
            case "placeholder" -> page.getByPlaceholder(value);
            case "role" -> roleLocator(page, value);
            case "css", "xpath" -> page.locator(value);
            default -> page.locator(value);
        };
    }

    private static Locator roleLocator(Page page, String spec) {
        if (spec == null || !spec.contains("|")) {
            return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(spec));
        }
        String[] parts = spec.split("\\|", 2);
        AriaRole role = switch (parts[0].toLowerCase(Locale.ROOT)) {
            case "button" -> AriaRole.BUTTON;
            case "link" -> AriaRole.LINK;
            case "textbox" -> AriaRole.TEXTBOX;
            case "searchbox" -> AriaRole.SEARCHBOX;
            case "combobox" -> AriaRole.COMBOBOX;
            case "checkbox" -> AriaRole.CHECKBOX;
            case "radio" -> AriaRole.RADIO;
            case "option" -> AriaRole.OPTION;
            default -> AriaRole.BUTTON;
        };
        return page.getByRole(role, new Page.GetByRoleOptions().setName(parts[1]));
    }
}
