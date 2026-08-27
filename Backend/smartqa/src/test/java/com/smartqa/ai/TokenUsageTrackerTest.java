package com.smartqa.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenUsageTrackerTest {

    @Test
    void accumulatesPromptAndOutputTokens() {
        TokenUsageTracker.reset();
        TokenUsageTracker.record(10, 4);
        TokenUsageTracker.record(3, 2);
        TokenUsageTracker.Usage usage = TokenUsageTracker.snapshot();
        assertEquals(13, usage.promptTokens());
        assertEquals(6, usage.outputTokens());
        assertEquals(19, usage.totalTokens());
        assertEquals(2, usage.calls());
        TokenUsageTracker.clear();
    }
}
