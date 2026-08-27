package com.smartqa.browser.multimodal;

import com.smartqa.browser.intelligence.ElementCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CandidateRelationshipGraphTest {

    @Test
    void linksHeadingToOwnedCheckbox() {
        ElementCandidate heading = ElementCandidate.fromMap(Map.of(
                "candidateId", "h-brand",
                "tag", "h3",
                "role", "heading",
                "accessibleName", "Brand",
                "text", "Brand",
                "headingContext", "Brand",
                "region", "FILTER_PANEL"
        ), 0);
        ElementCandidate option = ElementCandidate.fromMap(Map.of(
                "candidateId", "opt-ak",
                "tag", "label",
                "role", "checkbox",
                "inputType", "checkbox",
                "accessibleName", "AK",
                "text", "AK",
                "headingContext", "Brand",
                "ancestorContext", "Brand Filters",
                "region", "FILTER_PANEL"
        ), 1);
        CandidateRelationshipGraph.Graph graph = CandidateRelationshipGraph.build(List.of(heading, option));
        assertTrue(graph.compact(10).toLowerCase().contains("ak"));
        assertTrue(CandidateRelationshipGraph.childrenOf(List.of(heading, option), "Brand").stream()
                .anyMatch(el -> "opt-ak".equals(el.candidateId())));
    }

    @Test
    void structuralContainerOwnsDescendantsNotPageWide() {
        ElementCandidate container = ElementCandidate.fromMap(Map.of(
                "candidateId", "brand-container",
                "tag", "section",
                "role", "group",
                "accessibleName", "Brand",
                "text", "Brand",
                "region", "FILTER_PANEL",
                "headingContext", "Brand",
                "containerId", "brand"
        ), 0);
        ElementCandidate owned = ElementCandidate.fromMap(Map.of(
                "candidateId", "opt-ak",
                "tag", "input",
                "role", "checkbox",
                "inputType", "checkbox",
                "accessibleName", "AK",
                "text", "AK",
                "parentId", "brand-container",
                "containerId", "brand",
                "headingContext", "Brand",
                "region", "FILTER_PANEL"
        ), 1);
        ElementCandidate distractor = ElementCandidate.fromMap(Map.of(
                "candidateId", "page-ak",
                "tag", "span",
                "accessibleName", "AK",
                "text", "AK",
                "region", "CONTENT",
                "headingContext", "Product title"
        ), 2);
        List<ElementCandidate> descendants = CandidateRelationshipGraph.descendantsOf(
                List.of(container, owned, distractor), container);
        assertTrue(descendants.stream().anyMatch(el -> "opt-ak".equals(el.candidateId())));
        List<ElementCandidate> children = CandidateRelationshipGraph.childrenOf(
                List.of(container, owned, distractor), "Brand");
        assertTrue(children.stream().anyMatch(el -> "opt-ak".equals(el.candidateId())));
    }
}
