package com.smartqa.browser.intelligence.cdp;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses Chromium {@code DOMSnapshot.captureSnapshot} and {@code Accessibility.getFullAXTree}.
 */
public final class CdpSnapshotParser {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private CdpSnapshotParser() {
    }

    public static CdpCapture parse(Object snapshotRaw, Object axRaw, String url, String title) {
        JsonNode snapshot = toJson(snapshotRaw);
        List<NormalizedDomNode> nodes = parseSnapshot(snapshot, url);
        List<AccessibilityNode> ax = parseAccessibility(toJson(axRaw));
        return new CdpCapture(true, "chromium", url == null ? "" : url, title == null ? "" : title,
                new DomGraph(nodes), ax, null, java.time.Instant.now());
    }

    public static CdpCapture parseJson(String snapshotJson, String axJson, String url, String title) {
        try {
            JsonNode snap = snapshotJson == null || snapshotJson.isBlank()
                    ? MAPPER.createObjectNode()
                    : MAPPER.readTree(snapshotJson);
            JsonNode ax = axJson == null || axJson.isBlank()
                    ? MAPPER.createObjectNode()
                    : MAPPER.readTree(axJson);
            return parse(snap, ax, url, title);
        } catch (RuntimeException ex) {
            return CdpCapture.unavailable("parse_failed:" + ex.getClass().getSimpleName());
        }
    }

    static List<NormalizedDomNode> parseSnapshot(JsonNode snapshot, String url) {
        if (snapshot == null || snapshot.isMissingNode() || snapshot.isNull()) {
            return List.of();
        }
        JsonNode stringsNode = snapshot.path("strings");
        List<String> strings = new ArrayList<>();
        if (stringsNode.isArray()) {
            for (JsonNode s : stringsNode) {
                strings.add(s.asText(""));
            }
        }
        JsonNode documents = snapshot.path("documents");
        if (!documents.isArray() || documents.isEmpty()) {
            return List.of();
        }
        JsonNode doc = documents.get(0);
        String frameUrl = firstNonBlank(doc.path("documentURL").asText(""), url);
        JsonNode nodes = doc.path("nodes");
        JsonNode parentIndex = nodes.path("parentIndex");
        JsonNode nodeType = nodes.path("nodeType");
        JsonNode nodeName = nodes.path("nodeName");
        JsonNode nodeValue = nodes.path("nodeValue");
        JsonNode backendNodeId = nodes.path("backendNodeId");
        JsonNode attributes = nodes.path("attributes");
        int count = Math.max(parentIndex.size(), nodeName.size());
        Map<Integer, double[]> layout = parseLayout(doc.path("layout"));
        List<List<Integer>> children = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            children.add(new ArrayList<>());
        }
        List<NormalizedDomNode> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int parent = parentIndex.path(i).asInt(-1);
            if (parent >= 0 && parent < children.size()) {
                children.get(parent).add(i);
            }
            Map<String, String> attrs = parseAttributes(attributes.path(i), strings);
            double[] box = layout.getOrDefault(i, new double[] {0, 0, 0, 0});
            out.add(new NormalizedDomNode(
                    i,
                    backendNodeId.path(i).asInt(0),
                    nodeType.path(i).asInt(0),
                    str(strings, nodeName.path(i).asInt(-1)),
                    str(strings, nodeValue.path(i).asInt(-1)),
                    attrs,
                    parent,
                    List.of(),
                    box[0], box[1], box[2], box[3],
                    frameUrl
            ));
        }
        List<NormalizedDomNode> withChildren = new ArrayList<>();
        for (NormalizedDomNode node : out) {
            withChildren.add(new NormalizedDomNode(
                    node.nodeIndex(), node.backendNodeId(), node.nodeType(), node.nodeName(), node.nodeValue(),
                    node.attributes(), node.parentIndex(), List.copyOf(children.get(node.nodeIndex())),
                    node.x(), node.y(), node.width(), node.height(), node.frameUrl()
            ));
        }
        return withChildren;
    }

    static List<AccessibilityNode> parseAccessibility(JsonNode ax) {
        if (ax == null || ax.isNull()) {
            return List.of();
        }
        JsonNode nodes = ax.path("nodes");
        if (!nodes.isArray()) {
            return List.of();
        }
        List<AccessibilityNode> out = new ArrayList<>();
        for (JsonNode n : nodes) {
            List<String> childIds = new ArrayList<>();
            JsonNode kids = n.path("childIds");
            if (kids.isArray()) {
                for (JsonNode c : kids) {
                    childIds.add(c.asText(""));
                }
            }
            out.add(new AccessibilityNode(
                    n.path("nodeId").asText(""),
                    axValue(n.path("role")),
                    axValue(n.path("name")),
                    n.path("backendDOMNodeId").asInt(0),
                    n.path("ignored").asBoolean(false),
                    List.copyOf(childIds),
                    axStates(n.path("properties"))
            ));
        }
        return out;
    }

    private static Map<Integer, double[]> parseLayout(JsonNode layout) {
        Map<Integer, double[]> out = new LinkedHashMap<>();
        JsonNode indexes = layout.path("nodeIndex");
        JsonNode bounds = layout.path("bounds");
        int n = Math.min(indexes.size(), bounds.size());
        for (int i = 0; i < n; i++) {
            JsonNode box = bounds.get(i);
            out.put(indexes.get(i).asInt(-1), new double[] {
                    box.path(0).asDouble(0),
                    box.path(1).asDouble(0),
                    box.path(2).asDouble(0),
                    box.path(3).asDouble(0)
            });
        }
        return out;
    }

    private static Map<String, String> parseAttributes(JsonNode attrIndexes, List<String> strings) {
        Map<String, String> attrs = new LinkedHashMap<>();
        if (attrIndexes == null || !attrIndexes.isArray()) {
            return attrs;
        }
        for (int i = 0; i + 1 < attrIndexes.size(); i += 2) {
            String name = str(strings, attrIndexes.get(i).asInt(-1));
            String value = str(strings, attrIndexes.get(i + 1).asInt(-1));
            if (!name.isBlank()) {
                attrs.put(name, value);
            }
        }
        return attrs;
    }

    private static String str(List<String> strings, int index) {
        if (index < 0 || index >= strings.size()) {
            return "";
        }
        return strings.get(index);
    }

    private static String axStates(JsonNode properties) {
        if (properties == null || !properties.isArray()) {
            return "";
        }
        List<String> states = new ArrayList<>();
        for (JsonNode prop : properties) {
            String name = axValue(prop.path("name")).toLowerCase();
            if (name.isBlank()) {
                continue;
            }
            if (name.contains("checked") || name.contains("disabled") || name.contains("expanded")
                    || name.contains("selected") || name.contains("pressed") || name.contains("readonly")
                    || name.contains("required") || name.contains("invalid") || name.contains("busy")) {
                String val = axValue(prop.path("value"));
                states.add(name + "=" + val);
            }
        }
        return String.join(",", states);
    }

    private static String axValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText("");
        }
        return node.path("value").asText("");
    }

    private static JsonNode toJson(Object raw) {
        if (raw == null) {
            return MAPPER.createObjectNode();
        }
        if (raw instanceof JsonNode node) {
            return node;
        }
        if (raw instanceof String s) {
            try {
                return MAPPER.readTree(s);
            } catch (RuntimeException ex) {
                return MAPPER.createObjectNode();
            }
        }
        return MAPPER.valueToTree(raw);
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b == null ? "" : b);
    }
}
