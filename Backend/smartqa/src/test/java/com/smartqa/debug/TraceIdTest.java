package com.smartqa.debug;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceIdTest {

    @Test
    void generatesSmartQaTraceId() {
        String id = TraceId.newId();
        assertTrue(id.startsWith("SMARTQA-"));
        assertTrue(id.matches("SMARTQA-\\d{8}-\\d{6}-[0-9a-f]{6}"));
    }

    @Test
    void sanitizesIncomingHeader() {
        String id = TraceId.sanitize("SMARTQA-20260818-233012-a8f31c");
        assertTrue(id.startsWith("SMARTQA-"));
    }
}
