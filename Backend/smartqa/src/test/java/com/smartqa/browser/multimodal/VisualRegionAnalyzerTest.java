package com.smartqa.browser.multimodal;

import com.smartqa.browser.intelligence.ElementCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VisualRegionAnalyzerTest {

    @Test
    void mapsTopLeftAndHeader() {
        ElementCandidate el = ElementCandidate.fromMap(Map.of(
                "candidateId", "el-1",
                "tag", "div",
                "boundingBox", "10,10,40,20",
                "inHeaderRegion", true,
                "region", "HEADER"
        ), 1);
        var assignments = VisualRegionAnalyzer.assign(List.of(el), 1200, 800);
        assertEquals(1, assignments.size());
        assertEquals(VisualRegionAnalyzer.GridRegion.TOP_LEFT, assignments.getFirst().grid());
        assertEquals("HEADER", assignments.getFirst().semanticRegion());
    }
}
