package com.smartqa.browser.multimodal;

import com.smartqa.browser.intelligence.DomEvidence;
import com.smartqa.browser.intelligence.ElementCandidate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parent/child/ancestor/container relationships for ranking. Used in normal resolution, not only AI evidence.
 */
public final class CandidateRelationshipGraph {

    public record Edge(
            String parentId,
            String childId,
            String relation,
            String parentHeading,
            String childText
    ) {
    }

    public record Graph(List<Edge> edges, Map<String, List<String>> childrenByParent, Map<String, String> parentByChild) {
        public Graph(List<Edge> edges) {
            this(edges, indexChildren(edges), indexParents(edges));
        }

        public String compact(int limit) {
            if (edges == null || edges.isEmpty()) {
                return "(no relationships)";
            }
            StringBuilder sb = new StringBuilder();
            int n = Math.min(Math.max(1, limit), edges.size());
            for (int i = 0; i < n; i++) {
                Edge e = edges.get(i);
                sb.append(e.relation())
                        .append(": ")
                        .append(e.parentHeading())
                        .append(" -> ")
                        .append(e.childText())
                        .append(" [")
                        .append(e.childId())
                        .append("]\n");
            }
            return sb.toString();
        }

        public List<String> childrenIds(String parentId) {
            if (parentId == null || parentId.isBlank() || childrenByParent == null) {
                return List.of();
            }
            return childrenByParent.getOrDefault(parentId, List.of());
        }
    }

    private CandidateRelationshipGraph() {
    }

    public static Graph build(List<ElementCandidate> elements) {
        return build(elements, null);
    }

    public static Graph build(List<ElementCandidate> elements, com.smartqa.browser.intelligence.ElementTree tree) {
        if (elements == null || elements.isEmpty()) {
            return new Graph(List.of());
        }
        List<Edge> edges = new ArrayList<>();
        List<ElementCandidate> headings = elements.stream()
                .filter(CandidateRelationshipGraph::looksLikeHeading)
                .toList();
        for (ElementCandidate child : elements) {
            String childText = firstNonBlank(child.accessibleName(), child.text(), child.label(), child.value());
            addStructuralEdges(edges, child, childText);
            if (childText.isBlank()) {
                continue;
            }
            ElementCandidate heading = nearestHeading(headings, child);
            if (heading != null) {
                edges.add(new Edge(
                        heading.candidateId(),
                        child.candidateId(),
                        GraphRelation.OWNS.name(),
                        firstNonBlank(heading.accessibleName(), heading.text(), heading.headingContext()),
                        childText
                ));
            } else if (child.hasAssociatedControl()) {
                edges.add(new Edge(
                        child.candidateId(),
                        child.candidateId(),
                        GraphRelation.LABELS.name(),
                        firstNonBlank(child.label(), child.accessibleName()),
                        childText
                ));
            }
        }
        addSpatialEdges(edges, elements);
        return new Graph(List.copyOf(edges));
    }

    /**
     * Prefer structural descendants of an owner/container; fall back to text ownership.
     * Never page-wide text search when a container exists.
     */
    public static List<ElementCandidate> childrenOf(List<ElementCandidate> elements, String ownerHint) {
        if (elements == null || ownerHint == null || ownerHint.isBlank()) {
            return List.of();
        }
        Graph graph = build(elements);
        List<ElementCandidate> structural = descendantsOf(elements, graph, ownerHint);
        if (!structural.isEmpty()) {
            return structural;
        }
        List<ElementCandidate> owned = new ArrayList<>();
        for (ElementCandidate el : elements) {
            if (!el.visible()) {
                continue;
            }
            if (DomEvidence.ownsContext(el, ownerHint) || ownerIdMatches(el, ownerHint)) {
                owned.add(el);
            }
        }
        return owned;
    }

    public static List<ElementCandidate> descendantsOf(List<ElementCandidate> elements, ElementCandidate owner) {
        if (owner == null) {
            return List.of();
        }
        return descendantsOf(elements, build(elements), firstNonBlank(owner.candidateId(), owner.containerId(), owner.headingContext()));
    }

    public static List<ElementCandidate> descendantsOf(
            List<ElementCandidate> elements,
            Graph graph,
            String ownerHint
    ) {
        if (elements == null || ownerHint == null || ownerHint.isBlank()) {
            return List.of();
        }
        String needle = ownerHint.toLowerCase(Locale.ROOT).trim();
        ElementCandidate owner = null;
        for (ElementCandidate el : elements) {
            if (el.candidateId().equalsIgnoreCase(ownerHint)
                    || (el.accessibleName() != null && el.accessibleName().equalsIgnoreCase(ownerHint))
                    || (el.headingContext() != null && el.headingContext().equalsIgnoreCase(ownerHint))) {
                owner = el;
                break;
            }
        }
        if (owner != null && graph != null && graph.childrenByParent() != null) {
            List<ElementCandidate> bfs = bfsDescendants(elements, graph, owner.candidateId());
            if (!bfs.isEmpty()) {
                return bfs;
            }
        }
        List<ElementCandidate> out = new ArrayList<>();
        for (ElementCandidate el : elements) {
            if (!el.visible()) {
                continue;
            }
            if (ownerIdMatches(el, needle)) {
                out.add(el);
            }
        }
        return out;
    }

    public static List<ElementCandidate> descendantsOf(List<ElementCandidate> elements, Graph graph, ElementCandidate owner) {
        if (owner == null) {
            return List.of();
        }
        List<ElementCandidate> bfs = graph == null ? List.of() : bfsDescendants(elements, graph, owner.candidateId());
        return bfs.isEmpty() ? descendantsOf(elements, owner) : bfs;
    }

    private static List<ElementCandidate> bfsDescendants(List<ElementCandidate> elements, Graph graph, String rootId) {
        if (graph == null || graph.childrenByParent() == null || rootId == null) {
            return List.of();
        }
        Map<String, ElementCandidate> byId = new HashMap<>();
        for (ElementCandidate el : elements) {
            byId.put(el.candidateId(), el);
        }
        List<ElementCandidate> out = new ArrayList<>();
        java.util.ArrayDeque<String> q = new java.util.ArrayDeque<>(graph.childrenIds(rootId));
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        while (!q.isEmpty()) {
            String id = q.removeFirst();
            if (id == null || !seen.add(id)) {
                continue;
            }
            ElementCandidate el = byId.get(id);
            if (el != null && el.visible()) {
                out.add(el);
            }
            q.addAll(graph.childrenIds(id));
        }
        return out;
    }

    private static void addSpatialEdges(List<Edge> edges, List<ElementCandidate> elements) {
        int added = 0;
        for (int i = 0; i < elements.size() && added < 200; i++) {
            ElementCandidate a = elements.get(i);
            com.smartqa.browser.intelligence.LayoutGeometry.Box ba =
                    com.smartqa.browser.intelligence.LayoutGeometry.parse(a.boundingBox());
            if (ba == null) {
                continue;
            }
            for (int j = i + 1; j < elements.size() && added < 200; j++) {
                ElementCandidate b = elements.get(j);
                com.smartqa.browser.intelligence.LayoutGeometry.Box bb =
                        com.smartqa.browser.intelligence.LayoutGeometry.parse(b.boundingBox());
                if (bb == null) {
                    continue;
                }
                if (com.smartqa.browser.intelligence.LayoutGeometry.contains(ba, bb)) {
                    edges.add(new Edge(a.candidateId(), b.candidateId(), GraphRelation.VISUALLY_CONTAINS.name(), a.candidateId(), b.text()));
                    added++;
                } else if (com.smartqa.browser.intelligence.LayoutGeometry.overlaps(ba, bb)) {
                    int za = a.structureOrEmpty().zIndex();
                    int zb = b.structureOrEmpty().zIndex();
                    if (za > zb && ba.area() >= bb.area()) {
                        edges.add(new Edge(a.candidateId(), b.candidateId(), GraphRelation.COVERS.name(), a.candidateId(), b.text()));
                    } else {
                        edges.add(new Edge(a.candidateId(), b.candidateId(), GraphRelation.OVERLAPS.name(), a.candidateId(), b.text()));
                    }
                    added++;
                } else if (com.smartqa.browser.intelligence.LayoutGeometry.near(ba, bb, 48)) {
                    edges.add(new Edge(a.candidateId(), b.candidateId(), GraphRelation.VISUALLY_NEAR.name(), a.candidateId(), b.text()));
                    added++;
                }
            }
        }
    }

    private static void addStructuralEdges(List<Edge> edges, ElementCandidate child, String childText) {
        String text = childText == null || childText.isBlank()
                ? firstNonBlank(child.tag(), child.candidateId())
                : childText;
        if (!isBlank(child.parentId())) {
            edges.add(new Edge(child.parentId(), child.candidateId(), GraphRelation.PARENT_OF.name(), child.parentId(), text));
            edges.add(new Edge(child.candidateId(), child.parentId(), GraphRelation.CHILD_OF.name(), text, child.parentId()));
        }
        if (!isBlank(child.containerId())) {
            edges.add(new Edge(child.containerId(), child.candidateId(), containerRelation(child), child.containerId(), text));
        }
        for (String ancestor : child.ancestorIdList()) {
            edges.add(new Edge(ancestor, child.candidateId(), GraphRelation.ANCESTOR_OF.name(), ancestor, text));
        }
        for (String sibling : child.siblingIdList()) {
            edges.add(new Edge(child.candidateId(), sibling, GraphRelation.SIBLING_OF.name(), child.candidateId(), sibling));
        }
        if (!isBlank(child.frameId()) && !"main".equalsIgnoreCase(child.frameId())) {
            edges.add(new Edge(child.frameId(), child.candidateId(), GraphRelation.FRAME_OF.name(), child.frameId(), text));
        }
        if (!isBlank(child.shadowRootId())) {
            edges.add(new Edge(child.shadowRootId(), child.candidateId(), GraphRelation.SHADOW_ROOT_OF.name(), child.shadowRootId(), text));
        }
        if (child.hasAssociatedControl()) {
            edges.add(new Edge(child.candidateId(), child.candidateId(), GraphRelation.CONTROLS.name(), child.associatedControlRole(), text));
        }
    }

    private static String containerRelation(ElementCandidate child) {
        String region = child.region() == null ? "" : child.region().toLowerCase(Locale.ROOT);
        String tag = child.parentTag() == null ? "" : child.parentTag().toLowerCase(Locale.ROOT);
        String container = child.containerId() == null ? "" : child.containerId().toLowerCase(Locale.ROOT);
        if (container.contains("dialog") || "dialog".equals(region)) {
            return GraphRelation.DIALOG_OWNS.name();
        }
        if (container.contains("menu") || "menu".equalsIgnoreCase(child.role())) {
            return GraphRelation.MENU_OWNS.name();
        }
        if (container.contains("form") || "form".equals(tag)) {
            return GraphRelation.FORM_CONTROL.name();
        }
        if (container.contains("filter") || "filter_panel".equalsIgnoreCase(region) || "FILTER_PANEL".equalsIgnoreCase(region)) {
            return GraphRelation.FILTER_OWNS.name();
        }
        if (container.contains("search") || "SEARCH_AREA".equalsIgnoreCase(region)) {
            return GraphRelation.SEARCH_OWNS.name();
        }
        if (container.contains("card")) {
            return GraphRelation.CARD_OWNS.name();
        }
        if ("tr".equals(tag) || "table".equals(tag) || container.startsWith("tr")) {
            return GraphRelation.ROW_OWNS.name();
        }
        return GraphRelation.CONTAINS.name();
    }

    private static boolean ownerIdMatches(ElementCandidate el, String needle) {
        String blob = (el.containerId() + " " + el.parentId() + " " + el.headingContext()
                + " " + el.ancestorContext() + " " + el.region()).toLowerCase(Locale.ROOT);
        return blob.contains(needle);
    }

    private static Map<String, List<String>> indexChildren(List<Edge> edges) {
        Map<String, List<String>> map = new HashMap<>();
        if (edges == null) {
            return Map.of();
        }
        for (Edge edge : edges) {
            if (edge.parentId() == null || edge.childId() == null) {
                continue;
            }
            map.computeIfAbsent(edge.parentId(), key -> new ArrayList<>()).add(edge.childId());
        }
        return map;
    }

    private static Map<String, String> indexParents(List<Edge> edges) {
        Map<String, String> map = new HashMap<>();
        if (edges == null) {
            return Map.of();
        }
        for (Edge edge : edges) {
            if (edge.childId() != null && edge.parentId() != null && !map.containsKey(edge.childId())) {
                map.put(edge.childId(), edge.parentId());
            }
        }
        return map;
    }

    private static ElementCandidate nearestHeading(List<ElementCandidate> headings, ElementCandidate child) {
        String blob = child.ownershipContext();
        ElementCandidate best = null;
        int bestLen = 0;
        for (ElementCandidate heading : headings) {
            String name = firstNonBlank(heading.accessibleName(), heading.text(), heading.headingContext())
                    .toLowerCase(Locale.ROOT);
            if (name.length() < 2) {
                continue;
            }
            if (blob.contains(name) && name.length() > bestLen) {
                best = heading;
                bestLen = name.length();
            }
        }
        return best;
    }

    private static String relation(ElementCandidate heading, ElementCandidate child) {
        String role = child.role() == null ? "" : child.role().toLowerCase(Locale.ROOT);
        String type = child.inputType() == null ? "" : child.inputType().toLowerCase(Locale.ROOT);
        if ("checkbox".equals(role) || "checkbox".equals(type)) {
            return "owner";
        }
        if ("radio".equals(role) || "radio".equals(type)) {
            return "owner";
        }
        if (child.isLabel() || child.hasAssociatedControl()) {
            return "label";
        }
        return "container";
    }

    private static boolean looksLikeHeading(ElementCandidate el) {
        String tag = el.tag() == null ? "" : el.tag().toLowerCase(Locale.ROOT);
        String role = el.role() == null ? "" : el.role().toLowerCase(Locale.ROOT);
        return tag.matches("h[1-6]") || "heading".equals(role) || "legend".equals(tag) || "legend".equals(role);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return "";
    }
}
