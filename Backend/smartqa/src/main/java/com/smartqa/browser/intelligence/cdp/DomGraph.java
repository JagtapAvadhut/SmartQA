package com.smartqa.browser.intelligence.cdp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parent/child/sibling graph over {@link NormalizedDomNode}.
 */
public final class DomGraph {

    private final List<NormalizedDomNode> nodes;
    private final Map<Integer, NormalizedDomNode> byIndex = new HashMap<>();

    public DomGraph(List<NormalizedDomNode> nodes) {
        this.nodes = nodes == null ? List.of() : List.copyOf(nodes);
        for (NormalizedDomNode node : this.nodes) {
            byIndex.put(node.nodeIndex(), node);
        }
    }

    public List<NormalizedDomNode> nodes() {
        return nodes;
    }

    public NormalizedDomNode node(int index) {
        return byIndex.get(index);
    }

    public NormalizedDomNode parent(NormalizedDomNode node) {
        if (node == null || node.parentIndex() < 0) {
            return null;
        }
        return byIndex.get(node.parentIndex());
    }

    public List<NormalizedDomNode> children(NormalizedDomNode node) {
        if (node == null) {
            return List.of();
        }
        List<NormalizedDomNode> out = new ArrayList<>();
        for (Integer idx : node.childIndexes()) {
            NormalizedDomNode child = byIndex.get(idx);
            if (child != null) {
                out.add(child);
            }
        }
        return out;
    }

    public List<NormalizedDomNode> ancestors(NormalizedDomNode node, int max) {
        List<NormalizedDomNode> out = new ArrayList<>();
        NormalizedDomNode cur = parent(node);
        int guard = 0;
        while (cur != null && guard < Math.max(1, max)) {
            out.add(cur);
            cur = parent(cur);
            guard++;
        }
        return out;
    }

    public List<NormalizedDomNode> findByText(String hint, int limit) {
        if (hint == null || hint.isBlank()) {
            return List.of();
        }
        String needle = hint.toLowerCase(Locale.ROOT);
        List<NormalizedDomNode> out = new ArrayList<>();
        for (NormalizedDomNode node : nodes) {
            String blob = (node.nodeName() + " " + node.nodeValue() + " " + node.ariaLabel()
                    + " " + node.id() + " " + node.nameAttr()).toLowerCase(Locale.ROOT);
            if (blob.contains(needle)) {
                out.add(node);
                if (out.size() >= Math.max(1, limit)) {
                    break;
                }
            }
        }
        return out;
    }

    public String compact(int limit) {
        StringBuilder sb = new StringBuilder();
        int n = Math.min(Math.max(1, limit), nodes.size());
        for (int i = 0; i < n; i++) {
            NormalizedDomNode node = nodes.get(i);
            if (!"BODY".equalsIgnoreCase(node.nodeName()) && !"HTML".equalsIgnoreCase(node.nodeName())
                    && !"SCRIPT".equalsIgnoreCase(node.nodeName()) && !"STYLE".equalsIgnoreCase(node.nodeName())) {
                sb.append(node.compact()).append('\n');
            }
        }
        return sb.toString();
    }
}
