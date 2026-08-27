package com.smartqa.browser.multimodal;

import com.smartqa.browser.intelligence.ElementCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbsenceDiagnosisTest {

    @Test
    void visualWithoutCandidatesIsUnresolved() {
        SemanticTargetNormalizer.NormalizedTarget intent = SemanticTargetNormalizer.normalize("click", "select Brand AK");
        TargetHypothesis hypothesis = TargetHypothesis.visualUnresolved("AK visible under Brand");
        AbsenceDiagnosis diagnosis = AbsenceDiagnosis.inspect(
                List.of(), List.of(), intent, hypothesis, true);
        assertEquals("VISUAL_TARGET_PRESENT_DOM_UNRESOLVED", diagnosis.conclusion());
        assertTrue(diagnosis.visuallyPresent());
        assertTrue(diagnosis.missingFromCandidates());
    }

    @Test
    void noVisualNoCandidatesIsNotPresent() {
        ElementCandidate heading = ElementCandidate.fromMap(Map.of(
                "candidateId", "h1",
                "tag", "h3",
                "text", "Price",
                "accessibleName", "Price"
        ), 0);
        SemanticTargetNormalizer.NormalizedTarget intent = SemanticTargetNormalizer.normalize("click", "select Brand AK");
        AbsenceDiagnosis diagnosis = AbsenceDiagnosis.inspect(
                List.of(heading), List.of(), intent, TargetHypothesis.absent("not on page"), true);
        assertEquals("TARGET_NOT_PRESENT", diagnosis.conclusion());
    }
}
