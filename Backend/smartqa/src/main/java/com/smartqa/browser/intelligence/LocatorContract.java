package com.smartqa.browser.intelligence;

import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.SmartQaException;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;

import java.util.Locale;
import java.util.Set;

/**
 * Strict locator payload validation before Playwright execution.
 * Rejects malformed type/value combinations so failures surface as LOCATOR_INVALID
 * instead of opaque Playwright actionability errors.
 */
public final class LocatorContract {

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "css", "text", "role", "label", "placeholder", "title", "scroll"
    );

    private LocatorContract() {
    }

    public record Validation(
            boolean valid,
            String locatorType,
            String locatorValue,
            String reason,
            String sourceComponent,
            String originalCandidate
    ) {
        public static Validation ok(String type, String value, String source) {
            return new Validation(true, type, value, null, source, format(type, value));
        }

        public static Validation reject(String type, String value, String reason, String source) {
            return new Validation(false, type, value, reason, source, format(type, value));
        }

        private static String format(String type, String value) {
            String t = type == null ? "" : type;
            String v = value == null ? "" : value;
            if (t.isBlank() && v.isBlank()) {
                return "(missing)";
            }
            return t + "=" + v;
        }
    }

    public static Validation validate(String locatorType, String locatorValue, String sourceComponent) {
        String source = sourceComponent == null || sourceComponent.isBlank() ? "LocatorContract" : sourceComponent;
        String typeRaw = locatorType == null || locatorType.isBlank() ? "css" : locatorType.trim();
        String type = typeRaw.toLowerCase(Locale.ROOT);
        if (!SUPPORTED_TYPES.contains(type)) {
            return Validation.reject(locatorType, locatorValue, "unsupported locator type: " + locatorType, source);
        }
        if (locatorValue == null) {
            return Validation.reject(typeRaw, null, "missing locator value", source);
        }
        String trimmed = locatorValue.trim();
        if (trimmed.isEmpty()) {
            return Validation.reject(typeRaw, locatorValue, "empty selector", source);
        }
        if ("=".equals(trimmed) || "==".equals(trimmed)) {
            return Validation.reject(typeRaw, locatorValue, "malformed selector equals-only payload", source);
        }
        if ("#".equals(trimmed) || ".".equals(trimmed) || "*".equals(trimmed)) {
            return Validation.reject(typeRaw, locatorValue, "incomplete css selector token", source);
        }
        if ("null".equalsIgnoreCase(trimmed) || "undefined".equalsIgnoreCase(trimmed)) {
            return Validation.reject(typeRaw, locatorValue, "nullish selector literal", source);
        }
        if (trimmed.startsWith("=") || (trimmed.endsWith("=") && trimmed.length() <= 2)) {
            return Validation.reject(typeRaw, locatorValue, "unsupported equals-prefixed selector syntax", source);
        }
        if ("role".equals(type)) {
            String[] parts = trimmed.split("\\|", 2);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                return Validation.reject(typeRaw, locatorValue, "role locator requires role|name", source);
            }
        }
        if ("css".equals(type) && looksLikeBrokenAttrSelector(trimmed)) {
            return Validation.reject(typeRaw, locatorValue, "unsupported css attribute syntax", source);
        }
        return Validation.ok(type, trimmed, source);
    }

    public static boolean isUsable(String locatorType, String locatorValue) {
        return validate(locatorType, locatorValue, "LocatorContract").valid();
    }

    public static boolean isUsableCss(String selector) {
        return validate("css", selector, "LocatorContract").valid();
    }

    public static void requireValid(String locatorType, String locatorValue, String sourceComponent) {
        Validation validation = validate(locatorType, locatorValue, sourceComponent);
        if (validation.valid()) {
            return;
        }
        TraceLogger.warn("LOCATOR", "LOCATOR_INVALID", validation.reason(), TraceMeta.of(
                "locatorType", nullToEmpty(validation.locatorType()),
                "locatorValue", nullToEmpty(validation.locatorValue()),
                "reason", validation.reason(),
                "sourceComponent", validation.sourceComponent(),
                "originalCandidate", validation.originalCandidate()
        ));
        throw new SmartQaException(
                ErrorCode.LOCATOR_INVALID,
                "LOCATOR_INVALID: " + validation.reason()
                        + " | type=" + nullToEmpty(validation.locatorType())
                        + " | value=" + nullToEmpty(validation.locatorValue())
                        + " | source=" + validation.sourceComponent()
                        + " | candidate=" + validation.originalCandidate());
    }

    private static boolean looksLikeBrokenAttrSelector(String selector) {
        // e.g. [name=] or [href='] — attribute operator with empty value
        return selector.matches(".*\\[[^=\\]]+=\\s*['\"]?\\s*['\"]?\\].*")
                || selector.matches("^\\s*=.*");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
