package com.smartqa.browser.intelligence;

import com.smartqa.browser.multimodal.CandidateRelationshipGraph;

import java.util.List;

public record BrowserSnapshot(
        String url,
        String title,
        int interactiveCount,
        List<ElementCandidate> elements,
        List<String> consoleErrors,
        CandidateRelationshipGraph.Graph graph,
        String evidenceMomentId,
        List<PhysicalControl> controls,
        ElementTree tree,
        String treeVersion,
        String graphVersion
) {
    public BrowserSnapshot(
            String url,
            String title,
            int interactiveCount,
            List<ElementCandidate> elements,
            List<String> consoleErrors
    ) {
        this(
                url,
                title,
                interactiveCount,
                elements,
                consoleErrors,
                CandidateRelationshipGraph.build(elements),
                null,
                PhysicalControl.fromAll(elements),
                ElementTree.build(elements, ""),
                "",
                ""
        );
    }

    public BrowserSnapshot(
            String url,
            String title,
            int interactiveCount,
            List<ElementCandidate> elements,
            List<String> consoleErrors,
            CandidateRelationshipGraph.Graph graph,
            String evidenceMomentId,
            List<PhysicalControl> controls
    ) {
        this(
                url, title, interactiveCount, elements, consoleErrors, graph, evidenceMomentId, controls,
                ElementTree.build(elements, evidenceMomentId),
                evidenceMomentId == null ? "" : evidenceMomentId,
                evidenceMomentId == null ? "" : evidenceMomentId
        );
    }

    public CandidateRelationshipGraph.Graph graphOrBuild() {
        if (graph != null) {
            return graph;
        }
        return CandidateRelationshipGraph.build(elements);
    }

    public ElementTree treeOrBuild() {
        if (tree != null && tree.nodes() != null && !tree.nodes().isEmpty()) {
            return tree;
        }
        return ElementTree.build(elements, evidenceMomentId);
    }
}
