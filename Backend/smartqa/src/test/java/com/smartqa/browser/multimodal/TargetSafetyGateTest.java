package com.smartqa.browser.multimodal;

import com.smartqa.browser.intelligence.ElementCandidate;
import com.smartqa.browser.intelligence.LocatorRanker;
import com.smartqa.browser.intelligence.RankedLocator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetSafetyGateTest {

    @Test
    void acceptsOwnedFilterCandidate() {
        ElementCandidate ak = ElementCandidate.fromMap(Map.of(
                "candidateId", "opt-ak",
                "tag", "label",
                "role", "checkbox",
                "accessibleName", "AK",
                "text", "AK",
                "headingContext", "Brand",
                "ancestorContext", "Brand",
                "region", "FILTER_PANEL",
                "visible", true,
                "enabled", true
        ), 1);
        LocatorRanker.RankedElement ranked = new LocatorRanker.RankedElement(
                ak, 180, List.of(new RankedLocator("label", "AK", 0.9, "owned", 0.8)));
        TargetHypothesis hypothesis = new TargetHypothesis(
                "FILTER_TARGET", "Brand", "AK", "opt-ak",
                "resolve_child_of_filter_container", 0.9, true, true, "AK under Brand", List.of());
        SemanticTargetNormalizer.NormalizedTarget intent = SemanticTargetNormalizer.normalize("click", "select Brand AK");
        TargetSafetyGate.GateResult result = TargetSafetyGate.verify(hypothesis, List.of(ranked), intent);
        assertTrue(result.accepted());
        assertEquals("opt-ak", result.ranked().element().candidateId());
    }

    @Test
    void rejectsUnsafePlaywrightStrategy() {
        TargetHypothesis hypothesis = new TargetHypothesis(
                "FILTER_TARGET", "Brand", "AK", "opt-ak",
                "page.locator('css=div')", 0.99, true, true, "unsafe", List.of());
        TargetSafetyGate.GateResult result = TargetSafetyGate.verify(
                hypothesis, List.of(), SemanticTargetNormalizer.normalize("click", "Brand AK"));
        assertFalse(result.accepted());
        assertEquals("AI_IDENTIFIED_LIVE_VERIFICATION_FAILED", result.outcome());
    }

    @Test
    void rejectsWrongAiCandidateThatIsNotOwned() {
        ElementCandidate header = ElementCandidate.fromMap(Map.of(
                "candidateId", "header-ak",
                "tag", "a",
                "role", "link",
                "accessibleName", "AK",
                "text", "AK",
                "region", "HEADER",
                "headingContext", "Explore Plus Login Cart",
                "visible", true,
                "enabled", true,
                "clickable", true
        ), 1);
        LocatorRanker.RankedElement ranked = new LocatorRanker.RankedElement(
                header, 90, List.of(new RankedLocator("text", "AK", 0.4, "header", 0.3)));
        TargetHypothesis hypothesis = new TargetHypothesis(
                "FILTER_TARGET", "Brand", "AK", "header-ak",
                "resolve_child_of_filter_container", 0.99, true, true, "wrong chrome match", List.of());
        TargetSafetyGate.GateResult result = TargetSafetyGate.verify(
                hypothesis, List.of(ranked), SemanticTargetNormalizer.normalize("click", "select Brand AK"));
        assertFalse(result.accepted());
        assertEquals("AI_IDENTIFIED_LIVE_VERIFICATION_FAILED", result.outcome());
    }

    @Test
    void rejectsVisualRegionWithoutClickableCandidate() {
        ElementCandidate image = ElementCandidate.fromMap(Map.of(
                "candidateId", "img-1",
                "tag", "img",
                "accessibleName", "Sneaker Project",
                "boundingBox", "100,80,300,120",
                "visible", true,
                "enabled", true,
                "clickable", false
        ), 1);
        LocatorRanker.RankedElement ranked = new LocatorRanker.RankedElement(
                image, 100, List.of(new RankedLocator("css", "img", 0.5, "image", 0.4)));
        TargetHypothesis hypothesis = new TargetHypothesis(
                "GENERIC_TARGET", null, "Sneaker Project", "img-1",
                "resolve_child_of_filter_container", 0.9, true, true, "banner in screenshot", List.of(),
                TargetType.IMAGE_TEXT_TARGET, "THE SNEAKER PROJECT", new VisualRegion(100, 80, 300, 120));
        TargetSafetyGate.GateResult result = TargetSafetyGate.verify(
                hypothesis, List.of(ranked), SemanticTargetNormalizer.normalize("click", "sneaker project banner"));
        assertFalse(result.accepted());
        assertEquals("VISUAL_TARGET_PRESENT_DOM_UNRESOLVED", result.outcome());
    }

    @Test
    void screenshotDomMismatchStaysUnresolved() {
        TargetHypothesis hypothesis = TargetHypothesis.visualUnresolved("AK visible in screenshot");
        TargetSafetyGate.GateResult result = TargetSafetyGate.verify(
                hypothesis, List.of(), SemanticTargetNormalizer.normalize("click", "Brand AK"));
        assertFalse(result.accepted());
        assertEquals("VISUAL_TARGET_PRESENT_DOM_UNRESOLVED", result.outcome());
    }

    @Test
    void rejectsCandidateIdNotInLiveGraph() {
        ElementCandidate ak = ElementCandidate.fromMap(Map.of(
                "candidateId", "opt-ak",
                "tag", "label",
                "role", "checkbox",
                "accessibleName", "AK",
                "text", "AK",
                "visible", true,
                "enabled", true
        ), 1);
        LocatorRanker.RankedElement ranked = new LocatorRanker.RankedElement(
                ak, 180, List.of(new RankedLocator("label", "AK", 0.9, "owned", 0.8)));
        TargetHypothesis hypothesis = new TargetHypothesis(
                "FILTER_TARGET", "Brand", "AK", "invented-id",
                "resolve_child_of_filter_container", 0.99, true, true, "hallucinated", List.of());
        TargetSafetyGate.GateResult result = TargetSafetyGate.verify(
                hypothesis, List.of(ranked), SemanticTargetNormalizer.normalize("click", "select Brand AK"));
        assertFalse(result.accepted());
        assertEquals("AI_IDENTIFIED_LIVE_VERIFICATION_FAILED", result.outcome());
    }
}
