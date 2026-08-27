package com.smartqa.browser.intelligence;

import com.smartqa.browser.multimodal.CandidateRelationshipGraph;
import com.smartqa.browser.multimodal.GraphRelation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElementTreeGraphTest {

    @Test
    void parentChildJoinOnCandidateIds() {
        ElementCandidate brand = candidate("brand", "section", "Brand", "", "");
        ElementCandidate ak = candidate("ak", "input", "AK", "brand", "brand");
        ElementCandidate aema = candidate("aema", "input", "AEMA", "brand", "brand");
        List<ElementCandidate> elements = List.of(brand, ak, aema);
        ElementTree tree = ElementTree.build(elements, "m1");
        List<ElementCandidate> stamped = tree.stamp(elements);
        CandidateRelationshipGraph.Graph graph = CandidateRelationshipGraph.build(stamped, tree);

        assertEquals("brand", tree.node("ak").parentId());
        assertTrue(tree.descendants("brand").contains("ak"));
        assertTrue(tree.descendants("brand").contains("aema"));
        assertTrue(CandidateRelationshipGraph.descendantsOf(stamped, graph, brand).stream()
                .anyMatch(el -> "ak".equals(el.candidateId())));
        assertTrue(graph.edges().stream().anyMatch(edge ->
                GraphRelation.PARENT_OF.name().equals(edge.relation())
                        && "brand".equals(edge.parentId())
                        && "ak".equals(edge.childId())));
        TreeGraphReconciler.Result result = TreeGraphReconciler.reconcile(stamped, tree, graph);
        assertTrue(result.consistent());
    }

    @Test
    void coveredCandidateIsHardRejected() {
        Map<String, Object> raw = map(
                "candidateId", "hidden-btn",
                "tag", "button",
                "text", "Apply",
                "visible", true,
                "enabled", true,
                "clickable", true,
                "covered", true
        );
        ElementCandidate covered = ElementCandidate.fromMap(raw, 0)
                .withStructure(ElementStructure.fromMap(raw).withCovered(true, 9));
        assertEquals(HardConstraint.COVERED, HardConstraintChecker.evaluate(covered, "click", null));
    }

    private static ElementCandidate candidate(String id, String tag, String text, String parentId, String containerId) {
        return ElementCandidate.fromMap(map(
                "candidateId", id,
                "tag", tag,
                "role", "input".equals(tag) ? "checkbox" : "group",
                "accessibleName", text,
                "text", text,
                "parentId", parentId,
                "containerId", containerId,
                "headingContext", "Brand",
                "region", "FILTER_PANEL",
                "visible", true,
                "enabled", true,
                "clickable", true
        ), 0);
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> out = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            out.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return out;
    }
}
