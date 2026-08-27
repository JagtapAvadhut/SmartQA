package com.smartqa.browser.intelligence.cdp;

import com.smartqa.browser.intelligence.ElementCandidate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Merges CDP parent/layout/AX onto live {@link ElementCandidate}s. Playwright remains executor.
 */
public final class CdpCandidateEnricher {

    private CdpCandidateEnricher() {
    }

    public static List<ElementCandidate> enrich(List<ElementCandidate> elements, CdpCapture capture) {
        if (elements == null || elements.isEmpty() || capture == null || !capture.captured() || capture.graph() == null) {
            return elements == null ? List.of() : elements;
        }
        List<ElementCandidate> out = new ArrayList<>(elements.size());
        for (ElementCandidate el : elements) {
            NormalizedDomNode node = match(el, capture.graph());
            if (node == null) {
                out.add(el);
                continue;
            }
            String containerId = el.containerId();
            if ((containerId == null || containerId.isBlank()) && capture.graph().parent(node) != null) {
                NormalizedDomNode parent = capture.graph().parent(node);
                containerId = firstNonBlank(el.containerId(), parent.ariaLabel(), parent.id());
            }
            String bbox = node.visibleLayout()
                    ? node.x() + "," + node.y() + "," + node.width() + "," + node.height()
                    : "";
            AccessibilityNode ax = matchAx(el, capture.accessibility(), node.backendNodeId());
            String axRole = ax == null ? matchAxRole(el, capture.accessibility()) : ax.role();
            String axName = ax == null ? "" : ax.name();
            String axStates = ax == null ? "" : ax.states();
            out.add(el.withRelationshipIds(
                    el.parentId(),
                    containerId,
                    el.ancestorIds(),
                    el.siblingIds(),
                    firstNonBlank(el.frameId(), node.frameUrl()),
                    el.shadowRootId()
            ).withRoleAndBox(axRole, bbox)
                    .withStructure(el.structureOrEmpty().withCdp(
                            String.valueOf(node.backendNodeId()), axRole, axName, axStates)));
        }
        return List.copyOf(out);
    }

    private static NormalizedDomNode match(ElementCandidate el, DomGraph graph) {
        String needle = firstNonBlank(el.accessibleName(), el.text(), el.ariaLabel(), el.id()).toLowerCase(Locale.ROOT);
        if (needle.length() < 2) {
            return bboxMatch(el, graph);
        }
        NormalizedDomNode best = null;
        int bestScore = 0;
        for (NormalizedDomNode node : graph.nodes()) {
            String blob = (node.nodeName() + " " + node.nodeValue() + " " + node.ariaLabel()
                    + " " + node.id() + " " + node.nameAttr()).toLowerCase(Locale.ROOT);
            int score = 0;
            if (blob.contains(needle)) {
                score += needle.length();
            }
            if (bboxOverlaps(el.boundingBox(), node)) {
                score += 20;
            }
            if (score > bestScore) {
                bestScore = score;
                best = node;
            }
        }
        return bestScore >= 8 ? best : bboxMatch(el, graph);
    }

    private static NormalizedDomNode bboxMatch(ElementCandidate el, DomGraph graph) {
        for (NormalizedDomNode node : graph.nodes()) {
            if (bboxOverlaps(el.boundingBox(), node) && node.visibleLayout()) {
                return node;
            }
        }
        return null;
    }

    private static boolean bboxOverlaps(String bbox, NormalizedDomNode node) {
        if (bbox == null || bbox.isBlank() || node == null) {
            return false;
        }
        String[] parts = bbox.split(",");
        if (parts.length < 4) {
            return false;
        }
        try {
            double x = Double.parseDouble(parts[0].trim());
            double y = Double.parseDouble(parts[1].trim());
            double w = Double.parseDouble(parts[2].trim());
            double h = Double.parseDouble(parts[3].trim());
            return x < node.x() + node.width()
                    && x + w > node.x()
                    && y < node.y() + node.height()
                    && y + h > node.y();
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static AccessibilityNode matchAx(ElementCandidate el, List<AccessibilityNode> accessibility, int backendNodeId) {
        if (accessibility == null || accessibility.isEmpty()) {
            return null;
        }
        if (backendNodeId > 0) {
            for (AccessibilityNode node : accessibility) {
                if (node != null && node.backendDomNodeId() == backendNodeId && !node.ignored()) {
                    return node;
                }
            }
        }
        String needle = firstNonBlank(el.accessibleName(), el.text(), el.ariaLabel()).toLowerCase(Locale.ROOT);
        if (needle.length() < 2) {
            return null;
        }
        for (AccessibilityNode node : accessibility) {
            if (node == null || node.ignored() || node.name() == null || node.name().isBlank()) {
                continue;
            }
            String name = node.name().toLowerCase(Locale.ROOT);
            if (name.contains(needle) || needle.contains(name)) {
                return node;
            }
        }
        return null;
    }

    private static String matchAxRole(ElementCandidate el, List<AccessibilityNode> accessibility) {
        if (accessibility == null || accessibility.isEmpty()) {
            return "";
        }
        String needle = firstNonBlank(el.accessibleName(), el.text(), el.ariaLabel()).toLowerCase(Locale.ROOT);
        if (needle.length() < 2) {
            return "";
        }
        for (AccessibilityNode node : accessibility) {
            if (node == null || node.ignored() || node.name() == null || node.name().isBlank()) {
                continue;
            }
            String name = node.name().toLowerCase(Locale.ROOT);
            if (name.contains(needle) || needle.contains(name)) {
                return node.role() == null ? "" : node.role();
            }
        }
        return "";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
