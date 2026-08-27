package com.smartqa.browser.intelligence;

import com.smartqa.browser.multimodal.CandidateRelationshipGraph;
import com.smartqa.browser.multimodal.GraphRelation;

import java.util.ArrayList;
import java.util.List;

/**
 * Tree parent must match graph CHILD_OF. Mismatch is evidence inconsistency, not a guess.
 */
public final class TreeGraphReconciler {

    public record Result(boolean consistent, List<String> mismatches, List<ElementCandidate> stamped) {
    }

    private TreeGraphReconciler() {
    }

    public static Result reconcile(List<ElementCandidate> elements, ElementTree tree, CandidateRelationshipGraph.Graph graph) {
        if (elements == null || elements.isEmpty()) {
            return new Result(true, List.of(), List.of());
        }
        List<String> mismatches = new ArrayList<>();
        List<ElementCandidate> stamped = new ArrayList<>(elements.size());
        for (ElementCandidate el : elements) {
            boolean ok = true;
            if (el.structureOrEmpty().isActionableKind() || el.clickable()) {
                ElementTree.Node node = tree == null ? null : tree.node(el.candidateId());
                String treeParent = node == null ? "" : node.parentId();
                String graphParent = graphParentOf(graph, el.candidateId());
                if (!treeParent.isBlank() && !graphParent.isBlank() && !treeParent.equals(graphParent)) {
                    ok = false;
                    mismatches.add(el.candidateId());
                }
            }
            stamped.add(el.withStructure(el.structureOrEmpty().withReconciled(ok)));
        }
        return new Result(mismatches.isEmpty(), List.copyOf(mismatches), List.copyOf(stamped));
    }

    private static String graphParentOf(CandidateRelationshipGraph.Graph graph, String childId) {
        if (graph == null || graph.edges() == null || childId == null) {
            return "";
        }
        for (CandidateRelationshipGraph.Edge edge : graph.edges()) {
            if (childId.equals(edge.childId())
                    && (GraphRelation.CHILD_OF.name().equals(edge.relation())
                    || GraphRelation.PARENT_OF.name().equals(edge.relation())
                    || "parent".equalsIgnoreCase(edge.relation())
                    || "child".equalsIgnoreCase(edge.relation()))) {
                return edge.parentId() == null ? "" : edge.parentId();
            }
        }
        if (graph.parentByChild() != null) {
            String p = graph.parentByChild().get(childId);
            return p == null ? "" : p;
        }
        return "";
    }
}
