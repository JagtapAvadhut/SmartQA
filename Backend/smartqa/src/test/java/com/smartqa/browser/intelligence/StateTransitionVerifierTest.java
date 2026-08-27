package com.smartqa.browser.intelligence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateTransitionVerifierTest {

    @Test
    void expectedChangeWhenUrlDiffers() {
        StateSnapshot before = new StateSnapshot("https://a.example/login", "Login", "t1", "d1", 8, 0, false);
        StateSnapshot after = new StateSnapshot("https://a.example/home", "Home", "t2", "d2", 12, 0, false);
        StateTransitionVerifier.Verdict verdict = StateTransitionVerifier.verify(
                before, after, StateTransitionVerifier.Signals.unknown(true));
        assertEquals(StateTransitionVerifier.Classification.EXPECTED_CHANGE, verdict.classification());
        assertTrue(verdict.passAllowed());
    }

    @Test
    void contradictoryWhenSuccessAndErrorBothVisible() {
        StateSnapshot before = new StateSnapshot("https://a.example/x", "A", "t1", "d1", 10, 0, false);
        StateSnapshot after = new StateSnapshot("https://a.example/x", "A", "t2", "d2", 10, 0, false);
        StateTransitionVerifier.Signals signals = new StateTransitionVerifier.Signals(
                true, true, true, false, false);
        StateTransitionVerifier.Verdict verdict = StateTransitionVerifier.verify(before, after, signals);
        assertEquals(StateTransitionVerifier.Classification.CONTRADICTORY_CHANGE, verdict.classification());
        assertFalse(verdict.passAllowed());
    }

    @Test
    void contradictoryWhenLoginFormRemainsAfterLoginClick() {
        StateSnapshot before = new StateSnapshot("https://a.example/login", "Login", "t1", "d1", 6, 0, false);
        StateSnapshot after = new StateSnapshot("https://a.example/login", "Login", "t1", "d1", 6, 0, false);
        StateTransitionVerifier.Signals signals = new StateTransitionVerifier.Signals(
                true, false, false, true, false);
        StateTransitionVerifier.Verdict verdict = StateTransitionVerifier.verify(before, after, signals);
        assertEquals(StateTransitionVerifier.Classification.CONTRADICTORY_CHANGE, verdict.classification());
        assertFalse(verdict.passAllowed());
    }

    @Test
    void expectedNoChangeForVerifyAction() {
        StateSnapshot snap = new StateSnapshot("https://a.example", "Home", "abc", "dom", 12, 0, false);
        StateTransitionVerifier.Verdict verdict = StateTransitionVerifier.verify(
                snap, snap, StateTransitionVerifier.Signals.unknown(false));
        assertEquals(StateTransitionVerifier.Classification.EXPECTED_NO_CHANGE, verdict.classification());
        assertTrue(verdict.passAllowed());
    }

    @Test
    void fillingPasswordOnLoginPageIsNotAuthenticationSubmit() {
        assertFalse(StateTransitionVerifier.isAuthenticationSubmit("input", "Password"));
        assertFalse(StateTransitionVerifier.isAuthenticationSubmit("input", "Username"));
        assertFalse(StateTransitionVerifier.isAuthenticationSubmit("click", "Search"));
        assertFalse(StateTransitionVerifier.isAuthenticationSubmit("click", "Save"));
        assertTrue(StateTransitionVerifier.isAuthenticationSubmit("click", "Login"));
        assertTrue(StateTransitionVerifier.isAuthenticationSubmit("click", "Sign in"));
    }

    @Test
    void validationErrorAfterLoginClickIsExpectedStayOnForm() {
        StateSnapshot before = new StateSnapshot("https://a.example/login", "Login", "t1", "d1", 6, 0, false);
        StateSnapshot after = new StateSnapshot("https://a.example/login", "Login", "t1", "d1", 6, 0, false);
        StateTransitionVerifier.Signals signals = new StateTransitionVerifier.Signals(
                true, false, true, true, false);
        StateTransitionVerifier.Verdict verdict = StateTransitionVerifier.verify(before, after, signals);
        assertEquals(StateTransitionVerifier.Classification.EXPECTED_NO_CHANGE, verdict.classification());
        assertTrue(verdict.passAllowed());
    }

    @Test
    void typingIntoLoginFormWithPasswordFieldStillVisibleDoesNotContradict() {
        StateSnapshot before = new StateSnapshot("https://a.example/login", "Login", "t1", "d1", 6, 0, false);
        StateSnapshot after = new StateSnapshot("https://a.example/login", "Login", "t2", "d1", 6, 0, false);
        StateTransitionVerifier.Signals signals = new StateTransitionVerifier.Signals(
                true, false, false, false, false);
        StateTransitionVerifier.Verdict verdict = StateTransitionVerifier.verify(before, after, signals);
        assertTrue(verdict.passAllowed());
    }

    @Test
    void intendedStateIsMeaningfulOutcome() {
        StateSnapshot snap = new StateSnapshot("https://a.example", "Home", "abc", "dom", 12, 0, false);
        StateTransitionVerifier.Signals signals = new StateTransitionVerifier.Signals(
                true, false, false, false, true);
        StateTransitionVerifier.Verdict verdict = StateTransitionVerifier.verify(snap, snap, signals);
        assertTrue(StateTransitionVerifier.meaningfulOutcomeObserved(verdict, signals));
        StateTransitionVerifier.Verdict contradict = StateTransitionVerifier.verify(
                snap, snap, new StateTransitionVerifier.Signals(true, true, true, false, false));
        assertFalse(StateTransitionVerifier.meaningfulOutcomeObserved(contradict, null));
    }

    @Test
    void withIntendedStateMarksWidgetProof() {
        StateTransitionVerifier.Signals base = StateTransitionVerifier.Signals.unknown(true);
        assertFalse(base.intendedStatePresent());
        StateTransitionVerifier.Signals marked = StateTransitionVerifier.withIntendedState(base, true);
        assertTrue(marked.intendedStatePresent());
        assertTrue(marked.expectChange());
    }
}
