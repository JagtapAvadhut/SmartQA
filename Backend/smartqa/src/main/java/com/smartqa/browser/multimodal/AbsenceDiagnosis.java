package com.smartqa.browser.multimodal;

import com.smartqa.browser.intelligence.ElementCandidate;
import com.smartqa.browser.intelligence.LocatorRanker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Evidence checklist before SmartQA may conclude TARGET_NOT_PRESENT.
 */
public record AbsenceDiagnosis(
        boolean visuallyPresent,
        boolean semanticallyUnclear,
        boolean collapsedParent,
        boolean wrongParent,
        boolean wrongControlType,
        boolean wordingMismatch,
        boolean missingFromCandidates,
        boolean iframeLikely,
        boolean shadowLikely,
        boolean overlayLikely,
        boolean staleDomLikely,
        boolean viewportHidden,
        String conclusion,
        List<String> notes
) {
    public static AbsenceDiagnosis inspect(
            List<ElementCandidate> elements,
            List<LocatorRanker.RankedElement> ranked,
            SemanticTargetNormalizer.NormalizedTarget intent,
            TargetHypothesis hypothesis,
            boolean screenshotCaptured) {
        String field = intent == null ? "" : nullToEmpty(intent.semanticField()).toLowerCase(Locale.ROOT);
        String value = intent == null ? "" : nullToEmpty(intent.value()).toLowerCase(Locale.ROOT);
        boolean visual = hypothesis != null && hypothesis.visualTargetPresent();
        boolean collapsed = anyCollapsedOwner(elements, field);
        boolean iframe = elements != null && elements.stream().anyMatch(el ->
                notBlank(el.iframeContext()) && !"main".equalsIgnoreCase(el.iframeContext()));
        boolean shadow = elements != null && elements.stream().anyMatch(el -> notBlank(el.shadowContext()));
        boolean overlay = elements != null && elements.stream().anyMatch(el -> {
            String region = el.region() == null ? "" : el.region().toUpperCase(Locale.ROOT);
            return region.contains("DIALOG") || region.contains("OVERLAY");
        });
        boolean wrongType = ranked != null && !ranked.isEmpty() && intent != null && intent.isFilterOption()
                && !looksLikeFilterControl(ranked.getFirst().element());
        boolean wrongParent = ranked != null && !ranked.isEmpty() && !field.isBlank()
                && !ranked.getFirst().element().ownershipContext().contains(field);
        boolean missing = ranked == null || ranked.isEmpty();
        boolean wording = visual && missing;
        String conclusion;
        if (visual && missing) {
            conclusion = "VISUAL_TARGET_PRESENT_DOM_UNRESOLVED";
        } else if (!visual && missing && !collapsed && !iframe && !shadow) {
            conclusion = "TARGET_NOT_PRESENT";
        } else {
            conclusion = "NEEDS_MORE_DISCOVERY";
        }
        List<String> notes = new ArrayList<>();
        notes.add("screenshotCaptured=" + screenshotCaptured);
        notes.add("ranked=" + (ranked == null ? 0 : ranked.size()));
        if (collapsed) {
            notes.add("Owner section may be collapsed");
        }
        if (iframe) {
            notes.add("Iframe context present");
        }
        if (shadow) {
            notes.add("Shadow context present");
        }
        return new AbsenceDiagnosis(
                visual,
                wording,
                collapsed,
                wrongParent,
                wrongType,
                wording,
                missing,
                iframe,
                shadow,
                overlay,
                false,
                false,
                conclusion,
                List.copyOf(notes)
        );
    }

    private static boolean anyCollapsedOwner(List<ElementCandidate> elements, String field) {
        if (elements == null || field.isBlank()) {
            return false;
        }
        for (ElementCandidate el : elements) {
            String blob = (el.accessibleName() + " " + el.text() + " " + el.headingContext()).toLowerCase(Locale.ROOT);
            if (blob.contains(field) && !el.ariaExpanded()) {
                String tag = el.tag() == null ? "" : el.tag().toLowerCase(Locale.ROOT);
                String role = el.role() == null ? "" : el.role().toLowerCase(Locale.ROOT);
                if ("button".equals(role) || "summary".equals(tag) || el.ariaExpanded() == false && blob.contains(field)) {
                    return "button".equals(role) || "summary".equals(tag) || "heading".equals(role);
                }
            }
        }
        return false;
    }

    private static boolean looksLikeFilterControl(ElementCandidate el) {
        String role = el.role() == null ? "" : el.role().toLowerCase(Locale.ROOT);
        String type = el.inputType() == null ? "" : el.inputType().toLowerCase(Locale.ROOT);
        return "checkbox".equals(role) || "checkbox".equals(type) || "radio".equals(role) || el.hasAssociatedControl();
    }

    private static boolean notBlank(String v) {
        return v != null && !v.isBlank();
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }
}
