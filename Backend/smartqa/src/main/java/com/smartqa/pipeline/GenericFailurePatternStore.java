package com.smartqa.pipeline;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lightweight RAG for generic failure/recovery patterns.
 * Never stores secrets or site-specific selectors as truth. Current DOM always outranks RAG.
 */
@Component
public class GenericFailurePatternStore {

    private final CopyOnWriteArrayList<Pattern> patterns = new CopyOnWriteArrayList<>();

    public GenericFailurePatternStore() {
        patterns.add(new Pattern(
                "autocomplete",
                "Autocomplete controls often require selecting a visible suggestion after typing."));
        patterns.add(new Pattern(
                "filter",
                "Filter panels may use accordion + chip + checkbox combinations; expand closed panels before selecting."));
        patterns.add(new Pattern(
                "icon",
                "Icon-only buttons may require clickable ancestor discovery."));
        patterns.add(new Pattern(
                "host",
                "If search redirects to a different subdomain of the same site, restore the expected application host before asserting domestic results."));
        patterns.add(new Pattern(
                "assertion",
                "Never rewrite expected assertion text; diagnose wrong page/host/state first."));
        patterns.add(new Pattern(
                "overlay",
                "Transient overlays should be dismissed and retried by the engine without asking the user."));
        patterns.add(new Pattern(
                "form",
                "Missing validation messages often mean the form submit or required fields were not completed."));
        patterns.add(new Pattern(
                "profile",
                "Icon-only account/profile controls are often adjacent to cart/bag; use visual+semantic disambiguation, never nth/rightmost heuristics as truth."));
        patterns.add(new Pattern(
                "cart",
                "Cart vs profile ambiguity: prefer account/user semantics for profile instructions; verify with live Safety Gate."));
        patterns.add(new Pattern(
                "modal",
                "Modal/overlay patterns: close transient overlays then rediscover; do not ask the user for overlays."));
        patterns.add(new Pattern(
                "new-tab",
                "New-tab flows require switching to the expected page context before asserting."));
        patterns.add(new Pattern(
                "wrong-host",
                "Wrong-host recovery: inspect redirect, verify expected host, restore safe state, fresh DOM, retry — never weaken assertion."));
    }

    public void remember(AiDiagnosticResult result, FailureEvidence evidence) {
        if (result == null) {
            return;
        }
        String key = result.normalizedClassification().toLowerCase(Locale.ROOT);
        String hint = result.explanation();
        if (hint == null || hint.isBlank()) {
            return;
        }
        // Keep generic — strip obvious secrets
        if (looksLikeSecret(hint)) {
            return;
        }
        patterns.addIfAbsent(new Pattern(key, truncate(hint, 240)));
        if (patterns.size() > 80) {
            patterns.remove(0);
        }
    }

    public String relevantHints(FailureEvidence evidence) {
        String blob = evidence == null ? "" : (safe(evidence.exception()) + " " + safe(evidence.action())
                + " " + safe(evidence.failureCategory()) + " " + safe(evidence.url())).toLowerCase(Locale.ROOT);
        List<String> hits = new ArrayList<>();
        for (Pattern p : patterns) {
            if (blob.contains(p.key()) || "assertion".equals(p.key()) && blob.contains("assert")
                    || "host".equals(p.key()) && (blob.contains("export.") || blob.contains("host"))) {
                hits.add("- " + p.hint());
            }
            if (hits.size() >= 5) {
                break;
            }
        }
        if (hits.isEmpty()) {
            return "- Prefer current live DOM over historical patterns.";
        }
        return String.join("\n", hits);
    }

    private static boolean looksLikeSecret(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("password=")
                || lower.contains("api_key")
                || lower.contains("apikey")
                || lower.contains("bearer ")
                || lower.contains("otp=");
    }

    private static String safe(String v) {
        return v == null ? "" : v;
    }

    private static String truncate(String v, int max) {
        return v.length() <= max ? v : v.substring(0, max);
    }

    private record Pattern(String key, String hint) {
    }
}
