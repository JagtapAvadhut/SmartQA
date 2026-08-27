package com.smartqa.browser.intelligence.recovery;

import com.smartqa.debug.SecretMasker;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bounded previous-state stack for wrong-page recovery. Live DOM always wins for execution.
 */
public final class BrowserStateHistory {

    private final ArrayDeque<BrowserStateRecord> states = new ArrayDeque<>();
    private final int maxBacktrackSteps;

    public BrowserStateHistory(int maxBacktrackSteps) {
        this.maxBacktrackSteps = Math.max(0, maxBacktrackSteps);
    }

    public void record(BrowserStateRecord state) {
        if (state == null) {
            return;
        }
        String compact = SecretMasker.maskText(state.compactDom());
        String target = SecretMasker.maskText(state.target());
        if (looksSecret(compact) || looksSecret(target)) {
            compact = "";
            target = "";
        }
        states.addLast(new BrowserStateRecord(
                state.stepNumber(),
                state.url(),
                state.title(),
                state.timestamp() == null ? Instant.now() : state.timestamp(),
                state.screenshotRef(),
                compact == null ? "" : compact,
                state.action(),
                target,
                state.actionSucceeded(),
                state.assertionState()
        ));
        while (states.size() > Math.max(8, maxBacktrackSteps + 2)) {
            states.removeFirst();
        }
    }

    public List<BrowserStateRecord> recent() {
        return List.copyOf(states);
    }

    public BrowserStateRecord previous() {
        if (states.size() < 2) {
            return null;
        }
        List<BrowserStateRecord> list = new ArrayList<>(states);
        return list.get(list.size() - 2);
    }

    public BrowserStateRecord current() {
        return states.peekLast();
    }

    public int maxBacktrackSteps() {
        return maxBacktrackSteps;
    }

    public int size() {
        return states.size();
    }

    public void clear() {
        states.clear();
    }

    static boolean looksSecret(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String v = value.toLowerCase(Locale.ROOT);
        return v.contains("password") || v.contains("token") || v.contains("secret")
                || v.contains("authorization") || v.contains("cookie") || v.contains("otp");
    }
}
