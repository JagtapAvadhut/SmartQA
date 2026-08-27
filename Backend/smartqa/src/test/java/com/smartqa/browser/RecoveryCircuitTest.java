package com.smartqa.browser;

import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.SmartQaException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryCircuitTest {

    @Test
    void fourthRetryIsExhausted() {
        RecoveryCircuit circuit = RecoveryCircuit.defaults();
        assertTrue(circuit.tryRetry());
        assertTrue(circuit.tryRetry());
        assertTrue(circuit.tryRetry());
        assertFalse(circuit.tryRetry());
        assertEquals(3, circuit.retryCount());
        SmartQaException ex = circuit.exhaustedException("same approach");
        assertEquals(ErrorCode.RECOVERY_EXHAUSTED, ex.errorCode());
        assertTrue(ex.getMessage().contains("RECOVERY_EXHAUSTED"));
    }

    @Test
    void sameStateRetriesAreBounded() {
        RecoveryCircuit circuit = RecoveryCircuit.defaults();
        assertTrue(circuit.noteSameState());
        assertTrue(circuit.noteSameState());
        assertFalse(circuit.noteSameState());
        assertEquals(3, circuit.sameStateCount());
    }

    @Test
    void replanAndBacktrackAreIndependentCaps() {
        RecoveryCircuit circuit = RecoveryCircuit.defaults();
        assertTrue(circuit.tryReplan());
        assertTrue(circuit.tryReplan());
        assertFalse(circuit.tryReplan());
        assertTrue(circuit.tryBacktrack());
        assertFalse(circuit.tryBacktrack());
        assertEquals(2, circuit.replanCount());
        assertEquals(1, circuit.backtrackCount());
    }
}
