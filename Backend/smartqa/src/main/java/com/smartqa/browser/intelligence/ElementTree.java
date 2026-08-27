package com.smartqa.browser.intelligence;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Hierarchical view of the same {@link ElementCandidate} inventory. Not a second store.
 */
public record ElementTree(String rootId, Map<String, Node> nodes, String version) {

    public record Node(String candidateId, String parentId, List<String> childIds, int depth, String treePath) {
        public Node {
            childIds = childIds == null ? List.of() : List.copyOf(childIds);
            treePath = treePath == null ? "" : treePath;
            parentId = parentId == null ? "" : parentId;
            candidateId = candidateId == null ? "" : candidateId;
        }
    }

    public static ElementTree empty(String version) {
        return new ElementTree("PAGE", Map.of(), version == null ? "" : version);
    }

    public static ElementTree build(List<ElementCandidate> elements, String version) {
        if (elements == null || elements.isEmpty()) {
            return empty(version);
        }
        Map<String, ElementCandidate> byId = new HashMap<>();
        for (ElementCandidate el : elements) {
            if (el != null && el.candidateId() != null && !el.candidateId().isBlank()) {
                byId.put(el.candidateId(), el);
            }
        }
        Map<String, List<String>> children = new HashMap<>();
        for (ElementCandidate el : elements) {
            if (el == null) {
                continue;
            }
            String parent = el.parentId();
            if (parent != null && !parent.isBlank() && byId.containsKey(parent)) {
                children.computeIfAbsent(parent, key -> new ArrayList<>()).add(el.candidateId());
            }
        }
        Map<String, Node> nodes = new HashMap<>();
        List<String> roots = new ArrayList<>();
        for (ElementCandidate el : elements) {
            if (el == null) {
                continue;
            }
            String parent = el.parentId();
            if (parent == null || parent.isBlank() || !byId.containsKey(parent)) {
                roots.add(el.candidateId());
            }
        }
        if (roots.isEmpty()) {
            roots.add(elements.getFirst().candidateId());
        }
        for (String root : roots) {
            dfs(byId, children, root, "", 1, nodes, new HashSet<>());
        }
        return new ElementTree(roots.getFirst(), Map.copyOf(nodes), version == null ? "" : version);
    }

    public Node node(String candidateId) {
        if (candidateId == null || nodes == null) {
            return null;
        }
        return nodes.get(candidateId);
    }

    public List<String> descendants(String candidateId) {
        if (candidateId == null || nodes == null || !nodes.containsKey(candidateId)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        ArrayDeque<String> q = new ArrayDeque<>(nodes.get(candidateId).childIds());
        while (!q.isEmpty()) {
            String id = q.removeFirst();
            if (id == null || id.isBlank() || out.contains(id)) {
                continue;
            }
            out.add(id);
            Node n = nodes.get(id);
            if (n != null) {
                q.addAll(n.childIds());
            }
        }
        return out;
    }

    public List<ElementCandidate> stamp(List<ElementCandidate> elements) {
        if (elements == null || elements.isEmpty()) {
            return List.of();
        }
        List<ElementCandidate> out = new ArrayList<>(elements.size());
        for (ElementCandidate el : elements) {
            Node n = node(el.candidateId());
            if (n == null) {
                out.add(el);
                continue;
            }
            out.add(el.withStructure(el.structureOrEmpty().withTree(
                    n.treePath(), n.depth(), String.join(",", n.childIds()))));
        }
        return List.copyOf(out);
    }

    public ElementCandidate findByHint(List<ElementCandidate> elements, String hint) {
        if (elements == null || hint == null || hint.isBlank()) {
            return null;
        }
        String needle = hint.toLowerCase(Locale.ROOT).trim();
        ElementCandidate best = null;
        int bestScore = 0;
        for (ElementCandidate el : elements) {
            int score = 0;
            String name = (nullToEmpty(el.accessibleName()) + " " + nullToEmpty(el.text()) + " "
                    + nullToEmpty(el.headingContext()) + " " + nullToEmpty(el.region())).toLowerCase(Locale.ROOT);
            if (needle.equalsIgnoreCase(el.candidateId())) {
                score += 100;
            }
            if (name.contains(needle)) {
                score += 40 + Math.min(needle.length(), 20);
            }
            if (el.structureOrEmpty().isContainer()) {
                score += 15;
            }
            String region = el.region() == null ? "" : el.region();
            if ("FILTER_PANEL".equalsIgnoreCase(region) || "SIDEBAR".equalsIgnoreCase(region)) {
                score += 30;
            }
            if (score > bestScore) {
                bestScore = score;
                best = el;
            }
        }
        return bestScore >= 20 ? best : null;
    }

    private static void dfs(
            Map<String, ElementCandidate> byId,
            Map<String, List<String>> children,
            String id,
            String parentId,
            int depth,
            Map<String, Node> nodes,
            Set<String> visiting
    ) {
        if (id == null || nodes.containsKey(id) || visiting.contains(id)) {
            return;
        }
        visiting.add(id);
        ElementCandidate el = byId.get(id);
        String label = labelOf(el, id);
        Node parentNode = parentId.isBlank() ? null : nodes.get(parentId);
        String path = parentNode == null || parentNode.treePath().isBlank()
                ? "PAGE > " + label
                : parentNode.treePath() + " > " + label;
        List<String> kids = List.copyOf(children.getOrDefault(id, List.of()));
        nodes.put(id, new Node(id, parentId, kids, depth, path));
        for (String child : kids) {
            dfs(byId, children, child, id, depth + 1, nodes, visiting);
        }
        visiting.remove(id);
    }

    private static String labelOf(ElementCandidate el, String fallback) {
        if (el == null) {
            return fallback;
        }
        if (el.accessibleName() != null && !el.accessibleName().isBlank()) {
            return el.accessibleName().replaceAll("\\s+", " ").trim();
        }
        if (el.text() != null && !el.text().isBlank()) {
            return el.text().replaceAll("\\s+", " ").trim();
        }
        if (el.headingContext() != null && !el.headingContext().isBlank()) {
            return el.headingContext().trim();
        }
        return el.tag() == null || el.tag().isBlank() ? fallback : el.tag();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
