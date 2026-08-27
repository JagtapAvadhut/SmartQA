package com.smartqa.intent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic compiler for line-oriented instructions. Skips the Intent LLM when
 * every line maps to a supported action. Generic only — no website-specific selectors.
 */
public final class InstructionIntentCompiler {

    private static final Pattern URL = Pattern.compile("(?i)\\bhttps?://\\S+");
    private static final Pattern OPEN = Pattern.compile("(?i)^(open|go\\s+to|goto|visit|navigate(?:\\s+to)?)\\s+(.+)$");
    private static final Pattern CLOSE = Pattern.compile("(?i)^(close|dismiss|ignore)\\s+(.+)$");
    private static final Pattern CLICK = Pattern.compile("(?i)^(click|tap)\\s+(?:on\\s+)?(.+)$");
    private static final Pattern INSIDE = Pattern.compile(
            "(?i)^(?:inside|insite|within|under|below|in)\\s+(?:the\\s+)?(.+?)(?:\\s+(?:section|panel|form|modal|dialog|cart|filter|menu))?$");
    private static final Pattern INSIDE_NESTED = Pattern.compile(
            "(?i)^(?:inside|insite|within|under|below|in)\\s+(?:the\\s+)?(.+?)(?:\\s+(?:section|panel|form|modal|dialog|cart|filter|menu))?[,:]?\\s+"
                    + "(click|tap|select|type|enter|search|open|choose|hover|fill|submit|wait|clear|set|verify|scroll)\\s+(?:on\\s+)?(.+)$");
    private static final Pattern HOVER = Pattern.compile("(?i)^hover\\s+(?:over\\s+)?(.+)$");
    private static final Pattern SCROLL = Pattern.compile("(?i)^scroll\\s+(?:to\\s+|down\\s+|up\\s+)?(.*)$");
    private static final Pattern SUBMIT = Pattern.compile("(?i)^(submit|click\\s+submit)(?:\\s+(.+))?$");
    private static final Pattern WAIT_FOR = Pattern.compile("(?i)^wait\\s+(?:for\\s+|until\\s+)?(.+)$");
    private static final Pattern CLEAR_FILTERS = Pattern.compile("(?i)^clear\\s+(?:all\\s+)?filters?$");
    private static final Pattern SET_VALUE = Pattern.compile("(?i)^set\\s+(.+?)\\s+to\\s+(.+)$");
    private static final Pattern OPEN_SECTION = Pattern.compile("(?i)^open\\s+(.+?)(?:\\s+section)?$");
    private static final Pattern VERIFY = Pattern.compile("(?i)^(verify|assert|ensure|check that)\\s+(.+)$");
    private static final Pattern INPUT = Pattern.compile("(?i)^(enter|type|fill|input)\\s+(.+)$");
    private static final Pattern SEARCH = Pattern.compile("(?i)^search\\s+(?:for\\s+)?(.+)$");
    private static final Pattern IN_FIELD = Pattern.compile("(?i)^(.+?)\\s+(?:in|into)\\s+(?:the\\s+)?(.+)$");

    public record Result(IntentContract contract, boolean highConfidence, int parsedLines, int skippedLines) {
        public boolean usable() {
            return contract != null
                    && contract.scenarios() != null
                    && !contract.scenarios().isEmpty()
                    && contract.scenarios().getFirst().steps() != null
                    && contract.scenarios().getFirst().steps().size() >= 2;
        }
    }

    private InstructionIntentCompiler() {
    }

    public static Result compile(String instructions, String applicationUrl) {
        List<String> lines = splitLines(instructions);
        if (lines.isEmpty()) {
            return new Result(null, false, 0, 0);
        }
        List<IntentStep> steps = new ArrayList<>();
        String filterField = null;
        int skipped = 0;
        int order = 1;
        for (String line : lines) {
            Parsed parsed = parseLine(LanguageNormalizer.normalize(line), filterField, applicationUrl);
            if (parsed == null) {
                skipped++;
                continue;
            }
            if (parsed.filterField() != null) {
                filterField = parsed.filterField();
            }
            if (parsed.skipStep()) {
                continue;
            }
            ActionSemanticNormalizer.Rewrite rewrite = ActionSemanticNormalizer.rewrite(
                    parsed.action(), parsed.target(), parsed.value(), parsed.assertion());
            String action = firstNonBlank(rewrite.action(), parsed.action());
            String target = firstNonBlank(rewrite.target(), parsed.target());
            String value = firstNonBlank(rewrite.value(), parsed.value());
            IntentFilter filter = rewrite.filter() != null ? rewrite.filter() : parsed.filter();
            if ((SupportedActions.CHECKBOX.equals(action) || SupportedActions.RADIO.equals(action))
                    && filter == null && filterField != null && !isBlank(target)) {
                filter = new IntentFilter(filterField, "equals", target, null, null);
            }
            if (SupportedActions.EXPAND.equals(action) || SupportedActions.SELECT.equals(action)) {
                if (ControlPhrase.looksLikeCompoundControlName(target)) {
                    filterField = target;
                } else {
                    String fieldToken = ControlPhrase.firstFilterFieldToken(target);
                    if (fieldToken != null) {
                        filterField = fieldToken;
                    }
                }
            }
            String assertion = blankToNull(ControlPhrase.stripVerifyPrefix(parsed.assertion()));
            if (SupportedActions.VERIFY.equals(action)) {
                target = ControlPhrase.stripVerifyPrefix(target);
                value = ControlPhrase.stripVerifyPrefix(firstNonBlank(value, target));
                assertion = firstNonBlank(assertion, target);
            }
            IntentStep step = new IntentStep(
                    "s1_step" + order,
                    action,
                    blankToNull(target),
                    blankToNull(value),
                    assertion,
                    filter,
                    LocationHint.AUTO
            ).withSemantic(rewrite.controlType(), rewrite.targetType()).withScenarioId("s1");
            String scope = firstNonBlank(
                    parsed.filterField(),
                    ScopePhraseExtractor.extract(line),
                    searchOrFilterScope(action, filterField));
            if (!isBlank(scope)) {
                step = step.withContainerContext(scope);
            }
            List<String> constraints = new ArrayList<>();
            constraints.add("risk:" + ActionRiskClassifier.classify(action, target, value).name());
            if (!isBlank(scope)) {
                constraints.add("scope:" + scope);
            }
            if (!isBlank(target)) {
                constraints.add("entity:" + target);
            }
            step = step.withSemanticConstraints(constraints);
            steps.add(step);
            order++;
        }
        if (steps.isEmpty()) {
            return new Result(null, false, 0, skipped);
        }
        boolean hasNavigate = steps.stream().anyMatch(step -> SupportedActions.NAVIGATE.equals(step.action()));
        if (!hasNavigate && !isBlank(applicationUrl)) {
            steps.add(0, new IntentStep(
                    "s1_step0",
                    SupportedActions.NAVIGATE,
                    "application",
                    applicationUrl.trim(),
                    null,
                    null,
                    LocationHint.AUTO
            ));
        }
        IntentContract contract = new IntentContract(
                IntentContract.READY,
                firstNonBlank(firstLine(instructions), "Compiled test"),
                0.92,
                List.of(new IntentScenario("s1", "Main", steps)),
                List.of()
        );
        contract = IntentIdUniquifier.uniquify(contract);
        boolean high = skipped == 0 && steps.size() >= 3;
        if (skipped > 0 && steps.size() < 2) {
            contract = new IntentContract(
                    IntentContract.NEEDS_CLARIFICATION,
                    firstNonBlank(firstLine(instructions), "Compiled test"),
                    0.4,
                    List.of(new IntentScenario("s1", "Main", steps)),
                    List.of(new ClarificationQuestion(
                            "unparsed_lines",
                            "Some instructions could not be interpreted safely. Which did you mean?",
                            List.of("I will rewrite the unclear steps", "Proceed with the parsed steps only")
                    ))
            );
            contract = IntentIdUniquifier.uniquify(contract);
        }
        return new Result(contract, high, lines.size() - skipped, skipped);
    }

    private static Parsed parseLine(String line, String filterField, String applicationUrl) {
        String trimmed = line.trim().replaceAll("^[0-9]+[.)]\\s*", "");
        Matcher url = URL.matcher(trimmed);
        Matcher open = OPEN.matcher(trimmed);
        if (open.matches()) {
            String rest = open.group(2).trim();
            Matcher embedded = URL.matcher(rest);
            if (embedded.find()) {
                return new Parsed(
                        SupportedActions.NAVIGATE,
                        "application",
                        trimUrl(embedded.group()),
                        null,
                        null,
                        filterField,
                        false);
            }
        } else if (looksLikeOpen(trimmed) && url.find()) {
            return new Parsed(
                    SupportedActions.NAVIGATE,
                    "application",
                    trimUrl(url.group()),
                    null,
                    null,
                    filterField,
                    false);
        }
        Matcher close = CLOSE.matcher(trimmed);
        if (close.matches()) {
            return new Parsed(SupportedActions.CLICK, close.group(2).trim(), null, null, null, filterField, false);
        }
        Matcher nestedInside = INSIDE_NESTED.matcher(trimmed);
        if (nestedInside.matches()) {
            String container = stripSection(nestedInside.group(1));
            String verb = nestedInside.group(2).trim();
            String rest = stripNoise(nestedInside.group(3));
            String field = filterFieldFrom(container, filterField);
            return parseLine(verb + " " + rest, field, applicationUrl);
        }
        Matcher click = CLICK.matcher(trimmed);
        if (click.matches()) {
            return new Parsed(SupportedActions.CLICK, stripNoise(click.group(2)), null, null, null, filterField, false);
        }
        Matcher inside = INSIDE.matcher(trimmed);
        if (inside.matches()) {
            String section = stripSection(inside.group(1));
            String field = ControlPhrase.isFilterFieldToken(section) ? section : filterField;
            return new Parsed(SupportedActions.CLICK, section, null, null, null, field, false);
        }
        Matcher section = OPEN_SECTION.matcher(trimmed);
        if (section.matches() && !URL.matcher(trimmed).find()) {
            String name = stripSection(section.group(1));
            String field = ControlPhrase.isFilterFieldToken(name) ? name : filterField;
            return new Parsed(SupportedActions.CLICK, name, null, null, null, field, false);
        }
        Matcher verify = VERIFY.matcher(trimmed);
        if (verify.matches()) {
            String rest = ControlPhrase.stripVerifyPrefix(verify.group(2).trim());
            return new Parsed(SupportedActions.VERIFY, rest, null, rest, null, filterField, false);
        }
        Matcher input = INPUT.matcher(trimmed);
        if (input.matches()) {
            String rest = input.group(2).trim();
            Matcher inField = IN_FIELD.matcher(rest);
            if (inField.matches()) {
                return new Parsed(
                        SupportedActions.INPUT,
                        inField.group(2).trim(),
                        inField.group(1).trim(),
                        null,
                        null,
                        filterField,
                        false);
            }
            return new Parsed(SupportedActions.INPUT, "input", rest, null, null, filterField, false);
        }
        Matcher search = SEARCH.matcher(trimmed);
        if (search.matches()) {
            String query = ControlPhrase.unwrapQuotes(stripNoise(search.group(1)));
            query = query.replaceAll("(?i)\\s+in\\s+(?:the\\s+)?search\\s+(?:box|field)$", "").trim();
            return new Parsed(SupportedActions.SEARCH, "search", query, null, null, filterField, false);
        }
        Matcher hover = HOVER.matcher(trimmed);
        if (hover.matches()) {
            return new Parsed(SupportedActions.HOVER, stripNoise(hover.group(1)), null, null, null, filterField, false);
        }
        Matcher scroll = SCROLL.matcher(trimmed);
        if (scroll.matches()) {
            return new Parsed(SupportedActions.SCROLL, stripNoise(scroll.group(1)), null, null, null, filterField, false);
        }
        Matcher submit = SUBMIT.matcher(trimmed);
        if (submit.matches()) {
            return new Parsed(SupportedActions.SUBMIT, stripNoise(firstNonBlank(submit.group(2), "submit")), null, null, null, filterField, false);
        }
        Matcher waitFor = WAIT_FOR.matcher(trimmed);
        if (waitFor.matches()) {
            return new Parsed(SupportedActions.WAIT_FOR_STATE, stripNoise(waitFor.group(1)), null, null, null, filterField, false);
        }
        Matcher clear = CLEAR_FILTERS.matcher(trimmed);
        if (clear.matches()) {
            return new Parsed(SupportedActions.CLEAR_FILTERS, "filters", null, null, null, filterField, false);
        }
        Matcher setValue = SET_VALUE.matcher(trimmed);
        if (setValue.matches()) {
            return new Parsed(SupportedActions.SET_VALUE, stripNoise(setValue.group(1)), stripNoise(setValue.group(2)), null, null, filterField, false);
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (ControlPhrase.hasCheckbox(trimmed) || ControlPhrase.hasRadio(trimmed) || ControlPhrase.hasDropdown(trimmed)
                || lower.startsWith("select ") || lower.startsWith("choose ")) {
            ActionSemanticNormalizer.Rewrite rewrite = ActionSemanticNormalizer.rewrite("select", trimmed, null);
            IntentFilter filter = rewrite.filter();
            if (filter == null && filterField != null && !isBlank(rewrite.target())) {
                filter = new IntentFilter(filterField, "equals", rewrite.target(), null, null);
            }
            String action = rewrite.action().isBlank() ? SupportedActions.SELECT : rewrite.action();
            if (SupportedActions.SELECT.equals(action) && isBlank(rewrite.value())) {
                action = ControlPhrase.isFilterFieldToken(rewrite.target())
                        ? SupportedActions.EXPAND
                        : SupportedActions.CLICK;
            }
            return new Parsed(
                    action,
                    stripNoise(rewrite.target()),
                    rewrite.value(),
                    null,
                    filter,
                    filter != null ? filter.field() : filterField,
                    false);
        }
        return null;
    }

    private static boolean looksLikeOpen(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.startsWith("open ") || lower.startsWith("go to ") || lower.startsWith("visit ");
    }

    private static String stripSection(String raw) {
        return raw.replaceAll("(?i)\\b(the|section|panel|area)\\b", " ").replaceAll("\\s+", " ").trim();
    }

    private static String filterFieldFrom(String container, String fallback) {
        if (ControlPhrase.isFilterFieldToken(container)) {
            return container;
        }
        if (container != null) {
            for (String token : container.split("\\s+")) {
                if (ControlPhrase.isFilterFieldToken(token)) {
                    return token;
                }
            }
        }
        return fallback;
    }

    private static String stripNoise(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replaceAll("[\"']([^\"']+)[\"']", "$1")
                .replaceAll("(?i)\\s*\\.\\s*and\\s+enter\\s*$", "")
                .replaceAll("(?i)\\s+and\\s+enter\\s*$", "")
                .replaceAll("[.,;:]+$", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String trimUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        return url.trim()
                .replaceAll("^[\"']+", "")
                .replaceAll("[\"')\\],.;]+$", "");
    }

    private static List<String> splitLines(String instructions) {
        List<String> out = new ArrayList<>();
        if (instructions == null) {
            return out;
        }
        for (String line : instructions.split("\\r?\\n")) {
            if (line == null || line.isBlank()) {
                continue;
            }
            for (String piece : line.trim().split("(?i)\\s+then\\s+")) {
                if (piece == null || piece.isBlank()) {
                    continue;
                }
                for (String clause : splitCompoundClauses(piece.trim())) {
                    if (!clause.isBlank()) {
                        out.add(clause);
                    }
                }
            }
        }
        return out;
    }

    private static String firstLine(String instructions) {
        List<String> lines = splitLines(instructions);
        return lines.isEmpty() ? "" : lines.getFirst();
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

    private static String correctTypos(String line) {
        if (line == null || line.isBlank()) {
            return line;
        }
        return line
                .replaceAll("(?i)\\binsite\\b", "inside")
                .replaceAll("(?i)\\bselct\\b", "select")
                .replaceAll("(?i)\\bseach\\b", "search")
                .replaceAll("(?i)\\bclcik\\b", "click");
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static String searchOrFilterScope(String action, String filterField) {
        if (isBlank(filterField)) {
            return null;
        }
        if (SupportedActions.SEARCH.equals(action)
                || SupportedActions.CHECKBOX.equals(action)
                || SupportedActions.RADIO.equals(action)
                || SupportedActions.FILTER.equals(action)) {
            return filterField;
        }
        return null;
    }

    /**
     * Splits "click Brand dropdown, search volvo" into two clauses when the next
     * fragment starts with a new action verb. Quoted commas are left intact.
     */
    private static List<String> splitCompoundClauses(String line) {
        List<String> clauses = new ArrayList<>();
        if (line == null || line.isBlank()) {
            return clauses;
        }
        String remaining = line.trim();
        while (!remaining.isBlank()) {
            int splitAt = indexOfCompoundSplit(remaining);
            if (splitAt < 0) {
                clauses.add(remaining.trim());
                break;
            }
            String head = remaining.substring(0, splitAt).trim().replaceAll("[,\\s]+$", "");
            String tail = remaining.substring(splitAt).replaceFirst("(?i)^(?:[,]+|and(?:\\s+then)?)\\s+", "").trim();
            if (head.isBlank() || tail.isBlank() || tail.equalsIgnoreCase(remaining.trim())) {
                clauses.add(remaining.trim());
                break;
            }
            clauses.add(head);
            remaining = tail;
        }
        return clauses;
    }

    private static int indexOfCompoundSplit(String line) {
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '\'' && !inDouble) {
                inSingle = !inSingle;
                continue;
            }
            if (ch == '"' && !inSingle) {
                inDouble = !inDouble;
                continue;
            }
            if (inSingle || inDouble) {
                continue;
            }
            if (ch == ',') {
                String rest = line.substring(i + 1).trim();
                if (startsWithNewActionVerb(rest)) {
                    return i;
                }
            }
            if (i + 5 <= line.length() && line.substring(i).matches("(?i)and(?:\\s+then)?\\s+.*")) {
                Matcher and = Pattern.compile("(?i)^and(?:\\s+then)?\\s+").matcher(line.substring(i));
                if (and.find()) {
                    String rest = line.substring(i + and.end()).trim();
                    if (startsWithNewActionVerb(rest)) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    private static boolean startsWithNewActionVerb(String rest) {
        if (rest == null || rest.isBlank()) {
            return false;
        }
        String lower = rest.toLowerCase(Locale.ROOT);
        if (lower.startsWith("checkbox") || lower.startsWith("check box")) {
            return false;
        }
        return lower.matches("(?s)(search|click|tap|type|fill|verify|assert|ensure|check)\\b.*");
    }

    private record Parsed(
            String action,
            String target,
            String value,
            String assertion,
            IntentFilter filter,
            String filterField,
            boolean skipStep
    ) {
    }
}
