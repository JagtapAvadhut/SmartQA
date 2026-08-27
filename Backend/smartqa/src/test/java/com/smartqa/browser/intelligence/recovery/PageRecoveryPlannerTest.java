package com.smartqa.browser.intelligence.recovery;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageRecoveryPlannerTest {

    @Test
    void recommendsBackWhenPreviousStateHasTarget() {
        BrowserStateHistory history = new BrowserStateHistory(3);
        history.record(new BrowserStateRecord(
                1, "https://shop.example/fashion", "Fashion", Instant.now(), "shot-1",
                "Korean Store Seoul Streetwear", "click", "Fashion", true, ""));
        history.record(new BrowserStateRecord(
                2, "https://shop.example/listing", "Listing", Instant.now(), "shot-2",
                "product cards filters brand", "click", "Seoul Streetwear", true, ""));
        RecoveryDecision decision = PageRecoveryPlanner.recommend(
                history, "Korean Store", "https://shop.example/listing", "product cards filters brand", 2);
        assertTrue(decision.shouldRecover());
        assertTrue(decision.isBack());
        assertEquals(1, decision.targetStateStep());
    }

    @Test
    void historicalScreenshotDoesNotAuthorizeExecutionByItself() {
        BrowserStateHistory history = new BrowserStateHistory(3);
        history.record(new BrowserStateRecord(
                1, "https://shop.example/fashion", "Fashion", Instant.now(), "old-shot",
                "Korean Store", "click", "Fashion", true, ""));
        RecoveryDecision decision = PageRecoveryPlanner.recommend(
                history, "Korean Store", "https://shop.example/listing", "Korean Store still here", 2);
        assertFalse(decision.shouldRecover());
        assertEquals(RecoveryDecision.NONE, decision.recoveryAction());
    }

    @Test
    void respectsBacktrackLimit() {
        BrowserStateHistory history = new BrowserStateHistory(0);
        history.record(new BrowserStateRecord(
                1, "https://shop.example/a", "A", Instant.now(), "", "Korean Store", "click", "x", true, ""));
        history.record(new BrowserStateRecord(
                2, "https://shop.example/b", "B", Instant.now(), "", "listing only", "click", "y", true, ""));
        RecoveryDecision decision = PageRecoveryPlanner.recommend(
                history, "Korean Store", "https://shop.example/b", "listing only", 2);
        assertFalse(decision.shouldRecover());
    }
}
