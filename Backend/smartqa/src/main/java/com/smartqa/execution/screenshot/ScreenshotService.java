package com.smartqa.execution.screenshot;

import com.microsoft.playwright.Page;
import com.smartqa.common.config.SmartQaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class ScreenshotService {

    private static final Logger log = LoggerFactory.getLogger(ScreenshotService.class);
    private static final int MAX_SCREENSHOTS_PER_RUN = 100;

    private final SmartQaProperties properties;
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<ScreenshotMeta>> index = new ConcurrentHashMap<>();

    public ScreenshotService(SmartQaProperties properties) {
        this.properties = properties;
    }

    /**
     * Captures a screenshot if the current mode allows it.
     * Returns the screenshot ID or null if skipped.
     */
    public String capture(Page page, String traceId, UUID executionRunId,
                          String stepId, int stepNumber, String eventType, String url) {
        return capture(page, traceId, executionRunId, stepId, stepNumber, eventType, url, null);
    }

    public String capture(Page page, String traceId, UUID executionRunId,
                          String stepId, int stepNumber, String eventType, String url,
                          String evidenceMomentId) {
        String mode = properties.getExecution().getScreenshotMode();
        if ("OFF".equalsIgnoreCase(mode)) {
            return null;
        }
        if ("IMPORTANT".equalsIgnoreCase(mode) && !isImportantEvent(eventType)) {
            return null;
        }
        if (page == null) {
            return null;
        }
        try {
            if (page.isClosed()) {
                return null;
            }
        } catch (RuntimeException ex) {
            return null;
        }
        try {
            String screenshotId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            String runKey = executionRunId != null ? executionRunId.toString() : traceId;
            Path dir = Path.of(properties.getScreenshots().getBaseDir(), runKey);
            Files.createDirectories(dir);
            Path file = dir.resolve(screenshotId + ".png");
            page.screenshot(new Page.ScreenshotOptions().setPath(file).setFullPage(false));

            ScreenshotMeta meta = new ScreenshotMeta(
                    screenshotId, traceId, executionRunId, stepId,
                    stepNumber, eventType, Instant.now(), url, file.toString(), evidenceMomentId
            );
            CopyOnWriteArrayList<ScreenshotMeta> list = index.computeIfAbsent(runKey, k -> new CopyOnWriteArrayList<>());
            list.add(meta);
            if (list.size() > MAX_SCREENSHOTS_PER_RUN) {
                list.remove(0);
            }
            return screenshotId;
        } catch (Exception ex) {
            log.warn("screenshot_capture_failed stepId={} event={}", stepId, eventType, ex);
            return null;
        }
    }

    public List<ScreenshotMeta> list(UUID executionRunId) {
        CopyOnWriteArrayList<ScreenshotMeta> list = index.get(executionRunId.toString());
        if (list != null) {
            return Collections.unmodifiableList(list);
        }
        List<ScreenshotMeta> found = new ArrayList<>();
        for (CopyOnWriteArrayList<ScreenshotMeta> metas : index.values()) {
            for (ScreenshotMeta meta : metas) {
                if (executionRunId.equals(meta.executionRunId())) {
                    found.add(meta);
                }
            }
        }
        return found;
    }

    public ScreenshotMeta find(String screenshotId) {
        for (CopyOnWriteArrayList<ScreenshotMeta> metas : index.values()) {
            for (ScreenshotMeta meta : metas) {
                if (meta.id().equals(screenshotId)) {
                    return meta;
                }
            }
        }
        return null;
    }

    public Path filePath(String screenshotId) {
        ScreenshotMeta meta = find(screenshotId);
        if (meta == null) {
            return null;
        }
        Path path = Path.of(meta.filePath());
        return Files.exists(path) ? path : null;
    }

    public void cleanup(UUID executionRunId) {
        index.remove(executionRunId.toString());
    }

    private boolean isImportantEvent(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return false;
        }
        String normalized = eventType.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if ("FAILED".equals(normalized) || "FAIL".equals(normalized)) {
            normalized = "FAILURE";
        }
        return switch (normalized) {
            case "TEST_STARTED", "PAGE_LOADED",
                 "FAILURE", "TEST_COMPLETED", "ACTION_FAILED", "EXECUTION_COMPLETED",
                 "EXECUTION_FAILED", "VALIDATION_STEP_FAILED", "ASSERTION_PASSED", "ASSERTION_FAILED" -> true;
            default -> false;
        };
    }
}
