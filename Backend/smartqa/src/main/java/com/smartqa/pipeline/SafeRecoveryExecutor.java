package com.smartqa.pipeline;

import com.microsoft.playwright.Page;
import com.smartqa.browser.BlockingOverlayGuard;
import com.smartqa.browser.RecoveryIdempotency;
import com.smartqa.browser.SafeClick;
import com.smartqa.browser.intelligence.PageStateWatcher;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Executes only validated AI recovery recommendations via the deterministic engine.
 * Never runs arbitrary JS/CSS or off-domain navigation.
 */
@Component
public class SafeRecoveryExecutor {

    public record RecoveryOutcome(
            boolean attempted,
            boolean recovered,
            List<String> actionsTaken,
            Map<String, Object> hintsForNextAttempt,
            String summary
    ) {
    }

    /**
     * Live-page recovery when a Playwright page is still available.
     */
    public RecoveryOutcome executeLive(
            Page page,
            List<RecoveryPlanValidator.ValidationResult> validated,
            String expectedApplicationUrl,
            FailureEvidence evidence) {
        List<String> taken = new ArrayList<>();
        Map<String, Object> hints = new LinkedHashMap<>();
        if (page == null || validated == null || validated.isEmpty()) {
            return new RecoveryOutcome(false, false, taken, hints, "No live recovery available");
        }
        boolean recovered = false;
        for (RecoveryPlanValidator.ValidationResult result : validated) {
            if (!result.accepted() || result.option() == null) {
                continue;
            }
            String type = result.option().type().trim().toUpperCase(Locale.ROOT);
            if (RecoveryIdempotency.alreadySatisfied(page, type, evidence)) {
                taken.add(type + ":already-satisfied");
                recovered = true;
                TraceLogger.info("RECOVERY", "RECOVERY_IDEMPOTENT_SKIP", "Intended state already present", TraceMeta.of(
                        "type", type
                ));
                continue;
            }
            TraceLogger.info("RECOVERY", "AI_RECOVERY_STARTED", "Executing validated AI recovery", TraceMeta.of(
                    "type", type,
                    "reason", truncate(result.option().reason(), 160)
            ));
            try {
                boolean ok = switch (type) {
                    case "CLOSE_OVERLAY" -> BlockingOverlayGuard.dismissIfBlocking(page);
                    case "WAIT_FOR_STATE", "REFRESH_DOM" -> {
                        SafeClick.settle(page);
                        PageStateWatcher.waitForSubtreeSettle(page, "body", 150, 800);
                        yield true;
                    }
                    case "RESTORE_EXPECTED_HOST", "RE_NAVIGATE" -> restoreHost(page, expectedApplicationUrl, hints);
                    case "RESELECT_AUTOCOMPLETE", "RESEARCH_SEARCH_RESULT" -> {
                        hints.put("reselectAutocomplete", true);
                        hints.put("researchSearchResult", true);
                        SafeClick.settle(page);
                        yield true;
                    }
                    case "RE_APPLY_FILTER" -> {
                        hints.put("reapplyFilter", true);
                        SafeClick.settle(page);
                        yield true;
                    }
                    case "VERIFY_ASSERTION_CONTEXT" -> {
                        hints.put("verifyAssertionContext", true);
                        yield inspectAssertionContext(page, evidence);
                    }
                    case "REDISCOVER_ELEMENT", "REDISCOVER", "RE_RANK_CANDIDATES", "RE_RESOLVE", "OPEN_CONTROL", "RETRY_STEP" -> {
                        hints.put("rediscover", true);
                        hints.put("rerankCandidates", true);
                        SafeClick.settle(page);
                        yield true;
                    }
                    default -> false;
                };
                taken.add(type + (ok ? ":ok" : ":noop"));
                recovered = recovered || ok;
            } catch (RuntimeException ex) {
                taken.add(type + ":error");
                TraceLogger.warn("RECOVERY", "AI_RECOVERY_FAILED", "Recovery action failed", TraceMeta.of(
                        "type", type,
                        "message", truncate(ex.getMessage(), 160)
                ));
            }
        }
        return new RecoveryOutcome(!taken.isEmpty(), recovered, taken, hints,
                recovered ? "AI recovery applied" : "AI recovery did not restore state");
    }

    /**
     * Pipeline-level recovery: produce strategy hints for the next generate→validate→execute attempt.
     */
    public RecoveryOutcome executePipelineHints(
            List<RecoveryPlanValidator.ValidationResult> validated,
            String expectedApplicationUrl,
            AiDiagnosticResult diagnosis) {
        List<String> taken = new ArrayList<>();
        Map<String, Object> hints = new LinkedHashMap<>();
        if (validated == null || validated.isEmpty()) {
            return new RecoveryOutcome(false, false, taken, hints, "No validated recovery options");
        }
        for (RecoveryPlanValidator.ValidationResult result : validated) {
            if (!result.accepted() || result.option() == null) {
                continue;
            }
            String type = result.option().type().trim().toUpperCase(Locale.ROOT);
            taken.add(type);
            switch (type) {
                case "RESTORE_EXPECTED_HOST", "RE_NAVIGATE" -> {
                    hints.put("restoreExpectedHost", true);
                    hints.put("expectedApplicationUrl", expectedApplicationUrl);
                    hints.put("preferDomesticHost", true);
                }
                case "RESELECT_AUTOCOMPLETE", "RESEARCH_SEARCH_RESULT" -> {
                    hints.put("reselectAutocomplete", true);
                    hints.put("researchSearchResult", true);
                    hints.put("avoidExportHostRedirect", true);
                }
                case "RE_APPLY_FILTER" -> hints.put("reapplyFilter", true);
                case "CLOSE_OVERLAY" -> hints.put("dismissOverlayFirst", true);
                case "VERIFY_ASSERTION_CONTEXT" -> {
                    hints.put("verifyAssertionContext", true);
                    hints.put("doNotWeakenAssertion", true);
                }
                case "REDISCOVER_ELEMENT", "REDISCOVER", "RE_RANK_CANDIDATES", "RE_RESOLVE", "REFRESH_DOM", "RETRY_STEP" -> {
                    hints.put("rediscover", true);
                    hints.put("rerankCandidates", true);
                    hints.put("refreshDom", true);
                }
                case "WAIT_FOR_STATE" -> hints.put("extendStateWait", true);
                case "OPEN_CONTROL" -> hints.put("openControlBeforeAct", true);
                default -> {
                }
            }
        }
        if (diagnosis != null) {
            hints.put("aiClassification", diagnosis.normalizedClassification());
            hints.put("aiRootCause", diagnosis.rootCause());
            if (diagnosis.searchSubCategory() != null) {
                hints.put("searchSubCategory", diagnosis.searchSubCategory());
            }
            if (diagnosis.filterSubCategory() != null) {
                hints.put("filterSubCategory", diagnosis.filterSubCategory());
            }
            if (diagnosis.assertionSubCategory() != null) {
                hints.put("assertionSubCategory", diagnosis.assertionSubCategory());
            }
        }
        boolean attempted = !taken.isEmpty();
        return new RecoveryOutcome(attempted, attempted, taken, hints,
                attempted ? "Recovery hints prepared for retry" : "No recovery hints");
    }

    private boolean restoreHost(Page page, String expectedApplicationUrl, Map<String, Object> hints) {
        if (expectedApplicationUrl == null || expectedApplicationUrl.isBlank()) {
            return false;
        }
        String expectedHost = FailureEvidenceCollector.hostOf(expectedApplicationUrl);
        String currentHost = FailureEvidenceCollector.hostOf(page.url());
        if (expectedHost.isBlank()) {
            return false;
        }
        if (expectedHost.equalsIgnoreCase(currentHost)) {
            hints.put("hostAlreadyExpected", true);
            return true;
        }
        if (!FailureEvidenceCollector.sameRegistrableDomain(expectedHost, currentHost)
                && !currentHost.isBlank()) {
            // Do not jump to unrelated sites
            return false;
        }
        try {
            String target = normalizeBaseUrl(expectedApplicationUrl);
            page.navigate(target);
            page.waitForLoadState();
            SafeClick.settle(page);
            hints.put("restoredHost", FailureEvidenceCollector.hostOf(page.url()));
            return FailureEvidenceCollector.hostOf(page.url()).equalsIgnoreCase(expectedHost)
                    || FailureEvidenceCollector.sameRegistrableDomain(expectedHost, FailureEvidenceCollector.hostOf(page.url()));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private boolean inspectAssertionContext(Page page, FailureEvidence evidence) {
        if (page == null) {
            return false;
        }
        SafeClick.settle(page);
        String expected = evidence == null ? "" : evidence.expected();
        if (expected == null || expected.isBlank()) {
            return true;
        }
        try {
            Object found = page.evaluate("exp => (document.body && document.body.innerText || '').toLowerCase().includes(String(exp).toLowerCase())",
                    expected);
            return Boolean.TRUE.equals(found);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static String normalizeBaseUrl(String url) {
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme() == null ? "https" : uri.getScheme();
            String host = uri.getHost();
            if (host == null) {
                return url;
            }
            int port = uri.getPort();
            return port > 0 ? scheme + "://" + host + ":" + port + "/" : scheme + "://" + host + "/";
        } catch (Exception ex) {
            return url;
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
