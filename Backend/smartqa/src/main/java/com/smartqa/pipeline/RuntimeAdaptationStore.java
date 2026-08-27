package com.smartqa.pipeline;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Versioned runtime adaptation (strategy ordering, thresholds, retries, recovery policy).
 * Not a Java source modification.
 */
@Component
public class RuntimeAdaptationStore {

    private final ConcurrentHashMap<String, Object> values = new ConcurrentHashMap<>();
    private volatile int version = 1;
    private volatile Instant updatedAt = Instant.now();

    public RuntimeAdaptationStore() {
        values.put("ai.recovery.confidenceThreshold", RecoveryPlanValidator.DEFAULT_CONFIDENCE_THRESHOLD);
        values.put("pipeline.maxAttempts", 3);
        values.put("recovery.retryCount", 3);
        values.put("search.avoidExportHostRedirect", true);
        values.put("search.restoreExpectedHost", true);
        values.put("assertion.doNotWeaken", true);
        values.put("filter.expandClosedPanels", true);
        values.put("ai.promptVersion", "failure-diagnostic-v1");
        values.put("ai.primaryProvider", "gemini");
        values.put("ai.fallbackProvider", "ollama");
    }

    public synchronized void put(String key, Object value) {
        if (key == null || value == null) {
            return;
        }
        values.put(key, value);
        version++;
        updatedAt = Instant.now();
    }

    public synchronized void putAll(Map<String, Object> updates) {
        if (updates == null || updates.isEmpty()) {
            return;
        }
        values.putAll(updates);
        version++;
        updatedAt = Instant.now();
    }

    public Object get(String key) {
        return values.get(key);
    }

    public double confidenceThreshold() {
        Object v = values.get("ai.recovery.confidenceThreshold");
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        return RecoveryPlanValidator.DEFAULT_CONFIDENCE_THRESHOLD;
    }

    public boolean flag(String key, boolean defaultValue) {
        Object v = values.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        return defaultValue;
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> snap = new LinkedHashMap<>(values);
        snap.put("_version", version);
        snap.put("_updatedAt", updatedAt.toString());
        return snap;
    }

    public int version() {
        return version;
    }

    /**
     * Apply safe recovery hints into runtime adaptation without requiring a rebuild.
     */
    public void applyRecoveryHints(Map<String, Object> hints) {
        if (hints == null || hints.isEmpty()) {
            return;
        }
        Map<String, Object> updates = new LinkedHashMap<>();
        if (Boolean.TRUE.equals(hints.get("restoreExpectedHost"))
                || Boolean.TRUE.equals(hints.get("preferDomesticHost"))
                || Boolean.TRUE.equals(hints.get("avoidExportHostRedirect"))) {
            updates.put("search.restoreExpectedHost", true);
            updates.put("search.avoidExportHostRedirect", true);
        }
        if (Boolean.TRUE.equals(hints.get("reapplyFilter"))) {
            updates.put("filter.expandClosedPanels", true);
            updates.put("filter.reapplyOnRetry", true);
        }
        if (Boolean.TRUE.equals(hints.get("dismissOverlayFirst"))) {
            updates.put("recovery.dismissOverlayFirst", true);
        }
        if (Boolean.TRUE.equals(hints.get("extendStateWait"))) {
            updates.put("wait.stateMs", 800);
        }
        if (Boolean.TRUE.equals(hints.get("doNotWeakenAssertion"))) {
            updates.put("assertion.doNotWeaken", true);
        }
        if (!updates.isEmpty()) {
            putAll(updates);
        }
    }
}
