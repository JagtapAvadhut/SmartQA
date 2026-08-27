package com.smartqa.browser.intelligence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Compact relevant DOM package for AI — never the full page HTML.
 */
public final class RelevantDomExtractor {

    private RelevantDomExtractor() {
    }

    public static String compact(List<ElementCandidate> elements, String target, String ownerHint, int limit) {
        if (elements == null || elements.isEmpty()) {
            return "(no candidates)";
        }
        String hint = normalize(target);
        String owner = normalize(ownerHint);
        List<ElementCandidate> ranked = new ArrayList<>(elements);
        ranked.sort((a, b) -> Double.compare(relevance(b, hint, owner), relevance(a, hint, owner)));
        int n = Math.max(1, Math.min(limit, ranked.size()));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            ElementCandidate el = ranked.get(i);
            sb.append(el.candidateId())
                    .append(" | region=").append(safe(el.region()))
                    .append(" | role=").append(safe(el.role()))
                    .append(" | name=").append(safe(el.accessibleName()))
                    .append(" | text=").append(safe(el.text()))
                    .append(" | heading=").append(safe(el.headingContext()))
                    .append(" | ancestors=").append(safe(el.ancestorContext()))
                    .append(" | parent=").append(safe(el.parentContext()))
                    .append(" | siblings=").append(safe(el.siblingContext()))
                    .append(" | quality=").append(el.evidenceQuality())
                    .append('\n');
        }
        return sb.toString();
    }

    public static List<ElementCandidate> ownedOptions(
            List<ElementCandidate> elements, String optionHint, String ownerHint) {
        if (elements == null) {
            return List.of();
        }
        String option = normalize(optionHint);
        return elements.stream()
                .filter(ElementCandidate::visible)
                .filter(el -> matchesOption(el, option))
                .filter(el -> ownerHint == null || ownerHint.isBlank() || DomEvidence.ownsContext(el, ownerHint))
                .collect(Collectors.toList());
    }

    private static boolean matchesOption(ElementCandidate el, String option) {
        if (option.isBlank()) {
            return false;
        }
        String blob = normalize(String.join(" ",
                el.accessibleName(), el.text(), el.label(), el.ariaLabel(), el.value()));
        return blob.equals(option) || blob.contains(option) || option.contains(blob);
    }

    private static double relevance(ElementCandidate el, String hint, String owner) {
        double score = el.evidenceQuality() * 40;
        String blob = normalize(String.join(" ", el.semanticTokens()));
        if (!hint.isBlank() && blob.contains(hint)) {
            score += 50;
        }
        if (!owner.isBlank() && DomEvidence.ownsContext(el, owner)) {
            score += 60;
        }
        if ("FILTER_PANEL".equalsIgnoreCase(el.region()) || "SIDEBAR".equalsIgnoreCase(el.region())) {
            score += 15;
        }
        return score;
    }

    private static String normalize(String v) {
        return v == null ? "" : v.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static String safe(String v) {
        if (v == null || v.isBlank()) {
            return "";
        }
        return v.length() <= 80 ? v : v.substring(0, 80) + "…";
    }
}
