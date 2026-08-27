package com.smartqa.intent;

import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generic: when instructions contain quoted literals (Verify text as "Dashboard"),
 * snap AI-mangled verify/click targets back to the exact quoted text.
 * Does not invent selectors or site-specific rules.
 */
public final class IntentQuotedLiteralReconciler {

    private static final Pattern QUOTED = Pattern.compile("\"([^\"]{1,200})\"|'([^']{1,200})'");

    private IntentQuotedLiteralReconciler() {
    }

    public static IntentContract reconcile(IntentContract contract, String instructions) {
        if (contract == null || contract.scenarios() == null || contract.scenarios().isEmpty()) {
            return contract;
        }
        List<String> quotes = extractQuoted(instructions);
        if (quotes.isEmpty()) {
            return contract;
        }
        List<IntentScenario> scenarios = new ArrayList<>();
        int fixes = 0;
        for (IntentScenario scenario : contract.scenarios()) {
            if (scenario == null || scenario.steps() == null) {
                scenarios.add(scenario);
                continue;
            }
            List<IntentStep> steps = new ArrayList<>();
            for (IntentStep step : scenario.steps()) {
                IntentStep fixed = reconcileStep(step, quotes);
                if (fixed != step) {
                    fixes++;
                }
                steps.add(fixed);
            }
            scenarios.add(new IntentScenario(scenario.id(), scenario.name(), steps));
        }
        if (fixes > 0) {
            TraceLogger.info("INTENT", "INTENT_QUOTED_LITERAL_RECONCILED",
                    "Snapped AI intent fields to quoted instruction literals",
                    TraceMeta.of("fixes", fixes, "quotedLiterals", quotes.size()));
        }
        return new IntentContract(
                contract.status(),
                contract.testName(),
                contract.confidence(),
                scenarios,
                contract.clarifications() == null ? List.of() : contract.clarifications()
        );
    }

    private static IntentStep reconcileStep(IntentStep step, List<String> quotes) {
        if (step == null || step.action() == null) {
            return step;
        }
        String action = step.action().trim().toLowerCase(Locale.ROOT);
        boolean verifyLike = SupportedActions.VERIFY.equals(action);
        boolean clickLike = SupportedActions.CLICK.equals(action)
                || SupportedActions.HOVER.equals(action)
                || SupportedActions.CHECKBOX.equals(action)
                || SupportedActions.RADIO.equals(action);
        if (!verifyLike && !clickLike) {
            return step;
        }
        String target = step.target();
        String value = step.value();
        // "Select Admin from menu item" drift → target becomes vague "menu item"
        if (clickLike && isVagueTarget(target) && value != null && !value.isBlank()) {
            target = value;
            value = null;
        }
        target = snap(target, quotes, verifyLike);
        value = verifyLike ? snap(value, quotes, true) : value;
        String assertion = verifyLike ? snap(step.assertion(), quotes, true) : step.assertion();
        if (eq(target, step.target()) && eq(value, step.value()) && eq(assertion, step.assertion())) {
            return step;
        }
        return step.withTargetValueAssertion(target, value, assertion);
    }

    private static boolean isVagueTarget(String target) {
        if (target == null || target.isBlank()) {
            return true;
        }
        String lower = target.trim().toLowerCase(Locale.ROOT);
        return lower.equals("menu item")
                || lower.equals("menu")
                || lower.equals("option")
                || lower.equals("item")
                || lower.equals("button")
                || lower.equals("link")
                || lower.equals("element")
                || lower.equals("control")
                || lower.equals("the button")
                || lower.equals("the link");
    }

    static String snap(String candidate, List<String> quotes, boolean preferExactVerify) {
        if (candidate == null || candidate.isBlank() || quotes == null || quotes.isEmpty()) {
            return candidate;
        }
        String trimmed = candidate.trim();
        for (String quote : quotes) {
            if (quote.equals(trimmed)) {
                return quote;
            }
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        String best = null;
        int bestScore = 0;
        for (String quote : quotes) {
            String qLower = quote.toLowerCase(Locale.ROOT);
            if (lower.equals(qLower)) {
                return quote;
            }
            // "Dashboard text" / "Dashboard heading" → "Dashboard"
            if (lower.startsWith(qLower) && (lower.length() > qLower.length())
                    && (lower.charAt(qLower.length()) == ' '
                    || lower.charAt(qLower.length()) == '-'
                    || lower.charAt(qLower.length()) == ':')) {
                int score = quote.length() + (preferExactVerify ? 20 : 0);
                if (score > bestScore) {
                    bestScore = score;
                    best = quote;
                }
            }
            // AI dropped punctuation but kept the core phrase
            if (qLower.contains(lower) && lower.length() >= 4 && quote.length() - trimmed.length() <= 12) {
                int score = lower.length();
                if (score > bestScore) {
                    bestScore = score;
                    best = quote;
                }
            }
        }
        return best != null ? best : candidate;
    }

    static List<String> extractQuoted(String instructions) {
        List<String> out = new ArrayList<>();
        if (instructions == null || instructions.isBlank()) {
            return out;
        }
        Matcher matcher = QUOTED.matcher(instructions);
        while (matcher.find()) {
            String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (value != null && !value.isBlank()) {
                out.add(value.trim());
            }
        }
        return out;
    }

    private static boolean eq(String a, String b) {
        if (a == null) {
            return b == null;
        }
        return a.equals(b);
    }
}
