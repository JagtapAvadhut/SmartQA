package com.smartqa.browser;

import com.smartqa.common.config.SmartQaProperties;
import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.SmartQaException;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;

/**
 * Bounded recovery counters. Prevents infinite retry / replan / backtrack loops.
 */
public final class RecoveryCircuit {

    private final int maxRetries;
    private final int maxReplans;
    private final int maxBacktracks;
    private final int maxSameStateRetries;

    private int retryCount;
    private int replanCount;
    private int backtrackCount;
    private int sameStateCount;

    public RecoveryCircuit(int maxRetries, int maxReplans, int maxBacktracks, int maxSameStateRetries) {
        this.maxRetries = Math.max(1, maxRetries);
        this.maxReplans = Math.max(0, maxReplans);
        this.maxBacktracks = Math.max(0, maxBacktracks);
        this.maxSameStateRetries = Math.max(1, maxSameStateRetries);
    }

    public static RecoveryCircuit defaults() {
        return new RecoveryCircuit(3, 2, 1, 2);
    }

    public static RecoveryCircuit from(SmartQaProperties.Recovery config) {
        if (config == null) {
            return defaults();
        }
        return new RecoveryCircuit(
                config.getMaxRetries(),
                config.getMaxReplans(),
                config.getMaxBacktracks(),
                config.getMaxSameStateRetries()
        );
    }

    public boolean tryRetry() {
        if (retryCount >= maxRetries) {
            return false;
        }
        retryCount++;
        return true;
    }

    public boolean tryReplan() {
        if (replanCount >= maxReplans) {
            return false;
        }
        replanCount++;
        return true;
    }

    public boolean tryBacktrack() {
        if (backtrackCount >= maxBacktracks) {
            return false;
        }
        backtrackCount++;
        return true;
    }

    public boolean noteSameState() {
        sameStateCount++;
        return sameStateCount <= maxSameStateRetries;
    }

    public boolean exhausted() {
        return retryCount >= maxRetries
                && (replanCount >= maxReplans || maxReplans == 0);
    }

    public SmartQaException exhaustedException(String detail) {
        TraceLogger.warn("RECOVERY", "RECOVERY_EXHAUSTED", "Recovery circuit exhausted", TraceMeta.of(
                "retryCount", retryCount,
                "replanCount", replanCount,
                "backtrackCount", backtrackCount,
                "sameStateCount", sameStateCount,
                "maxRetries", maxRetries,
                "detail", detail == null ? "" : detail
        ));
        return new SmartQaException(ErrorCode.RECOVERY_EXHAUSTED,
                "RECOVERY_EXHAUSTED: " + (detail == null ? "recovery limits reached" : detail));
    }

    public int retryCount() {
        return retryCount;
    }

    public int replanCount() {
        return replanCount;
    }

    public int backtrackCount() {
        return backtrackCount;
    }

    public int sameStateCount() {
        return sameStateCount;
    }

    public int maxRetries() {
        return maxRetries;
    }
}
