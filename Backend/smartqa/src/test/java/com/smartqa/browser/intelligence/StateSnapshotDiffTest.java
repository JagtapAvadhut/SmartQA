package com.smartqa.browser.intelligence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateSnapshotDiffTest {

    @Test
    void emitsMeaningfulChangeWhenUrlDiffers() {
        StateSnapshot before = new StateSnapshot("https://a.example/x", "A", "t1", "d1", 10, 0, false);
        StateSnapshot after = new StateSnapshot("https://a.example/y", "A", "t1", "d1", 10, 0, false);
        StateSnapshot.Diff diff = StateSnapshot.diff(before, after);
        assertTrue(diff.urlChanged());
        assertTrue(diff.meaningfullyChanged());
        assertTrue(before.meaningfullyDifferent(after));
    }

    @Test
    void noChangeWhenFingerprintsMatch() {
        StateSnapshot before = new StateSnapshot("https://a.example", "Home", "abc", "dom", 12, 1, true);
        StateSnapshot after = new StateSnapshot("https://a.example", "Home", "abc", "dom", 12, 1, true);
        StateSnapshot.Diff diff = StateSnapshot.diff(before, after);
        assertFalse(diff.meaningfullyChanged());
        assertFalse(before.meaningfullyDifferent(after));
    }

    @Test
    void loadingClearCountsAsChange() {
        StateSnapshot before = new StateSnapshot("https://a.example", "Home", "abc", "dom", 12, 0, true);
        StateSnapshot after = new StateSnapshot("https://a.example", "Home", "abc", "dom", 12, 0, false);
        assertTrue(StateSnapshot.diff(before, after).loadingCleared());
        assertTrue(before.meaningfullyDifferent(after));
    }
}
