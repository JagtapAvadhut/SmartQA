package com.smartqa.browser.intelligence;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FailClosedDecisionTest {

    @Test
    void incompleteCaptureDoesNotProceed() {
        FailClosedDecision.Result result = FailClosedDecision.evaluate(List.of(), false);
        assertEquals(FailClosedDecision.Outcome.INCOMPLETE_CAPTURE, result.outcome());
        assertFalse(result.proceed());
    }

    @Test
    void uniqueHighScoreCandidateMayProceed() {
        ElementCandidate search = ElementCandidate.fromMap(Map.of(
                "tag", "input",
                "role", "searchbox",
                "accessibleName", "Search",
                "placeholder", "Search",
                "visible", true,
                "enabled", true
        ), 0);
        List<LocatorRanker.RankedElement> ranked = LocatorRanker.rank(List.of(search), "input", "Search");
        FailClosedDecision.Result result = FailClosedDecision.evaluate(ranked, true);
        assertEquals(FailClosedDecision.Outcome.UNIQUE, result.outcome());
        assertTrue(result.proceed());
    }

    @Test
    void twoEquallySupportedSaveButtonsAreAmbiguous() {
        ElementCandidate dialogSave = ElementCandidate.fromMap(Map.of(
                "tag", "button",
                "role", "button",
                "accessibleName", "Save",
                "text", "Save",
                "visible", true,
                "enabled", true,
                "parentContext", "dialog"
        ), 0);
        ElementCandidate toolbarSave = ElementCandidate.fromMap(Map.of(
                "tag", "button",
                "role", "button",
                "accessibleName", "Save",
                "text", "Save",
                "visible", true,
                "enabled", true,
                "parentContext", "toolbar"
        ), 1);
        List<LocatorRanker.RankedElement> ranked = LocatorRanker.rank(
                List.of(dialogSave, toolbarSave), "click", "Save");
        assertEquals(FailClosedDecision.Outcome.AMBIGUOUS, FailClosedDecision.evaluate(ranked, true).outcome());
        assertTrue(FailClosedDecision.equallySupportedDuplicates(ranked, "Save"));
    }
}
