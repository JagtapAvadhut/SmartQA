package com.smartqa.intent;

import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Rewrites a step's action/target/value from explicit control words.
 * "select acuarte ABCD checkbox" → checkbox / ABCD, never dropdown.
 */
public final class ActionSemanticNormalizer {

    public record Rewrite(
            String action,
            String target,
            String value,
            IntentFilter filter,
            String controlType,
            String targetType
    ) {
    }

    private ActionSemanticNormalizer() {
    }

    public static Rewrite rewrite(String action, String target, String value) {
        return rewrite(action, target, value, null);
    }

    public static Rewrite rewrite(String action, String target, String value, String assertion) {
        String act = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        String rawTarget = target == null ? "" : target.trim();
        String rawValue = value == null ? "" : value.trim();
        if ("type".equals(act) || "enter".equals(act) || "fill".equals(act) || "write".equals(act)) {
            act = SupportedActions.INPUT;
        }
        if (looksLikeCss(rawTarget) && !ControlPhrase.hasCheckbox(act, rawTarget, rawValue, assertion)
                && !ControlPhrase.hasRadio(act, rawTarget, rawValue, assertion)) {
            return new Rewrite(act, rawTarget, rawValue, null, "", null);
        }
        String control = ControlPhrase.detect(act, rawTarget, rawValue, assertion);
        String blob = join(act, rawTarget, rawValue, assertion);

        if (ControlPhrase.CHECKBOX.equals(control)) {
            Extracted extracted = extract(blob, rawTarget, rawValue);
            String nextAction = SupportedActions.CHECKBOX;
            String nextTarget = firstNonBlank(extracted.option(), extracted.field(), rawTarget);
            String nextValue = firstNonBlank(extracted.option(), rawValue);
            IntentFilter filter = null;
            if (extracted.field() != null && extracted.option() != null) {
                filter = new IntentFilter(extracted.field(), "equals", extracted.option(), null, null);
            }
            Rewrite result = new Rewrite(
                    nextAction,
                    nextTarget,
                    nextValue,
                    filter,
                    ControlPhrase.CHECKBOX,
                    "FILTER_OPTION");
            emit(result, blob);
            return result;
        }
        if (ControlPhrase.RADIO.equals(control)) {
            Extracted extracted = extract(blob, rawTarget, rawValue);
            String nextTarget = firstNonBlank(extracted.option(), extracted.field(), rawTarget);
            Rewrite result = new Rewrite(
                    SupportedActions.RADIO,
                    nextTarget,
                    firstNonBlank(extracted.option(), rawValue),
                    extracted.field() == null || extracted.option() == null
                            ? null
                            : new IntentFilter(extracted.field(), "equals", extracted.option(), null, null),
                    ControlPhrase.RADIO,
                    "FILTER_OPTION");
            emit(result, blob);
            return result;
        }
        if (ControlPhrase.INPUT.equals(control)) {
            Extracted extracted = extract(blob, rawTarget, rawValue);
            String nextTarget = firstNonBlank(extracted.field(), strip(rawTarget), "input");
            String nextValue = firstNonBlank(extracted.option(), rawValue, extracted.remainder());
            Rewrite result = new Rewrite(
                    SupportedActions.INPUT,
                    nextTarget,
                    nextValue,
                    null,
                    ControlPhrase.INPUT,
                    "INPUT");
            emit(result, blob);
            return result;
        }
        if (ControlPhrase.DROPDOWN.equals(control)) {
            Extracted extracted = extract(blob, rawTarget, rawValue);
            String nextValue = firstNonBlank(extracted.option(), rawValue);
            if (isBlank(nextValue)
                    || ControlPhrase.isFilterFieldToken(nextValue)
                    || ControlPhrase.looksLikeAllFieldHeading(nextValue)) {
                String expandTarget = firstNonBlank(
                        ControlPhrase.stripControlWords(rawTarget), extracted.field(), "dropdown");
                Rewrite result = new Rewrite(
                        SupportedActions.EXPAND,
                        expandTarget,
                        null,
                        null,
                        ControlPhrase.DROPDOWN,
                        "EXPAND");
                emit(result, blob);
                return result;
            }
            Rewrite result = new Rewrite(
                    SupportedActions.SELECT,
                    firstNonBlank(extracted.field(), ControlPhrase.stripControlWords(rawTarget), "dropdown"),
                    nextValue,
                    extracted.field() == null || extracted.option() == null
                            ? null
                            : new IntentFilter(extracted.field(), "equals", extracted.option(), null, null),
                    ControlPhrase.DROPDOWN,
                    "DROPDOWN");
            emit(result, blob);
            return result;
        }
        if (ControlPhrase.BUTTON.equals(control) || ControlPhrase.LINK.equals(control)) {
            Extracted extracted = extract(blob, rawTarget, rawValue);
            String nextTarget = firstNonBlank(extracted.remainder(), extracted.option(), rawTarget);
            Rewrite result = new Rewrite(
                    SupportedActions.CLICK,
                    nextTarget,
                    null,
                    null,
                    control,
                    ControlPhrase.BUTTON.equals(control) ? "BUTTON" : "LINK");
            emit(result, blob);
            return result;
        }
        if (ControlPhrase.VISUAL.equals(control)) {
            Rewrite result = new Rewrite(
                    act.isBlank() ? SupportedActions.CLICK : act,
                    firstNonBlank(rawTarget, rawValue),
                    rawValue,
                    null,
                    ControlPhrase.VISUAL,
                    "VISUAL_TARGET");
            emit(result, blob);
            return result;
        }
        String blobLower = blob.toLowerCase(Locale.ROOT);
        if (blobLower.contains("add to cart") || blobLower.contains("add to bag")
                || blobLower.contains("add to basket") || blobLower.contains("addtocart")) {
            Rewrite result = new Rewrite(
                    SupportedActions.ADD_TO_CART,
                    firstNonBlank(rawTarget, rawValue, "add to cart"),
                    rawValue,
                    null,
                    ControlPhrase.BUTTON,
                    "CART");
            emit(result, blob);
            return result;
        }
        if (blobLower.contains("collapse")) {
            Rewrite result = new Rewrite(
                    SupportedActions.COLLAPSE,
                    firstNonBlank(rawTarget, rawValue),
                    rawValue,
                    null,
                    control,
                    "COLLAPSE");
            emit(result, blob);
            return result;
        }
        if ((blobLower.contains("expand") || looksLikeOpenSection(blobLower))
                && !ControlPhrase.DROPDOWN.equals(control)) {
            Extracted extracted = extract(blob, rawTarget, rawValue);
            String section = firstNonBlank(
                    extracted.field(),
                    stripOpenWords(extracted.remainder()),
                    stripOpenWords(rawTarget),
                    stripOpenWords(rawValue));
            Rewrite result = new Rewrite(
                    SupportedActions.EXPAND,
                    section,
                    null,
                    null,
                    control,
                    "EXPAND");
            emit(result, blob);
            return result;
        }
        if (SupportedActions.SELECT.equals(SupportedActions.canonicalize(act))
                && isBlank(rawValue)
                && !ControlPhrase.DROPDOWN.equals(control)
                && !ControlPhrase.hasDropdown(blob)) {
            Extracted extracted = extract(blob, rawTarget, rawValue);
            if (isBlank(extracted.option()) || ControlPhrase.isFilterFieldToken(extracted.option())
                    || looksLikeOpenSection(blobLower)) {
                String section = firstNonBlank(extracted.field(), stripOpenWords(rawTarget), stripOpenWords(rawValue));
                Rewrite result = new Rewrite(
                        ControlPhrase.isFilterFieldToken(section) ? SupportedActions.EXPAND : SupportedActions.CLICK,
                        section,
                        null,
                        null,
                        control,
                        "EXPAND");
                emit(result, blob);
                return result;
            }
        }
        if (SupportedActions.SELECT.equals(SupportedActions.canonicalize(act))) {
            Extracted extracted = extract(blob, rawTarget, rawValue);
            if (!isBlank(extracted.field()) && !isBlank(extracted.option())) {
                Rewrite result = new Rewrite(
                        SupportedActions.SELECT,
                        extracted.field(),
                        extracted.option(),
                        new IntentFilter(extracted.field(), "equals", extracted.option(), null, null),
                        control,
                        "DROPDOWN");
                emit(result, blob);
                return result;
            }
        }
        if ((blobLower.contains("quantity") || blobLower.contains("qty"))
                && (blobLower.contains("increase") || blobLower.contains("increment")
                || blobLower.contains("plus") || blobLower.contains("+"))) {
            Rewrite result = new Rewrite(
                    SupportedActions.QUANTITY,
                    firstNonBlank(rawTarget, "quantity"),
                    firstNonBlank(rawValue, "+"),
                    null,
                    ControlPhrase.BUTTON,
                    "QUANTITY");
            emit(result, blob);
            return result;
        }
        return new Rewrite(SupportedActions.canonicalize(act), rawTarget, rawValue, null, control, null);
    }

    private static Extracted extract(String blob, String target, String value) {
        String cleaned = ControlPhrase.stripControlWords(join(blob));
        if (ControlPhrase.isGenericTarget(cleaned)) {
            cleaned = "";
        }
        if (ControlPhrase.looksLikeCompoundControlName(cleaned)
                && ControlPhrase.tokens(cleaned).stream().noneMatch(ControlPhrase::looksLikeOptionCode)) {
            return new Extracted(null, null, cleaned);
        }
        List<String> tokens = ControlPhrase.tokens(cleaned);
        String field = null;
        List<String> rest = new ArrayList<>();
        for (String token : tokens) {
            if (ControlPhrase.looksLikeAllFieldHeading(token)) {
                if (field == null && ControlPhrase.isFilterFieldToken(token)) {
                    field = ControlPhrase.singularFilterField(token);
                }
                continue;
            }
            if (ControlPhrase.isJoinerToken(token) && tokens.size() > 1) {
                rest.add(token);
                continue;
            }
            if (field == null && ControlPhrase.isFilterFieldToken(token)) {
                field = ControlPhrase.singularFilterField(token);
            } else if (ControlPhrase.looksLikeNoiseToken(token) && tokens.size() > 1) {
                continue;
            } else if (!ControlPhrase.isGenericTarget(token)) {
                rest.add(token);
            }
        }
        String option = null;
        for (int i = rest.size() - 1; i >= 0; i--) {
            if (ControlPhrase.looksLikeOptionCode(rest.get(i))) {
                option = rest.get(i);
                rest.remove(i);
                break;
            }
        }
        if (option == null && !rest.isEmpty()) {
            option = rest.remove(rest.size() - 1);
        }
        if (option == null && !isBlank(value) && !ControlPhrase.hasCheckbox(value)
                && !ControlPhrase.isGenericTarget(value)) {
            option = ControlPhrase.stripControlWords(value);
        }
        String remainder = String.join(" ", rest).trim();
        return new Extracted(field, option, remainder);
    }

    private static void emit(Rewrite result, String blob) {
        TraceLogger.info("INTENT", "ACTION_SEMANTIC_NORMALIZED", "Normalized action from control words", TraceMeta.of(
                "blob", blob == null ? "" : blob,
                "action", result.action(),
                "target", result.target() == null ? "" : result.target(),
                "value", result.value() == null ? "" : result.value(),
                "controlType", result.controlType() == null ? "" : result.controlType(),
                "targetType", result.targetType() == null ? "" : result.targetType()
        ));
    }

    private static String join(String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(part.trim());
        }
        return builder.toString();
    }

    private static String strip(String text) {
        return ControlPhrase.stripControlWords(text);
    }

    private static boolean looksLikeOpenSection(String blobLower) {
        if (blobLower == null || blobLower.isBlank()) {
            return false;
        }
        return blobLower.contains(" and open")
                || blobLower.endsWith(" open")
                || blobLower.endsWith(" open.")
                || blobLower.contains("open section")
                || blobLower.contains("open filter")
                || (blobLower.contains("open") && blobLower.contains("select") && !blobLower.contains("dropdown"));
    }

    private static String stripOpenWords(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replaceAll("(?i)\\band\\s+open\\b", " ")
                .replaceAll("(?i)\\bopen\\b", " ")
                .replaceAll("(?i)\\bselect\\b", " ")
                .replaceAll("[.]+$", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean looksLikeCss(String target) {
        if (target == null || target.isBlank()) {
            return false;
        }
        String trimmed = target.trim();
        return trimmed.startsWith(".")
                || trimmed.startsWith("#")
                || trimmed.startsWith("//")
                || trimmed.contains("[")
                || trimmed.contains("xpath=");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private record Extracted(String field, String option, String remainder) {
    }
}
