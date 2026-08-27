package com.smartqa.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagRetrievalServiceUnitTest {

    @Test
    void applicationKeyStripsWww() {
        assertEquals("urbancompany.com",
                RagRetrievalService.applicationKeyFrom("https://www.urbancompany.com/pune"));
        assertEquals("flipkart.com",
                RagRetrievalService.applicationKeyFrom("https://www.flipkart.com/search?q=laptop"));
    }

    @Test
    void failureQueryIncludesCategoryAndTarget() {
        String q = RagRetrievalService.buildFailureQuery(
                com.smartqa.pipeline.FailureEvidence.builder()
                        .failureCategory("FILTER_NOT_OPEN")
                        .action("click")
                        .target("Brand HP")
                        .instruction("Apply brand filter")
                        .build());
        assertTrue(q.contains("FILTER_NOT_OPEN"));
        assertTrue(q.contains("Brand HP"));
    }
}
