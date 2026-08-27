package com.smartqa.testcase;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestCaseServiceParseTest {

    @Test
    void parseStepsStripsNumberingAndBlanks() {
        List<String> steps = TestCaseService.parseSteps("""
                Open https://example.com
                
                2. Click More information
                Verify the heading
                """);
        assertEquals(List.of(
                "Open https://example.com",
                "Click More information",
                "Verify the heading"
        ), steps);
    }
}
