package com.smartqa.browser.intelligence.recovery;

/**
 * AI or planner may recommend recovery. Playwright is the only executor.
 */
public record RecoveryDecision(
        boolean shouldRecover,
        String reason,
        int targetStateStep,
        int currentStep,
        String recoveryAction,
        double confidence
) {
    public static final String NONE = "NONE";
    public static final String REFRESH = "REFRESH";
    public static final String BACK = "BACK";
    public static final String REOPEN_TAB = "REOPEN_TAB";
    public static final String CLOSE_POPUP = "CLOSE_POPUP";
    public static final String REOPEN_MENU = "REOPEN_MENU";
    public static final String REINSPECT = "REINSPECT";
    public static final String RETRY_ACTION = "RETRY_ACTION";
    public static final String RESELECT_TARGET = "RESELECT_TARGET";
    public static final String REGENERATE_LOCATOR = "REGENERATE_LOCATOR";
    public static final String ESCALATE_AI = "ESCALATE_AI";

    public static RecoveryDecision none(String reason) {
        return new RecoveryDecision(false, reason, -1, -1, NONE, 0);
    }

    public boolean isBack() {
        return shouldRecover && BACK.equalsIgnoreCase(recoveryAction);
    }
}
