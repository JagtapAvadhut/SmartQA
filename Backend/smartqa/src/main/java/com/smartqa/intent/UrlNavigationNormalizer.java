package com.smartqa.intent;

import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generic rewrite: a URL is a navigation target, never a CSS/XPath locator.
 * CLICK/OPEN/GO TO/TAP + URL → NAVIGATE + URL before locator-safety validation.
 */
public final class UrlNavigationNormalizer {

    private static final Pattern HTTP_URL = Pattern.compile("(?i)https?://\\S+");
    private static final Set<String> CLICK_LIKE = Set.of(
            "click", "tap", "press", "open", "go", "goto", "go_to", "visit", "load"
    );
    private static final Set<String> NAV_ALIASES = Set.of(
            "open", "go", "goto", "go_to", "visit", "load", "navigate", "nav"
    );

    public record Rewrite(String action, String target, String value, boolean changed) {
    }

    private UrlNavigationNormalizer() {
    }

    public static boolean looksLikeHttpUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String lower = value.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    public static IntentContract rewrite(IntentContract contract) {
        if (contract == null || contract.scenarios() == null) {
            return contract;
        }
        List<IntentScenario> scenarios = new ArrayList<>();
        int changes = 0;
        for (IntentScenario scenario : contract.scenarios()) {
            if (scenario == null || scenario.steps() == null) {
                scenarios.add(scenario);
                continue;
            }
            List<IntentStep> steps = new ArrayList<>();
            for (IntentStep step : scenario.steps()) {
                IntentStep rewritten = rewrite(step);
                if (rewritten != step) {
                    changes++;
                }
                steps.add(rewritten);
            }
            scenarios.add(new IntentScenario(scenario.id(), scenario.name(), steps));
        }
        if (changes == 0) {
            return contract;
        }
        return new IntentContract(
                contract.status(),
                contract.testName(),
                contract.confidence(),
                scenarios,
                contract.clarifications() == null ? List.of() : contract.clarifications()
        );
    }

    /**
     * Keep NAVIGATE on the requested application host. AI/RAG must not send the browser
     * to a previous site's URL.
     */
    public static IntentContract bindToApplication(IntentContract contract, String applicationUrl) {
        if (contract == null || applicationUrl == null || applicationUrl.isBlank() || contract.scenarios() == null) {
            return contract;
        }
        String expectedHost = hostOf(applicationUrl);
        if (expectedHost.isBlank()) {
            return contract;
        }
        List<IntentScenario> scenarios = new ArrayList<>();
        int changes = 0;
        for (IntentScenario scenario : contract.scenarios()) {
            if (scenario == null || scenario.steps() == null) {
                scenarios.add(scenario);
                continue;
            }
            List<IntentStep> steps = new ArrayList<>();
            for (IntentStep step : scenario.steps()) {
                IntentStep bound = bindStepToApplication(step, applicationUrl.trim(), expectedHost);
                if (bound != step) {
                    changes++;
                }
                steps.add(bound);
            }
            scenarios.add(new IntentScenario(scenario.id(), scenario.name(), steps));
        }
        if (changes == 0) {
            return contract;
        }
        return new IntentContract(
                contract.status(),
                contract.testName(),
                contract.confidence(),
                scenarios,
                contract.clarifications() == null ? List.of() : contract.clarifications()
        );
    }

    private static IntentStep bindStepToApplication(IntentStep step, String applicationUrl, String expectedHost) {
        if (step == null) {
            return null;
        }
        String action = step.action() == null ? "" : step.action().trim().toLowerCase(Locale.ROOT);
        if (!SupportedActions.NAVIGATE.equals(action) && !isNavAlias(action)) {
            return step;
        }
        String url = extractNavigationUrl(step.target(), step.value());
        if (url == null) {
            return step;
        }
        String actualHost = hostOf(url);
        if (actualHost.isBlank() || expectedHost.equalsIgnoreCase(actualHost)) {
            return step;
        }
        TraceLogger.info(
                "INTENT",
                "NAVIGATE_HOST_BOUND",
                "Rebound foreign navigation URL to the requested application",
                TraceMeta.of(
                        "stepId", step.id(),
                        "action", action,
                        "fromHost", actualHost,
                        "toHost", expectedHost,
                        "fromUrl", url,
                        "toUrl", applicationUrl
                ));
        return step.withActionTargetValue(SupportedActions.NAVIGATE, applicationUrl, applicationUrl);
    }

    static String hostOf(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (RuntimeException ex) {
            return "";
        }
    }

    public static IntentStep rewrite(IntentStep step) {
        if (step == null) {
            return null;
        }
        Rewrite rewritten = rewriteFields(step.action(), step.target(), step.value());
        if (!rewritten.changed()) {
            return step;
        }
        TraceLogger.info(
                "INTENT",
                "CLICK_URL_NORMALIZED_TO_NAVIGATE",
                "Normalized URL navigation intent before locator validation",
                TraceMeta.of(
                        "stepId", step.id(),
                        "fromAction", step.action(),
                        "toAction", rewritten.action(),
                        "url", rewritten.target()
                ));
        return step.withActionTargetValue(rewritten.action(), rewritten.target(), rewritten.value());
    }

    public static Rewrite rewriteFields(String action, String target, String value) {
        String act = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        String url = extractNavigationUrl(target, value);
        if (url == null) {
            return new Rewrite(action, target, value, false);
        }
        if (!isClickLike(act) && !isNavAlias(act) && !SupportedActions.NAVIGATE.equals(act)) {
            return new Rewrite(action, target, value, false);
        }
        boolean alreadyNavigate = SupportedActions.NAVIGATE.equals(act);
        boolean targetIsUrl = looksLikeHttpUrl(target);
        if (alreadyNavigate && targetIsUrl) {
            return new Rewrite(act, target == null ? url : target.trim(), value, false);
        }
        if (alreadyNavigate && looksLikeHttpUrl(value) && !isClickLike(act)) {
            return new Rewrite(act, target, value, false);
        }
        return new Rewrite(SupportedActions.NAVIGATE, url, looksLikeHttpUrl(value) ? value.trim() : url, true);
    }

    static String extractNavigationUrl(String target, String value) {
        if (looksLikeHttpUrl(target)) {
            return target.trim();
        }
        String strippedTarget = stripNavigationPrefix(target);
        if (looksLikeHttpUrl(strippedTarget)) {
            return strippedTarget;
        }
        if (looksLikeHttpUrl(value)) {
            return value.trim();
        }
        String extracted = extractUrlIfDominant(target);
        if (extracted != null) {
            return extracted;
        }
        return extractUrlIfDominant(value);
    }

    private static String stripNavigationPrefix(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.trim().replaceFirst(
                "(?i)^(open|go\\s+to|goto|visit|navigate(?:\\s+to)?|click|tap)\\s+",
                "").trim();
    }

    private static String extractUrlIfDominant(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = HTTP_URL.matcher(text.trim());
        if (!matcher.find()) {
            return null;
        }
        String url = matcher.group().replaceAll("[\\),.;]+$", "");
        String remainder = HTTP_URL.matcher(text).replaceFirst("")
                .replaceAll("(?i)\\b(open|go to|goto|visit|navigate to|navigate|click|tap|the|url|website|page|site|link)\\b", "")
                .replaceAll("[\\s/:]+", "")
                .trim();
        return remainder.isEmpty() ? url : null;
    }

    private static boolean isClickLike(String action) {
        return CLICK_LIKE.contains(action);
    }

    private static boolean isNavAlias(String action) {
        return NAV_ALIASES.contains(action);
    }
}
