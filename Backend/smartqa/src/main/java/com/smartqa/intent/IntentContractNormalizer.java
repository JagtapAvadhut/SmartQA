package com.smartqa.intent;

import com.smartqa.common.json.JsonSupport;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Locale;

/**
 * Normalizes common AI JSON shape drift into SmartQA IntentContract fields.
 * Generic only: no website-specific rules.
 */
@Component
public class IntentContractNormalizer {

    private final JsonMapper objectMapper;

    public IntentContractNormalizer(JsonMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public IntentContract parse(String raw) {
        String json = JsonSupport.extractJson(raw);
        JsonNode root = objectMapper.readTree(json);
        if (!(root instanceof ObjectNode object)) {
            return objectMapper.convertValue(root, IntentContract.class);
        }
        normalizeRoot(object);
        IntentIdUniquifier.uniquify(object);
        persistSequentialDependsOn(object);
        IntentContract contract = objectMapper.convertValue(object, IntentContract.class);
        TraceLogger.info("INTENT", "INTENT_NORMALIZED", "Normalized AI intent contract fields", TraceMeta.of(
                "status", contract.status(),
                "scenarios", contract.scenarios() == null ? 0 : contract.scenarios().size()
        ));
        return contract;
    }

    private void normalizeRoot(ObjectNode root) {
        normalizeStatus(root);
        normalizeConfidence(root);
        JsonNode scenarios = root.get("scenarios");
        if (scenarios instanceof ArrayNode array) {
            for (int i = 0; i < array.size(); i++) {
                JsonNode scenarioNode = array.get(i);
                if (scenarioNode instanceof ObjectNode scenario) {
                    ensureText(scenario, "id", "s" + (i + 1));
                    ensureText(scenario, "name", "Scenario " + (i + 1));
                    normalizeSteps(scenario, scenario.path("id").asText("s" + (i + 1)));
                }
            }
        }
        if (!root.has("clarifications") || root.get("clarifications").isNull()) {
            root.putArray("clarifications");
        }
    }

    private void normalizeSteps(ObjectNode scenario, String scenarioId) {
        JsonNode stepsNode = scenario.get("steps");
        if (!(stepsNode instanceof ArrayNode steps)) {
            return;
        }
        for (int i = 0; i < steps.size(); i++) {
            JsonNode stepNode = steps.get(i);
            if (!(stepNode instanceof ObjectNode step)) {
                continue;
            }
            ensureText(step, "id", scenarioId + "_step" + (i + 1));
            nullifyBlankObjectFields(step, "filter");
            // AI often emits object-shaped string fields (value/target); coerce before Jackson bind.
            coerceScalarStringFields(step,
                    "action", "type", "verb", "target", "value", "assertion", "location",
                    "url", "text", "element", "control", "selectorLabel", "name", "description",
                    "label", "instruction", "query", "key", "expected", "expectedText", "input",
                    "option", "item", "choice", "field", "dropdown", "button", "link",
                    "ui_element", "elementName", "accessibleName", "what", "to", "for",
                    "controlType", "targetType", "scenarioId", "containerContext",
                    "expectedState", "timeoutPolicy", "recoveryPolicy",
                    "filterField", "filterValue", "filter_field", "filter_value");
            nullifyBlankStringFields(step, "target", "value", "assertion", "location", "url", "text", "element");
            String action = firstNonBlank(
                    text(step, "action"),
                    text(step, "type"),
                    text(step, "verb")
            ).toLowerCase(Locale.ROOT).trim();
            // Ollama often emits "click profile icon" as the action itself.
            String[] split = splitCompoundAction(action);
            if (split != null) {
                action = split[0];
                if (isBlank(text(step, "target")) && !isBlank(split[1])) {
                    step.put("target", split[1]);
                }
            }
            if ("type".equals(action) || "enter".equals(action) || "fill".equals(action) || "write".equals(action)) {
                action = SupportedActions.INPUT;
            }
            if (!action.isBlank()) {
                step.put("action", action);
            }
            String target = firstNonBlank(
                    text(step, "target"),
                    text(step, "element"),
                    text(step, "control"),
                    text(step, "selectorLabel"),
                    text(step, "description"),
                    text(step, "label"),
                    text(step, "instruction"),
                    text(step, "ui_element"),
                    text(step, "elementName"),
                    text(step, "accessibleName"),
                    text(step, "button"),
                    text(step, "link"),
                    text(step, "field"),
                    text(step, "dropdown"),
                    text(step, "what"),
                    text(step, "to"),
                    text(step, "for"),
                    text(step, "name")
            );
            String value = firstNonBlank(
                    text(step, "value"),
                    text(step, "url"),
                    text(step, "text"),
                    text(step, "input"),
                    text(step, "query"),
                    text(step, "option"),
                    text(step, "item"),
                    text(step, "choice"),
                    text(step, "expected"),
                    text(step, "expectedText")
            );
            if (isBlank(target)) {
                target = salvageSemanticLabel(step);
            }
            String assertion = firstNonBlank(
                    text(step, "assertion"),
                    text(step, "condition")
            );
            String location = firstNonBlank(
                    text(step, "location"),
                    text(step, "region"),
                    text(step, "area")
            );
            step.put("location", LocationHint.normalize(location));
            step.put("scenarioId", scenarioId);
            normalizeFilterNode(step);
            normalizeStringArray(step, "dependsOn");
            normalizeStringArray(step, "preconditions");
            normalizeStringArray(step, "postconditions");
            normalizeStringArray(step, "semanticConstraints");

            ActionSemanticNormalizer.Rewrite rewrite = ActionSemanticNormalizer.rewrite(action, target, value, assertion);
            if (!rewrite.action().isBlank()) {
                action = rewrite.action();
                step.put("action", action);
            }
            if (!isBlank(rewrite.target())) {
                target = rewrite.target();
                step.put("target", rewrite.target());
            }
            if (rewrite.value() != null) {
                value = rewrite.value();
                if (!isBlank(value)) {
                    step.put("value", value);
                }
            }
            if (rewrite.filter() != null) {
                ObjectNode filterNode = step.putObject("filter");
                if (!isBlank(rewrite.filter().field())) {
                    filterNode.put("field", rewrite.filter().field());
                }
                if (!isBlank(rewrite.filter().operator())) {
                    filterNode.put("operator", rewrite.filter().operator());
                }
                if (!isBlank(rewrite.filter().value())) {
                    filterNode.put("value", rewrite.filter().value());
                }
            }
            if (!isBlank(rewrite.controlType())) {
                step.put("controlType", rewrite.controlType());
            }
            if (!isBlank(rewrite.targetType())) {
                step.put("targetType", rewrite.targetType());
            }

            if ("navigate".equals(action)) {
                if (isBlank(text(step, "target")) && !isBlank(target) && !looksLikeUrl(target)) {
                    step.put("target", semanticize(target));
                }
                if (isBlank(text(step, "value")) && looksLikeUrl(value)) {
                    step.put("value", value);
                } else if (isBlank(text(step, "value")) && looksLikeUrl(target)) {
                    step.put("value", target);
                } else if (isBlank(text(step, "target")) && !isBlank(target)) {
                    step.put("target", semanticize(target));
                }
                if (isBlank(text(step, "value")) && looksLikeUrl(text(step, "url"))) {
                    step.put("value", text(step, "url"));
                }
                // Prefer URL in value when target accidentally holds the URL.
                if (looksLikeUrl(text(step, "target")) && isBlank(text(step, "value"))) {
                    step.put("value", text(step, "target"));
                    step.put("target", "application");
                }
            } else if ("find".equals(action) || "locate".equals(action) || "look".equals(action)
                    || "look_for".equals(action) || "lookfor".equals(action)) {
                // Generic alias: "find X section" → scroll; otherwise click the named control.
                String findTarget = firstNonBlank(text(step, "target"), target, value, text(step, "text"));
                String lowerFind = findTarget.toLowerCase(Locale.ROOT);
                boolean scrollLike = lowerFind.contains("section")
                        || lowerFind.contains("heading")
                        || lowerFind.contains("details")
                        || lowerFind.startsWith("scroll");
                if (scrollLike) {
                    step.put("action", SupportedActions.SCROLL);
                    step.put("target", findTarget.isBlank() ? "down" : semanticize(findTarget));
                } else if (looksLikeSearchQuery(findTarget, value)) {
                    step.put("action", SupportedActions.SEARCH);
                    String query = firstNonBlank(value, findTarget);
                    step.put("target", "search");
                    if (!isBlank(query)) {
                        step.put("value", query);
                    }
                } else if (!findTarget.isBlank()) {
                    step.put("action", SupportedActions.CLICK);
                    step.put("target", semanticize(findTarget));
                } else {
                    step.put("action", SupportedActions.SCROLL);
                    step.put("target", "down");
                }
            } else if (action.contains("tab") && (action.contains("switch") || action.contains("new")
                    || action.contains("window"))) {
                step.put("action", SupportedActions.SWITCH_TO_NEW_TAB);
            } else if ("press_key".equals(action) || "press".equals(action) || "keydown".equals(action)) {
                step.put("action", SupportedActions.PRESS_KEY);
                String key = firstNonBlank(
                        text(step, "value"),
                        value,
                        text(step, "key"),
                        text(step, "target"),
                        target
                );
                if (isBlank(key) || isEnterLike(key)) {
                    step.put("value", "Enter");
                } else {
                    step.put("value", key.trim());
                }
                // press_key does not need a semantic target
                if (!isBlank(text(step, "target")) && isEnterLike(text(step, "target"))) {
                    step.putNull("target");
                }
            } else if ("scroll".equals(action) || "scroll_to".equals(action) || "scrollto".equals(action)) {
                step.put("action", SupportedActions.SCROLL);
                String scrollTarget = firstNonBlank(text(step, "target"), target, text(step, "value"), value, "down");
                if (scrollTarget.toLowerCase(Locale.ROOT).startsWith("scroll to ")) {
                    scrollTarget = scrollTarget.substring("scroll to ".length()).trim();
                } else if (scrollTarget.toLowerCase(Locale.ROOT).startsWith("scroll ")) {
                    scrollTarget = scrollTarget.substring("scroll ".length()).trim();
                }
                step.put("target", scrollTarget);
            } else if ("select".equals(action)) {
                String selectTarget = firstNonBlank(
                        text(step, "target"),
                        target,
                        text(step, "field"),
                        text(step, "dropdown"),
                        text(step, "label"),
                        text(step, "control")
                );
                String selectValue = firstNonBlank(
                        text(step, "value"),
                        value,
                        text(step, "option"),
                        text(step, "item"),
                        text(step, "choice"),
                        text(step, "text")
                );
                // "select ESS" with empty target — keep value, invent generic dropdown target.
                if (isBlank(selectTarget) && !isBlank(selectValue)) {
                    selectTarget = "dropdown";
                }
                if (isBlank(selectValue) && !isBlank(selectTarget)
                        && !selectTarget.toLowerCase(Locale.ROOT).contains("dropdown")
                        && !selectTarget.toLowerCase(Locale.ROOT).contains("role")
                        && !selectTarget.toLowerCase(Locale.ROOT).contains("status")) {
                    // Model put the option in target.
                    selectValue = selectTarget;
                    selectTarget = "dropdown";
                }
                if (!isBlank(selectTarget)) {
                    step.put("target", semanticize(selectTarget));
                }
                if (!isBlank(selectValue)) {
                    step.put("value", selectValue);
                }
            } else if ("search".equals(action)) {
                String searchTarget = firstNonBlank(
                        text(step, "target"),
                        target,
                        "search"
                );
                String searchValue = firstNonBlank(
                        text(step, "value"),
                        value,
                        text(step, "query"),
                        text(step, "text")
                );
                // If model put the query in target and left value empty, promote it.
                if (isBlank(searchValue) && !isBlank(searchTarget)
                        && !"search".equalsIgnoreCase(searchTarget)
                        && !searchTarget.toLowerCase().contains("search bar")
                        && !searchTarget.toLowerCase().contains("search box")
                        && !searchTarget.toLowerCase().contains("search input")) {
                    searchValue = searchTarget;
                    searchTarget = "search";
                }
                if (isBlank(searchTarget)) {
                    searchTarget = "search";
                }
                step.put("target", semanticize(searchTarget));
                if (!isBlank(searchValue)) {
                    step.put("value", searchValue);
                }
            } else if ("verify".equals(action)) {
                if (isBlank(text(step, "target")) && !isBlank(target)) {
                    step.put("target", semanticize(target));
                }
                String verifyTarget = text(step, "target");
                if (verifyTarget.matches("(?i)text|label|heading|message") && !isBlank(text(step, "value"))) {
                    step.put("target", semanticize(text(step, "value")));
                }
                if (verifyTarget.toLowerCase(Locale.ROOT).startsWith("text as ")) {
                    step.put("target", semanticize(verifyTarget.substring(8).trim()));
                }
                if (isBlank(text(step, "target")) && !isBlank(value)) {
                    step.put("target", semanticize(value));
                }
                if (isBlank(text(step, "value")) && !isBlank(value) && !value.equals(text(step, "target"))) {
                    step.put("value", value);
                }
                if (isBlank(text(step, "assertion")) && !isBlank(assertion)) {
                    step.put("assertion", assertion);
                }
                if (isBlank(text(step, "assertion")) && isBlank(text(step, "value"))) {
                    step.put("assertion", "contains");
                }
            } else {
                if (isBlank(text(step, "target")) && !isBlank(target)) {
                    step.put("target", semanticize(target));
                } else if (!isBlank(text(step, "target"))) {
                    step.put("target", semanticize(text(step, "target")));
                }
                // Click/hover often put the label only in value/text — promote to target.
                if (("click".equals(action) || "hover".equals(action) || "checkbox".equals(action))
                        && isBlank(text(step, "target"))) {
                    String promoted = firstNonBlank(value, salvageSemanticLabel(step));
                    if (!isBlank(promoted) && !looksLikeUrl(promoted)) {
                        step.put("target", semanticize(promoted));
                    }
                }
                if (isBlank(text(step, "value")) && !isBlank(value) && !looksLikeUrl(value)) {
                    if (!"click".equals(action) && !"hover".equals(action) && !"checkbox".equals(action)) {
                        step.put("value", value);
                    }
                }
                if (("input".equals(action) || "select".equals(action))
                        && isBlank(text(step, "value"))
                        && !isBlank(value)) {
                    step.put("value", value);
                }
                if ("select".equals(text(step, "action")) && isBlank(text(step, "value"))) {
                    String section = text(step, "target")
                            .replaceAll("(?i)\\band\\s+open\\b", " ")
                            .replaceAll("(?i)\\bopen\\b", " ")
                            .replaceAll("(?i)\\bselect\\b", " ")
                            .replaceAll("[.]+$", "")
                            .replaceAll("\\s+", " ")
                            .trim();
                    if (!section.isBlank()) {
                        step.put("target", section);
                        step.put("action", ControlPhrase.isFilterFieldToken(section)
                                ? SupportedActions.EXPAND
                                : SupportedActions.CLICK);
                    }
                }
                if ("input".equals(action) && isBlank(text(step, "target"))) {
                    String inputTarget = firstNonBlank(target, salvageSemanticLabel(step), "input");
                    step.put("target", semanticize(inputTarget));
                }
            }
            if (SupportedActions.FILTER.equals(text(step, "action"))) {
                salvageFilter(step);
            }
            if ("click".equals(text(step, "action"))
                    && !isBlank(text(step, "value"))
                    && looksLikeEditableField(text(step, "target"))) {
                step.put("action", SupportedActions.INPUT);
            }
            UrlNavigationNormalizer.Rewrite rewritten = UrlNavigationNormalizer.rewriteFields(
                    text(step, "action"), text(step, "target"), text(step, "value"));
            if (rewritten.changed()) {
                step.put("action", rewritten.action());
                if (!isBlank(rewritten.target())) {
                    step.put("target", rewritten.target());
                }
                if (!isBlank(rewritten.value())) {
                    step.put("value", rewritten.value());
                }
            }
            String canonical = SupportedActions.canonicalize(text(step, "action"));
            if (!isBlank(canonical)) {
                step.put("action", canonical);
            }
        }
        salvageSearchSpecificity(steps);
    }

    /**
     * Split phrases like {@code click profile icon} into action + target.
     */
    private static String[] splitCompoundAction(String action) {
        if (action == null || action.isBlank() || !action.contains(" ")) {
            return null;
        }
        String lower = action.trim().toLowerCase(Locale.ROOT);
        String[] verbs = {
                "navigate", "click", "input", "type", "enter", "fill", "write", "select",
                "verify", "search", "hover", "scroll", "wait", "press", "find", "locate", "check",
                "choose", "tick", "uncheck", "untick", "open", "visit", "go",
                "expand", "collapse", "add", "increase"
        };
        for (String verb : verbs) {
            if (lower.equals(verb)) {
                return null;
            }
            if (lower.startsWith(verb + " ")) {
                String rest = action.trim().substring(verb.length()).trim();
                if (rest.isBlank()) {
                    return null;
                }
                // Strip common filler words.
                rest = rest.replaceFirst("(?i)^(on|the|a|an)\\s+", "").trim();
                if ("go".equals(verb)) {
                    rest = rest.replaceFirst("(?i)^to\\s+", "").trim();
                }
                if ("verify".equals(verb)) {
                    rest = rest.replaceFirst("(?i)^(that\\s+)?(the\\s+)?(text|heading|label|message)\\s+(as|is|:)?\\s*", "").trim();
                }
                if (("open".equals(verb) || "visit".equals(verb) || "go".equals(verb) || "goto".equals(verb))
                        && UrlNavigationNormalizer.looksLikeHttpUrl(rest)) {
                    return new String[]{SupportedActions.NAVIGATE, rest};
                }
                if ("open".equals(verb) || "visit".equals(verb)) {
                    return new String[]{SupportedActions.CLICK, rest};
                }
                if ("add".equals(verb) && rest.toLowerCase(Locale.ROOT).contains("cart")) {
                    return new String[]{SupportedActions.ADD_TO_CART, rest};
                }
                if ("increase".equals(verb) && (rest.toLowerCase(Locale.ROOT).contains("qty")
                        || rest.toLowerCase(Locale.ROOT).contains("quantity"))) {
                    return new String[]{SupportedActions.QUANTITY, rest};
                }
                if ("expand".equals(verb)) {
                    return new String[]{SupportedActions.EXPAND, rest};
                }
                if ("collapse".equals(verb)) {
                    return new String[]{SupportedActions.COLLAPSE, rest};
                }
                return new String[]{verb, rest};
            }
        }
        return null;
    }

    /** Last-resort: any leftover semantic string field on the step object. */
    private static String salvageSemanticLabel(ObjectNode step) {
        if (step == null) {
            return "";
        }
        java.util.Set<String> skip = java.util.Set.of(
                "id", "action", "type", "verb", "location", "filter", "status",
                "confidence", "url", "assertion", "condition",
                "controlType", "targetType", "scenarioId", "containerContext",
                "dependsOn", "preconditions", "postconditions", "expectedState",
                "timeoutPolicy", "recoveryPolicy", "semanticConstraints",
                "value", "text", "input", "query", "option", "item", "choice",
                "expected", "expectedText", "key"
        );
        var fields = step.propertyNames();
        for (String name : fields) {
            if (name == null || skip.contains(name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            String candidate = text(step, name);
            if (candidate.isBlank() || looksLikeUrl(candidate) || candidate.length() > 120) {
                continue;
            }
            if (SupportedActions.ALL.contains(candidate.toLowerCase(Locale.ROOT))) {
                continue;
            }
            return candidate;
        }
        return "";
    }

    /**
     * Coerce AI shape drift where string fields arrive as objects/arrays/numbers.
     * Prevents Jackson: Cannot deserialize String from Object (START_OBJECT).
     */
    private static void coerceScalarStringFields(ObjectNode step, String... fields) {
        for (String field : fields) {
            JsonNode value = step.get(field);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isTextual()) {
                continue;
            }
            if (value.isNumber() || value.isBoolean()) {
                step.put(field, value.asText());
                continue;
            }
            if (value instanceof ObjectNode object) {
                String extracted = firstNonBlank(
                        text(object, "text"),
                        text(object, "value"),
                        text(object, "query"),
                        text(object, "label"),
                        text(object, "name"),
                        text(object, "target"),
                        text(object, "url"),
                        text(object, "expected"),
                        text(object, "description"),
                        text(object, "content")
                );
                if (!extracted.isBlank()) {
                    step.put(field, extracted);
                } else {
                    step.putNull(field);
                }
                continue;
            }
            if (value instanceof ArrayNode array) {
                StringBuilder joined = new StringBuilder();
                for (JsonNode item : array) {
                    if (item == null || item.isNull()) {
                        continue;
                    }
                    String piece = item.isTextual() || item.isNumber() || item.isBoolean()
                            ? item.asText("").trim()
                            : "";
                    if (piece.isBlank() && item instanceof ObjectNode obj) {
                        piece = firstNonBlank(text(obj, "text"), text(obj, "value"), text(obj, "label"));
                    }
                    if (piece.isBlank()) {
                        continue;
                    }
                    if (!joined.isEmpty()) {
                        joined.append(' ');
                    }
                    joined.append(piece);
                }
                if (!joined.isEmpty()) {
                    step.put(field, joined.toString());
                } else {
                    step.putNull(field);
                }
                continue;
            }
            step.putNull(field);
        }
    }

    private static void normalizeStatus(ObjectNode root) {
        String status = text(root, "status").trim().toUpperCase();
        if (status.isBlank()
                || status.equals("ACTIVE")
                || status.equals("SUCCESS")
                || status.equals("OK")
                || status.equals("COMPLETE")
                || status.equals("COMPLETED")) {
            root.put("status", IntentContract.READY);
            return;
        }
        if (status.equals("CLARIFY")
                || status.equals("NEEDS_CLARIFICATION")
                || status.equals("AMBIGUOUS")
                || status.equals("UNCLEAR")) {
            root.put("status", IntentContract.NEEDS_CLARIFICATION);
            return;
        }
        root.put("status", status);
    }

    private static void normalizeConfidence(ObjectNode root) {
        JsonNode confidence = root.get("confidence");
        if (confidence == null || confidence.isNull() || !confidence.isNumber()) {
            return;
        }
        double value = confidence.asDouble();
        if (value > 1.0 && value <= 100.0) {
            root.put("confidence", value / 100.0);
        }
    }

    private static void ensureText(ObjectNode node, String field, String fallback) {
        if (isBlank(text(node, field))) {
            node.put(field, fallback);
        }
    }

    /**
     * Ollama/Gemini sometimes emit {@code "filter": ""} or {@code "filter": "null"}.
     * Jackson cannot coerce an empty string into {@link IntentFilter}; force null first.
     */
    private static void nullifyBlankObjectFields(ObjectNode step, String... fields) {
        for (String field : fields) {
            JsonNode value = step.get(field);
            if (value == null) {
                continue;
            }
            if (value.isNull()) {
                step.putNull(field);
                continue;
            }
            if (value.isTextual() && isBlankOrNullToken(value.asText())) {
                step.putNull(field);
            }
        }
    }

    private static void nullifyBlankStringFields(ObjectNode step, String... fields) {
        for (String field : fields) {
            JsonNode value = step.get(field);
            if (value != null && value.isTextual() && isBlankOrNullToken(value.asText())) {
                step.putNull(field);
            }
        }
    }

    private static void normalizeStringArray(ObjectNode step, String field) {
        JsonNode value = step.get(field);
        if (value == null || value.isNull()) {
            step.putArray(field);
            return;
        }
        if (value instanceof ArrayNode array) {
            ArrayNode cleaned = step.putArray(field);
            for (JsonNode item : array) {
                if (item == null || item.isNull()) {
                    continue;
                }
                String text = item.isTextual() || item.isNumber() || item.isBoolean()
                        ? item.asText("").trim()
                        : "";
                if (!text.isBlank()) {
                    cleaned.add(text);
                }
            }
            return;
        }
        if (value.isTextual() && !value.asText("").isBlank()) {
            ArrayNode cleaned = step.putArray(field);
            cleaned.add(value.asText().trim());
            return;
        }
        step.putArray(field);
    }

    private static void persistSequentialDependsOn(ObjectNode root) {
        JsonNode scenarios = root.get("scenarios");
        if (!(scenarios instanceof ArrayNode array)) {
            return;
        }
        for (int s = 0; s < array.size(); s++) {
            if (!(array.get(s) instanceof ObjectNode scenario)) {
                continue;
            }
            JsonNode stepsNode = scenario.get("steps");
            if (!(stepsNode instanceof ArrayNode steps)) {
                continue;
            }
            String previousId = null;
            for (int i = 0; i < steps.size(); i++) {
                if (!(steps.get(i) instanceof ObjectNode step)) {
                    continue;
                }
                if (previousId != null && missingDependsOn(step)) {
                    ArrayNode deps = step.putArray("dependsOn");
                    deps.add(previousId);
                }
                previousId = text(step, "id");
            }
        }
    }

    private static boolean missingDependsOn(ObjectNode step) {
        JsonNode deps = step.get("dependsOn");
        return deps == null || deps.isNull() || !deps.isArray() || deps.isEmpty();
    }

    private static void normalizeFilterNode(ObjectNode step) {
        String promotedField = firstNonBlank(text(step, "filterField"), text(step, "filter_field"));
        String promotedValue = firstNonBlank(text(step, "filterValue"), text(step, "filter_value"));
        JsonNode filterNode = step.get("filter");
        if (!isBlank(promotedField) && (filterNode == null || filterNode.isNull() || filterNode.isTextual())) {
            ObjectNode filter = step.putObject("filter");
            filter.put("field", promotedField);
            if (!isBlank(promotedValue)) {
                filter.put("value", promotedValue);
            }
            filter.put("operator", "equals");
            filterNode = filter;
        }
        if (filterNode == null || filterNode.isNull()) {
            step.putNull("filter");
            return;
        }
        if (filterNode.isTextual()) {
            if (isBlankOrNullToken(filterNode.asText())) {
                step.putNull("filter");
                return;
            }
            ObjectNode parsed = parseLooseFilterObject(filterNode.asText());
            if (parsed == null) {
                step.putNull("filter");
            } else {
                step.set("filter", parsed);
            }
            return;
        }
        if (!(filterNode instanceof ObjectNode filter)) {
            step.putNull("filter");
            return;
        }
        nullifyBlankStringFields(filter, "field", "operator", "value");
        JsonNode min = filter.get("min");
        if (min != null && min.isTextual() && isBlankOrNullToken(min.asText())) {
            filter.putNull("min");
        }
        JsonNode max = filter.get("max");
        if (max != null && max.isTextual() && isBlankOrNullToken(max.asText())) {
            filter.putNull("max");
        }
        boolean empty = isBlank(text(filter, "field"))
                && isBlank(text(filter, "operator"))
                && isBlank(text(filter, "value"))
                && (filter.get("min") == null || filter.get("min").isNull())
                && (filter.get("max") == null || filter.get("max").isNull());
        if (empty) {
            step.putNull("filter");
        }
    }

    private static boolean isBlankOrNullToken(String value) {
        if (value == null) {
            return true;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty()
                || "null".equalsIgnoreCase(trimmed)
                || "undefined".equalsIgnoreCase(trimmed)
                || "none".equalsIgnoreCase(trimmed);
    }

    private static String text(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        if (value.isTextual() && isBlankOrNullToken(value.asText())) {
            return "";
        }
        return value.asText("").trim();
    }

    private static boolean looksLikeSearchQuery(String target, String value) {
        String blob = ((target == null ? "" : target) + " " + (value == null ? "" : value)).toLowerCase(Locale.ROOT);
        if (blob.contains("search")) {
            return true;
        }
        String query = firstNonBlank(value, target);
        if (isBlank(query) || looksLikeUiControl(query)) {
            return false;
        }
        return query.trim().split("\\s+").length >= 2;
    }

    private static boolean looksLikeUiControl(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("cart")
                || lower.contains("bag")
                || lower.contains("login")
                || lower.equals("+")
                || lower.equals("-")
                || lower.contains("increment")
                || lower.contains("quantity")
                || lower.contains("filter")
                || lower.contains("profile")
                || lower.contains("button")
                || lower.contains("icon");
    }

    private static boolean looksLikeEditableField(String target) {
        if (target == null || target.isBlank()) {
            return false;
        }
        String lower = target.toLowerCase(Locale.ROOT);
        return lower.contains("username")
                || lower.contains("user name")
                || lower.contains("password")
                || lower.contains("email")
                || lower.contains("search")
                || lower.contains("textbox")
                || lower.contains("text box")
                || lower.contains("phone")
                || lower.contains("employee")
                || lower.contains("first name")
                || lower.contains("last name");
    }

    /**
     * If a search query is generic-only, adopt a later more specific product phrase
     * that shares the same generic stem (smartphones → samsung smartphone).
     */
    private static void salvageSearchSpecificity(ArrayNode steps) {
        for (int i = 0; i < steps.size(); i++) {
            if (!(steps.get(i) instanceof ObjectNode searchStep)) {
                continue;
            }
            if (!"search".equalsIgnoreCase(text(searchStep, "action"))) {
                continue;
            }
            String query = text(searchStep, "value");
            if (query.isBlank() || !com.smartqa.browser.SearchStateContract.distinctiveTokens(query).isEmpty()) {
                continue;
            }
            for (int j = i + 1; j < steps.size(); j++) {
                if (!(steps.get(j) instanceof ObjectNode later)) {
                    continue;
                }
                String laterText = firstNonBlank(text(later, "value"), text(later, "target"));
                if (laterText.isBlank()
                        || com.smartqa.browser.SearchStateContract.distinctiveTokens(laterText).isEmpty()) {
                    continue;
                }
                if (relatedQuery(query, laterText)) {
                    searchStep.put("value", laterText);
                    break;
                }
            }
        }
    }

    private static boolean relatedQuery(String generic, String specific) {
        String g = generic.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
        String s = specific.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
        if (s.contains(g) || g.contains(s)) {
            return true;
        }
        for (String token : g.split("\\s+")) {
            if (token.length() >= 5 && s.contains(token.substring(0, token.length() - 1))) {
                return true;
            }
        }
        return false;
    }

    private static void salvageFilter(ObjectNode step) {
        JsonNode filterNode = step.get("filter");
        ObjectNode filter;
        if (filterNode instanceof ObjectNode object) {
            filter = object;
        } else {
            filter = step.putObject("filter");
        }
        String field = text(filter, "field");
        String value = text(filter, "value");
        String target = text(step, "target");
        String stepValue = text(step, "value");
        if (isBlank(field) && !isBlank(target)) {
            IntentFilter parsed = FilterIntentParser.parse(target);
            if (parsed != null && !isBlank(parsed.field())) {
                filter.put("field", parsed.field());
                field = parsed.field();
                if (isBlank(value) && parsed.value() != null) {
                    filter.put("value", parsed.value());
                    value = parsed.value();
                }
                if (parsed.min() != null) {
                    filter.put("min", parsed.min());
                }
                if (parsed.max() != null) {
                    filter.put("max", parsed.max());
                }
                if (isBlank(text(filter, "operator")) && parsed.operator() != null) {
                    filter.put("operator", parsed.operator());
                }
            } else {
                filter.put("field", target);
                field = target;
            }
        }
        if (isBlank(text(filter, "operator")) && (!isBlank(field) || !isBlank(value)
                || (filter.get("min") != null && !filter.get("min").isNull()))) {
            filter.put("operator", (filter.get("min") != null && !filter.get("min").isNull()) ? "between" : "equals");
        }
        if (isBlank(value) && !isBlank(stepValue)) {
            filter.put("value", stepValue);
            value = stepValue;
        }
        if (isBlank(text(step, "target")) && !isBlank(field)) {
            step.put("target", field);
        }
        if (isBlank(text(step, "target")) && !isBlank(value)) {
            step.put("target", value);
        }
        boolean empty = isBlank(text(filter, "field"))
                && isBlank(text(filter, "operator"))
                && isBlank(text(filter, "value"))
                && (filter.get("min") == null || filter.get("min").isNull())
                && (filter.get("max") == null || filter.get("max").isNull());
        if (empty) {
            step.putNull("filter");
        }
    }

    private static ObjectNode parseLooseFilterObject(String text) {
        IntentFilter parsed = FilterIntentParser.parse(text);
        if (parsed == null || (isBlank(parsed.field()) && isBlank(parsed.value()) && parsed.min() == null)) {
            return null;
        }
        ObjectNode filter = (ObjectNode) tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        if (!isBlank(parsed.field())) {
            filter.put("field", parsed.field());
        }
        if (!isBlank(parsed.value())) {
            filter.put("value", parsed.value());
        }
        if (parsed.min() != null) {
            filter.put("min", parsed.min());
        }
        if (parsed.max() != null) {
            filter.put("max", parsed.max());
        }
        filter.put("operator", parsed.operator() == null ? "equals" : parsed.operator());
        return filter;
    }

    private static String[] parseFieldAndValue(String text) {
        if (text == null || text.isBlank()) {
            return new String[]{"", ""};
        }
        String trimmed = text.trim();
        java.util.regex.Matcher between = java.util.regex.Pattern
                .compile("(?i)^(.+?)\\s+between\\s+([0-9,.]+)\\s+and\\s+([0-9,.]+)$")
                .matcher(trimmed);
        if (between.matches()) {
            return new String[]{between.group(1).trim(), between.group(2).trim() + "-" + between.group(3).trim()};
        }
        int colon = trimmed.indexOf(':');
        if (colon > 0 && colon < trimmed.length() - 1) {
            return new String[]{trimmed.substring(0, colon).trim(), trimmed.substring(colon + 1).trim()};
        }
        String[] parts = trimmed.split("\\s+", 2);
        if (parts.length == 2) {
            String rest = parts[1].trim();
            if (rest.startsWith("&") || rest.startsWith("/") || rest.startsWith("-")
                    || (ControlPhrase.isFilterFieldToken(rest.split("\\s+")[0])
                    && !ControlPhrase.looksLikeOptionCode(rest.split("\\s+")[0]))) {
                return new String[]{trimmed, ""};
            }
            return new String[]{parts[0], parts[1]};
        }
        return new String[]{trimmed, ""};
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean looksLikeUrl(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.trim().toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    /** Generic: "press enter" / blank press_key → Enter key. No site-specific rules. */
    private static boolean isEnterLike(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String lower = value.trim().toLowerCase().replace('_', ' ').replace('-', ' ');
        return lower.equals("enter")
                || lower.equals("return")
                || lower.equals("press enter")
                || lower.equals("hit enter")
                || lower.equals("submit");
    }

    private static String semanticize(String value) {
        if (value == null || value.isBlank() || looksLikeUrl(value)) {
            return value == null ? "" : value.trim();
        }
        String trimmed = value.trim();
        // Convert simple invented CSS-like tokens into semantic labels.
        if (trimmed.matches("(?i)^[.#][A-Za-z][\\w-]*$")) {
            trimmed = trimmed.substring(1).replace('-', ' ').replace('_', ' ');
        } else if (trimmed.matches("(?i)^[A-Za-z][\\w-]*$") && trimmed.contains("-")) {
            trimmed = trimmed.replace('-', ' ').replace('_', ' ');
        }
        return trimmed.trim();
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
}
