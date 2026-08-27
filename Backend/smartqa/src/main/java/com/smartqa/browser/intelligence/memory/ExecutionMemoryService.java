package com.smartqa.browser.intelligence.memory;

import com.smartqa.common.config.SmartQaProperties;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Advisory execution memory. Live DOM always wins. Secrets are never stored.
 */
@Service
public class ExecutionMemoryService {

    private final CopyOnWriteArrayList<ExecutionMemoryRecord> records = new CopyOnWriteArrayList<>();
    private final SmartQaProperties properties;

    public ExecutionMemoryService(SmartQaProperties properties) {
        this.properties = properties;
    }

    public void rememberSuccess(
            String pageUrl,
            String testCaseId,
            String executionId,
            String action,
            String semanticTarget,
            String role,
            String parentContext,
            String frameContext,
            String shadowContext,
            String locatorType,
            String locatorHint,
            double confidence) {
        if (looksSensitive(semanticTarget) || looksSensitive(locatorHint)) {
            return;
        }
        records.add(new ExecutionMemoryRecord(
                MemoryScope.APPLICATION,
                hostOf(pageUrl),
                testCaseId,
                executionId,
                action,
                safe(semanticTarget),
                safe(role),
                safe(parentContext),
                safe(frameContext),
                safe(shadowContext),
                safe(locatorType),
                safe(locatorHint),
                confidence,
                true,
                Instant.now()
        ));
        trim();
    }

    public void rememberSuccess(
            String pageUrl,
            String testCaseId,
            String executionId,
            String action,
            String semanticTarget,
            String role,
            String parentContext,
            String frameContext,
            String shadowContext,
            String locatorType,
            String locatorHint,
            double confidence,
            String controlType,
            String container,
            String verifiedLocatorStrategy,
            String evidenceMomentId) {
        if (looksSensitive(semanticTarget) || looksSensitive(locatorHint)) {
            return;
        }
        records.add(new ExecutionMemoryRecord(
                MemoryScope.APPLICATION,
                hostOf(pageUrl),
                testCaseId,
                executionId,
                action,
                safe(semanticTarget),
                safe(role),
                safe(parentContext),
                safe(frameContext),
                safe(shadowContext),
                safe(locatorType),
                safe(locatorHint),
                confidence,
                true,
                Instant.now(),
                safe(controlType),
                safe(container),
                safe(verifiedLocatorStrategy),
                safe(evidenceMomentId)
        ));
        trim();
    }

    public List<ExecutionMemoryRecord> hints(String pageUrl, String action, String semanticTarget) {
        String host = hostOf(pageUrl);
        String needle = safe(semanticTarget).toLowerCase(Locale.ROOT);
        List<ExecutionMemoryRecord> out = new ArrayList<>();
        for (int i = records.size() - 1; i >= 0 && out.size() < 5; i--) {
            ExecutionMemoryRecord rec = records.get(i);
            if (!host.isBlank() && !host.equalsIgnoreCase(rec.applicationHost())) {
                continue;
            }
            if (action != null && rec.action() != null && !action.equalsIgnoreCase(rec.action())) {
                continue;
            }
            if (!needle.isBlank() && rec.semanticTarget() != null
                    && rec.semanticTarget().toLowerCase(Locale.ROOT).contains(needle)) {
                out.add(rec);
            }
        }
        return List.copyOf(out);
    }

    public int size() {
        return records.size();
    }

    private void trim() {
        int max = properties == null ? 200 : Math.max(20, properties.getIntelligence().getMemoryMaxEntries());
        while (records.size() > max) {
            records.remove(0);
        }
    }

    private static String hostOf(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            String host = URI.create(url).getHost();
            return host == null ? "" : host;
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static boolean looksSensitive(String value) {
        if (value == null) {
            return false;
        }
        String v = value.toLowerCase(Locale.ROOT);
        return v.contains("password") || v.contains("token") || v.contains("secret")
                || v.contains("authorization") || v.contains("cookie");
    }

    private static String safe(String v) {
        return v == null ? "" : v.trim();
    }
}
