package com.smartqa.browser.multimodal;

import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import com.smartqa.intent.ControlPhrase;
import com.smartqa.intent.FilterIntentParser;
import com.smartqa.intent.IntentFilter;
import com.smartqa.intent.LocationHint;
import com.smartqa.intent.SupportedActions;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns raw instructions such as "select Brand AK" or "CHECKBOX [MIDDLE_LEFT] AK = AK"
 * into field + value + control type. Generic only — no website-specific rules.
 */
public final class SemanticTargetNormalizer {

    private static final Pattern SELECT_PREFIX = Pattern.compile(
            "(?i)^(select|choose|check|tick|apply|filter|pick|checkbox)\\s+(.+)$");
    private static final Pattern UNDER = Pattern.compile(
            "(?i)^(.+?)\\s+(?:under|in|from)\\s+(.+)$");
    private static final Pattern LOCATION_BRACKET = Pattern.compile("\\[([A-Za-z_]+)]");
    private static final Pattern EQUALS = Pattern.compile("(?i)^(.+?)\\s*=\\s*(.+)$");
    private static final Set<String> FILTER_FIELD_HINTS = Set.of(
            "brand", "state", "city", "color", "size", "price", "rating", "category",
            "gender", "type", "occasion", "fit", "storage", "ram", "processor",
            "model", "availability", "customer rating", "discount", "material", "pattern");
    private static final Pattern SPATIAL = Pattern.compile(
            "(?i)\\b(top left|top right|middle left|middle right|bottom left|bottom right|top center|bottom center)\\b");

    private SemanticTargetNormalizer() {
    }

    public record NormalizedTarget(
            String action,
            String semanticField,
            String value,
            String targetType,
            String original,
            String location,
            String controlType
    ) {
        public boolean isFilterOption() {
            return TargetType.isFilterOption(targetType);
        }

        public boolean isVisual() {
            return TargetType.isVisual(targetType);
        }

        public String ownedHint() {
            if (semanticField == null || semanticField.isBlank()) {
                return value;
            }
            if (value == null || value.isBlank()) {
                return semanticField;
            }
            return value + " under " + semanticField;
        }
    }

    public static NormalizedTarget normalize(String action, String target) {
        String original = target == null ? "" : target.trim();
        String act = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        String text = original;
        String location = extractLocation(text);
        text = LOCATION_BRACKET.matcher(text).replaceAll(" ").replaceAll("\\s+", " ").trim();
        Matcher spatial = SPATIAL.matcher(text);
        if (location == null && spatial.find()) {
            location = LocationHint.normalize(spatial.group(1).replace(' ', '_'));
            text = spatial.replaceAll(" ").replaceAll("\\s+", " ").trim();
        }

        Matcher prefixed = SELECT_PREFIX.matcher(text);
        if (prefixed.matches()) {
            text = prefixed.group(2).trim();
            if (act.isBlank() || "click".equals(act) || "select".equals(act)) {
                String verb = prefixed.group(1).toLowerCase(Locale.ROOT);
                if (ControlPhrase.hasCheckbox(original) || "checkbox".equals(verb)
                        || "check".equals(verb) || "tick".equals(verb)) {
                    act = SupportedActions.CHECKBOX;
                } else if (ControlPhrase.hasRadio(original)) {
                    act = SupportedActions.RADIO;
                } else if (ControlPhrase.hasDropdown(original)) {
                    act = SupportedActions.SELECT;
                } else {
                    act = SupportedActions.CLICK;
                }
            }
        }
        text = ControlPhrase.stripControlWords(text);

        if (shouldKeepAtomicTarget(act, original, text)) {
            String control = expandLike(act, original) ? "expandable" : act;
            NormalizedTarget result = new NormalizedTarget(
                    act.isBlank() ? SupportedActions.EXPAND : act,
                    null,
                    text.isBlank() ? original : text,
                    TargetType.GENERIC,
                    original,
                    location,
                    control);
            emitNormalized(result);
            return result;
        }

        Matcher equals = EQUALS.matcher(text);
        if (equals.matches()) {
            String left = equals.group(1).trim();
            String right = equals.group(2).trim();
            text = right.equalsIgnoreCase(left) ? left : right;
        }

        Matcher under = UNDER.matcher(text);
        if (under.matches()) {
            String left = under.group(1).trim();
            String right = under.group(2).trim();
            NormalizedTarget result = new NormalizedTarget(
                    act, right, left, TargetType.FILTER_OPTION, original, location, SupportedActions.CHECKBOX);
            emitNormalized(result);
            return result;
        }
        if (looksLikeFilter(act, original) || SupportedActions.FILTER.equals(act) || SupportedActions.CHECKBOX.equals(act)) {
            IntentFilter parsed = FilterIntentParser.parse(text);
            if (parsed != null && parsed.field() != null && parsed.value() != null) {
                NormalizedTarget result = new NormalizedTarget(
                        act.isBlank() ? SupportedActions.CLICK : act,
                        parsed.field(),
                        parsed.value(),
                        TargetType.FILTER_OPTION,
                        original,
                        location,
                        SupportedActions.CHECKBOX);
                emitNormalized(result);
                return result;
            }
            if (parsed != null && parsed.value() == null && parsed.field() != null
                    && text.split("\\s+").length == 1) {
                NormalizedTarget result = new NormalizedTarget(
                        act.isBlank() ? SupportedActions.CHECKBOX : act,
                        null,
                        parsed.field(),
                        TargetType.FILTER_OPTION,
                        original,
                        location,
                        SupportedActions.CHECKBOX);
                emitNormalized(result);
                return result;
            }
        }
        if (text.split("\\s+").length == 2) {
            IntentFilter parsed = FilterIntentParser.parse(text);
            boolean filterVerb = act.contains("select") || act.contains("filter") || act.contains("checkbox");
            boolean clickOnKnownFilterField = act.contains("click")
                    && parsed != null
                    && ControlPhrase.isFilterFieldToken(parsed.field());
            if (parsed != null && parsed.value() != null && parsed.field() != null
                    && (filterVerb || clickOnKnownFilterField)) {
                NormalizedTarget result = new NormalizedTarget(
                        act.isBlank() ? SupportedActions.CLICK : act,
                        parsed.field(),
                        parsed.value(),
                        TargetType.FILTER_OPTION,
                        original,
                        location,
                        SupportedActions.CHECKBOX);
                emitNormalized(result);
                return result;
            }
        }
        String visualType = inferVisualType(original);
        if (visualType != null) {
            NormalizedTarget result = new NormalizedTarget(act, null, text, visualType, original, location, act);
            emitNormalized(result);
            return result;
        }
        NormalizedTarget result = new NormalizedTarget(
                act, null, text.isBlank() ? original : text, TargetType.GENERIC, original, location, act);
        emitNormalized(result);
        return result;
    }

    private static String extractLocation(String text) {
        Matcher matcher = LOCATION_BRACKET.matcher(text == null ? "" : text);
        if (matcher.find()) {
            String normalized = LocationHint.normalize(matcher.group(1));
            if (!LocationHint.AUTO.equals(normalized)) {
                return normalized;
            }
        }
        return null;
    }

    private static String inferVisualType(String target) {
        String blob = target == null ? "" : target.toLowerCase(Locale.ROOT);
        if (blob.contains("banner")) {
            return TargetType.BANNER;
        }
        if (blob.contains("icon") || blob.contains("svg")) {
            return TargetType.ICON;
        }
        if (blob.contains("product card")) {
            return TargetType.PRODUCT_CARD;
        }
        if (blob.contains("card") || blob.contains("tile")) {
            return TargetType.VISUAL_CARD;
        }
        if (blob.contains("canvas")) {
            return TargetType.IMAGE_TARGET;
        }
        boolean imageLike = blob.contains("image") || blob.contains("img") || blob.contains("photo");
        boolean textInImage = blob.contains("text") || blob.contains("title") || blob.contains("caption")
                || blob.contains("containing") || blob.contains("shows") || blob.contains("says");
        if (imageLike && textInImage) {
            return TargetType.IMAGE_TEXT_TARGET;
        }
        if (imageLike) {
            return TargetType.IMAGE_TARGET;
        }
        return null;
    }

    private static boolean shouldKeepAtomicTarget(String action, String original, String text) {
        if (ControlPhrase.hasCheckbox(action, original, text) || SupportedActions.CHECKBOX.equals(action)) {
            return false;
        }
        if (SupportedActions.SELECT.equals(action) && hasExplicitOption(text)) {
            return false;
        }
        if (inferVisualType(original) != null) {
            return false;
        }
        if (expandLike(action, original)) {
            return true;
        }
        return ControlPhrase.looksLikeCompoundControlName(text)
                || ControlPhrase.looksLikeCompoundControlName(original);
    }

    private static boolean expandLike(String action, String original) {
        String blob = ((action == null ? "" : action) + " " + (original == null ? "" : original))
                .toLowerCase(Locale.ROOT);
        return blob.contains("expand")
                || blob.contains("dropdown")
                || blob.contains("combo")
                || blob.contains("open section")
                || blob.contains("open filter");
    }

    private static boolean hasExplicitOption(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String token : ControlPhrase.tokens(text)) {
            if (ControlPhrase.looksLikeOptionCode(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeFilter(String action, String target) {
        String blob = (action + " " + target).toLowerCase(Locale.ROOT);
        if (blob.contains("checkbox") || blob.startsWith("select ")) {
            return true;
        }
        if (blob.contains("filter") && !ControlPhrase.looksLikeCompoundControlName(target)) {
            return true;
        }
        if (expandLike(action, target) || ControlPhrase.looksLikeCompoundControlName(target)) {
            return false;
        }
        if (!hasExplicitOption(target)) {
            return false;
        }
        for (String hint : FILTER_FIELD_HINTS) {
            if (blob.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    private static void emitNormalized(NormalizedTarget result) {
        TraceLogger.info("INTENT", "INTENT_NORMALIZED", "Normalized semantic target", TraceMeta.of(
                "action", result.action(),
                "targetType", result.targetType(),
                "semanticField", result.semanticField() == null ? "" : result.semanticField(),
                "value", result.value(),
                "location", result.location() == null ? "AUTO" : result.location()
        ));
        if (result.isFilterOption()) {
            TraceLogger.info("FILTER", "FILTER_INTENT_NORMALIZED", "Filter intent normalized", TraceMeta.of(
                    "field", result.semanticField() == null ? "" : result.semanticField(),
                    "value", result.value(),
                    "controlType", result.controlType()
            ));
        }
    }
}
