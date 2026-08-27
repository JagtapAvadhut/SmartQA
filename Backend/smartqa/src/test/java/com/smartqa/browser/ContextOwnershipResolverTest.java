package com.smartqa.browser;

import com.smartqa.browser.intelligence.ElementCandidate;
import com.smartqa.browser.intelligence.ElementTree;
import com.smartqa.browser.intelligence.HardConstraint;
import com.smartqa.browser.intelligence.HardConstraintChecker;
import com.smartqa.browser.multimodal.CandidateRelationshipGraph;
import com.smartqa.intent.InstructionIntentCompiler;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextOwnershipResolverTest {

    @Test
    void insideTypoCompilesToContainerContext() {
        InstructionIntentCompiler.Result result = InstructionIntentCompiler.compile(
                """
                open https://example.com/
                insite brand click Search Brand
                selct AK checkbox
                """,
                "https://example.com/");
        assertTrue(result.usable());
        var steps = result.contract().scenarios().getFirst().steps();
        assertTrue(steps.stream().anyMatch(step ->
                step.containerContext() != null && step.containerContext().toLowerCase().contains("brand")));
    }

    @Test
    void formOwnershipStaysInsideOwner() {
        ElementCandidate form = element("login-form", "form", "Login", "", "");
        ElementCandidate user = element("user", "input", "Username", "login-form", "login-form");
        ElementCandidate headerUser = element("header-user", "span", "Username", "header", "header");
        List<ElementCandidate> elements = List.of(form, user, headerUser);
        ElementTree tree = ElementTree.build(elements, "m1");
        CandidateRelationshipGraph.Graph graph = CandidateRelationshipGraph.build(tree.stamp(elements), tree);
        List<ElementCandidate> owned = CandidateRelationshipGraph.descendantsOf(elements, graph, form);
        assertTrue(owned.stream().anyMatch(el -> "user".equals(el.candidateId())));
        assertTrue(owned.stream().noneMatch(el -> "header-user".equals(el.candidateId())));
    }

    @Test
    void wrongOwnerIsHardRejected() {
        ElementCandidate headerAk = ElementCandidate.fromMap(map(
                "candidateId", "header-ak",
                "tag", "input",
                "role", "checkbox",
                "inputType", "checkbox",
                "text", "AK",
                "accessibleName", "AK",
                "region", "HEADER",
                "headingContext", "Navigation",
                "visible", true,
                "enabled", true,
                "clickable", true
        ), 0);
        assertEquals(HardConstraint.INVALID_OWNER,
                HardConstraintChecker.evaluate(headerAk, "checkbox", "Brand"));
    }

    private static ElementCandidate element(String id, String tag, String text, String parentId, String containerId) {
        return ElementCandidate.fromMap(map(
                "candidateId", id,
                "tag", tag,
                "accessibleName", text,
                "text", text,
                "parentId", parentId,
                "containerId", containerId,
                "visible", true,
                "enabled", true
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
