package com.smartqa.browser.intelligence;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepeatedInstanceDetectorTest {

    @Test
    void groupsRepeatedButtonsByStructureAndReadsOrdinal() {
        ElementCandidate first = ElementCandidate.fromMap(Map.of(
                "tag", "button",
                "role", "button",
                "accessibleName", "Add to cart",
                "text", "Add to cart",
                "visible", true,
                "enabled", true,
                "parentTag", "article"
        ), 0);
        ElementCandidate second = ElementCandidate.fromMap(Map.of(
                "tag", "button",
                "role", "button",
                "accessibleName", "Add to cart",
                "text", "Add to cart",
                "visible", true,
                "enabled", true,
                "parentTag", "article"
        ), 1);
        assertTrue(RepeatedInstanceDetector.repeated(List.of(first, second)));
        assertEquals(1, RepeatedInstanceDetector.requestedIndex("click the first product cart"));
        assertEquals(4, RepeatedInstanceDetector.requestedIndex("checkbox in row 4"));
    }
}
