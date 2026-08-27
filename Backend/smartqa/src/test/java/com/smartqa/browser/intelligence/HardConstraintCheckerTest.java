package com.smartqa.browser.intelligence;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HardConstraintCheckerTest {

    @Test
    void nonClickableHeadingIsNonActionableForClick() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("candidateId", "h-1");
        raw.put("tag", "h2");
        raw.put("role", "heading");
        raw.put("accessibleName", "Brand");
        raw.put("text", "Brand");
        raw.put("visible", true);
        raw.put("enabled", true);
        raw.put("clickable", false);
        ElementCandidate candidate = ElementCandidate.fromMap(raw, 0);
        assertEquals(HardConstraint.NON_ACTIONABLE, HardConstraintChecker.evaluate(candidate, "click", null));
    }

    @Test
    void filterOnLoginChromeIsWrongPageState() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("candidateId", "f-1");
        raw.put("tag", "input");
        raw.put("role", "checkbox");
        raw.put("inputType", "checkbox");
        raw.put("accessibleName", "AK");
        raw.put("text", "AK");
        raw.put("region", "login");
        raw.put("headingContext", "Sign in");
        raw.put("visible", true);
        raw.put("enabled", true);
        raw.put("clickable", true);
        ElementCandidate candidate = ElementCandidate.fromMap(raw, 0);
        assertEquals(HardConstraint.WRONG_PAGE_STATE, HardConstraintChecker.evaluate(candidate, "filter", "Brand"));
    }

    @Test
    void headerSpanIsCapabilityMismatchForCheckbox() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("candidateId", "header-ak");
        raw.put("tag", "span");
        raw.put("role", "");
        raw.put("accessibleName", "AK");
        raw.put("text", "AK");
        raw.put("region", "HEADER");
        raw.put("headingContext", "Navigation");
        raw.put("visible", true);
        raw.put("enabled", true);
        raw.put("clickable", true);
        ElementCandidate candidate = ElementCandidate.fromMap(raw, 0);
        assertEquals(HardConstraint.CAPABILITY_MISMATCH, HardConstraintChecker.evaluate(candidate, "checkbox", "Brand"));
    }

    @Test
    void headerSpanIsInvalidOwnerForOwnedClick() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("candidateId", "header-ak");
        raw.put("tag", "span");
        raw.put("role", "");
        raw.put("accessibleName", "AK");
        raw.put("text", "AK");
        raw.put("region", "HEADER");
        raw.put("headingContext", "Navigation");
        raw.put("visible", true);
        raw.put("enabled", true);
        raw.put("clickable", true);
        ElementCandidate candidate = ElementCandidate.fromMap(raw, 0);
        assertEquals(HardConstraint.INVALID_OWNER, HardConstraintChecker.evaluate(candidate, "click", "Brand"));
    }

    @Test
    void visibleEnabledButtonIsNotHardRejectedForClick() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("candidateId", "b-1");
        raw.put("tag", "button");
        raw.put("role", "button");
        raw.put("accessibleName", "Login");
        raw.put("text", "Login");
        raw.put("visible", true);
        raw.put("enabled", true);
        raw.put("clickable", true);
        ElementCandidate candidate = ElementCandidate.fromMap(raw, 0);
        assertNull(HardConstraintChecker.evaluate(candidate, "click", null));
    }
}
