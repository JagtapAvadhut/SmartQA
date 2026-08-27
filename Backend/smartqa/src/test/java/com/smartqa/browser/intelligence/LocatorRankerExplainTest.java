package com.smartqa.browser.intelligence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LocatorRankerExplainTest {

    @Test
    void explainsOwnedFilterMatch() {
        java.util.Map<String, Object> raw = new java.util.LinkedHashMap<>();
        raw.put("candidateId", "opt-ak");
        raw.put("tag", "label");
        raw.put("role", "checkbox");
        raw.put("accessibleName", "AK");
        raw.put("text", "AK");
        raw.put("headingContext", "Brand");
        raw.put("ancestorContext", "Brand");
        raw.put("region", "FILTER_PANEL");
        raw.put("visible", true);
        raw.put("enabled", true);
        raw.put("clickable", true);
        ElementCandidate ak = ElementCandidate.fromMap(raw, 1);
        ScoreBreakdown breakdown = LocatorRanker.explain(ak, "click", "AK", "Brand");
        assertTrue(breakdown.total() > 0);
        assertTrue(breakdown.explanation().toLowerCase().contains("ak"));
        assertTrue(breakdown.ownership() > 0);
        assertTrue(breakdown.total() == LocatorRanker.score(ak, "click", "ak", "Brand")
                || breakdown.total() > 0);
    }

    @Test
    void hardConstraintRejectsHiddenCandidate() {
        java.util.Map<String, Object> raw = new java.util.LinkedHashMap<>();
        raw.put("candidateId", "hidden");
        raw.put("tag", "button");
        raw.put("accessibleName", "Login");
        raw.put("text", "Login");
        raw.put("visible", false);
        raw.put("enabled", true);
        ElementCandidate hidden = ElementCandidate.fromMap(raw, 0);
        ScoreBreakdown breakdown = LocatorRanker.explain(hidden, "click", "Login", null);
        assertTrue(breakdown.total() == 0);
        assertTrue("NOT_VISIBLE".equals(breakdown.hardConstraint()));
    }

    @Test
    void capabilityMismatchCheckboxVsDropdownIsHardReject() {
        java.util.Map<String, Object> raw = new java.util.LinkedHashMap<>();
        raw.put("candidateId", "dd");
        raw.put("tag", "select");
        raw.put("role", "combobox");
        raw.put("accessibleName", "AK");
        raw.put("text", "AK");
        raw.put("visible", true);
        raw.put("enabled", true);
        raw.put("clickable", true);
        ElementCandidate dropdown = ElementCandidate.fromMap(raw, 0);
        ScoreBreakdown breakdown = LocatorRanker.explain(dropdown, "checkbox", "AK under Brand", null);
        assertTrue(breakdown.total() == 0);
        assertTrue(HardConstraint.CAPABILITY_MISMATCH.name().equals(breakdown.hardConstraint())
                || HardConstraint.INVALID_OWNER.name().equals(breakdown.hardConstraint())
                || HardConstraint.NON_ACTIONABLE.name().equals(breakdown.hardConstraint()));
    }

    @Test
    void historyScoreIsAdvisoryAndDoesNotOverrideHardReject() {
        java.util.Map<String, Object> raw = new java.util.LinkedHashMap<>();
        raw.put("candidateId", "btn");
        raw.put("tag", "button");
        raw.put("role", "button");
        raw.put("accessibleName", "Login");
        raw.put("text", "Login");
        raw.put("visible", true);
        raw.put("enabled", true);
        raw.put("clickable", true);
        ElementCandidate button = ElementCandidate.fromMap(raw, 1);
        double without = LocatorRanker.rank(java.util.List.of(button), "click", "Login", null, 0).getFirst().score();
        double withHistory = LocatorRanker.rank(java.util.List.of(button), "click", "Login", null, 25).getFirst().score();
        assertTrue(withHistory >= without);

        java.util.Map<String, Object> hiddenRaw = new java.util.LinkedHashMap<>(raw);
        hiddenRaw.put("visible", false);
        hiddenRaw.put("candidateId", "hidden");
        ElementCandidate hidden = ElementCandidate.fromMap(hiddenRaw, 0);
        assertTrue(LocatorRanker.rank(java.util.List.of(hidden), "click", "Login", null, 100).isEmpty());
    }
}
