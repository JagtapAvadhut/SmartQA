package com.smartqa.debug;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineTimerTest {

    @Test
    void measureReturnsSupplierResult() {
        int value = PipelineTimer.time("TEST_COMPONENT", "step-1", () -> 7);
        assertEquals(7, value);
        assertTrue(true);
    }
}
