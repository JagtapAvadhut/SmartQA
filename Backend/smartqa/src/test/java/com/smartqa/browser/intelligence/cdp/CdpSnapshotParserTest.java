package com.smartqa.browser.intelligence.cdp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CdpSnapshotParserTest {

    @Test
    void parsesOfficialDomSnapshotShape() {
        String snapshot = """
                {
                  "documents": [{
                    "documentURL": "https://example.test/",
                    "nodes": {
                      "parentIndex": [-1, 0, 1],
                      "nodeType": [9, 1, 1],
                      "nodeName": [0, 1, 2],
                      "nodeValue": [-1, -1, 5],
                      "backendNodeId": [1, 2, 3],
                      "attributes": [[], [], [3, 4]]
                    },
                    "layout": {
                      "nodeIndex": [2],
                      "bounds": [[12, 40, 80, 18]]
                    }
                  }],
                  "strings": ["#document", "BODY", "LABEL", "id", "brand-ak", "AK"]
                }
                """;
        String ax = """
                {
                  "nodes": [{
                    "nodeId": "1",
                    "role": {"value": "checkbox"},
                    "name": {"value": "AK"},
                    "backendDOMNodeId": 3,
                    "ignored": false,
                    "childIds": []
                  }]
                }
                """;
        CdpCapture capture = CdpSnapshotParser.parseJson(snapshot, ax, "https://example.test/", "Example");
        assertTrue(capture.captured());
        assertEquals(3, capture.nodeCount());
        NormalizedDomNode label = capture.graph().nodes().get(2);
        assertEquals("LABEL", label.nodeName());
        assertEquals("brand-ak", label.id());
        assertEquals(12, label.x(), 0.01);
        List<NormalizedDomNode> children = capture.graph().children(capture.graph().node(1));
        assertEquals(1, children.size());
        assertEquals("checkbox", capture.accessibility().getFirst().role());
        assertTrue(capture.compactAccessibility(5).contains("AK"));
        assertFalse(capture.graph().findByText("AK", 3).isEmpty());
    }
}
