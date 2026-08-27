package com.smartqa.browser.intelligence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceConsensusEngineTest {

    @Test
    void rejectsAiWhenDomOwnershipContradicts() {
        EvidenceConsensusEngine.Scores scores = new EvidenceConsensusEngine.Scores(
                0.9, 0.8, 0.9, 0.8, 0.2, 0.1, 0.95);
        EvidenceConsensusEngine.Decision decision = EvidenceConsensusEngine.decide(scores, true);
        assertFalse(decision.aiAccepted());
        assertTrue(decision.reason().toLowerCase().contains("contradict"));
    }

    @Test
    void acceptsAiWhenNoDomContradiction() {
        EvidenceConsensusEngine.Scores scores = new EvidenceConsensusEngine.Scores(
                0.5, 0.5, 0.4, 0.6, 0.5, 0.3, 0.8);
        EvidenceConsensusEngine.Decision decision = EvidenceConsensusEngine.decide(scores, false);
        assertTrue(decision.aiAccepted());
    }
}
