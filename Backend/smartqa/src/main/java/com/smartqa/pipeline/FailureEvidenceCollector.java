package com.smartqa.pipeline;

import com.microsoft.playwright.Page;
import com.smartqa.debug.TraceContext;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds compact {@link FailureEvidence} from pipeline / browser failure sites.
 * Prefer live page when available; otherwise synthesize from message + context.
 */
@Component
public class FailureEvidenceCollector {

    public FailureEvidence fromPipelineFailure(
            PipelineRun run,
            String stage,
            String message,
            String category,
            String expected,
            String actual,
            Integer stepNumber,
            String action,
            String target,
            String screenshotPath,
            List<String> previousAttempts) {
        String url = run.applicationUrl();
        String website = hostOf(url);
        return FailureEvidence.builder()
                .traceId(TraceContext.current())
                .pipelineId(run.id() == null ? null : run.id().toString())
                .runId(run.executionRunId() == null ? null : run.executionRunId().toString())
                .testCaseId(run.testCaseId() == null ? null : run.testCaseId().toString())
                .website(website)
                .url(url)
                .pageTitle(null)
                .stepNumber(stepNumber)
                .instruction(null)
                .action(action)
                .target(target)
                .location(stage)
                .expected(expected)
                .actual(actual == null ? message : actual)
                .failureCategory(category)
                .exception(message)
                .screenshotPath(screenshotPath)
                .previousAttempts(previousAttempts == null ? List.of() : previousAttempts)
                .timestamp(Instant.now())
                .extras(Map.of("stage", stage == null ? "" : stage, "attempt", run.attempt()))
                .build();
    }

    public FailureEvidence fromPage(
            Page page,
            PipelineRun run,
            String stage,
            String message,
            String category,
            String expected,
            String actual,
            Integer stepNumber,
            String action,
            String target,
            String locator,
            String locatorType,
            Double confidence,
            List<String> candidates,
            List<Double> scores,
            String screenshotPath,
            List<String> previousAttempts) {
        String url = safeUrl(page);
        String title = safeTitle(page);
        String visible = safeVisibleText(page, 1500);
        String dom = safeDomExcerpt(page, 2500);
        return FailureEvidence.builder()
                .traceId(TraceContext.current())
                .pipelineId(run == null || run.id() == null ? null : run.id().toString())
                .runId(run == null || run.executionRunId() == null ? null : run.executionRunId().toString())
                .testCaseId(run == null || run.testCaseId() == null ? null : run.testCaseId().toString())
                .website(hostOf(url))
                .url(url)
                .pageTitle(title)
                .stepNumber(stepNumber)
                .action(action)
                .target(target)
                .location(stage)
                .expected(expected)
                .actual(actual)
                .failureCategory(category)
                .exception(message)
                .locator(locator)
                .locatorType(locatorType)
                .confidence(confidence)
                .candidateLocators(candidates)
                .candidateScores(scores)
                .domExcerpt(dom)
                .visibleTextExcerpt(visible)
                .accessibilityExcerpt(safeAriaExcerpt(page, 1000))
                .overlayEvidence(detectOverlayHint(page))
                .screenshotPath(screenshotPath)
                .previousAttempts(previousAttempts == null ? List.of() : previousAttempts)
                .timestamp(Instant.now())
                .build();
    }

    public FailureEvidence enrichWithHostMismatch(FailureEvidence evidence, String expectedApplicationUrl) {
        if (evidence == null) {
            return null;
        }
        String expectedHost = hostOf(expectedApplicationUrl);
        String actualHost = hostOf(evidence.url());
        if (expectedHost.isBlank() || actualHost.isBlank() || expectedHost.equalsIgnoreCase(actualHost)) {
            return evidence;
        }
        // Same registrable family still flagged when subdomain diverges (export. vs www.)
        if (sameRegistrableDomain(expectedHost, actualHost) && !expectedHost.equalsIgnoreCase(actualHost)) {
            List<String> attempts = new ArrayList<>(evidence.previousAttempts() == null ? List.of() : evidence.previousAttempts());
            attempts.add("host_mismatch:" + expectedHost + "->" + actualHost);
            return FailureEvidence.builder()
                    .traceId(evidence.traceId())
                    .pipelineId(evidence.pipelineId())
                    .runId(evidence.runId())
                    .testCaseId(evidence.testCaseId())
                    .website(evidence.website())
                    .url(evidence.url())
                    .pageTitle(evidence.pageTitle())
                    .stepNumber(evidence.stepNumber())
                    .instruction(evidence.instruction())
                    .action(evidence.action())
                    .target(evidence.target())
                    .location(evidence.location())
                    .expected(evidence.expected())
                    .actual("host=" + actualHost + "; expectedHost=" + expectedHost
                            + (evidence.actual() == null || evidence.actual().isBlank() ? "" : "; " + evidence.actual()))
                    .failureCategory(firstNonBlank(evidence.failureCategory(), "WRONG_HOST"))
                    .exception(evidence.exception())
                    .locator(evidence.locator())
                    .locatorType(evidence.locatorType())
                    .confidence(evidence.confidence())
                    .candidateLocators(evidence.candidateLocators())
                    .candidateScores(evidence.candidateScores())
                    .domExcerpt(evidence.domExcerpt())
                    .accessibilityExcerpt(evidence.accessibilityExcerpt())
                    .visibleTextExcerpt(evidence.visibleTextExcerpt())
                    .frameContext(evidence.frameContext())
                    .shadowContext(evidence.shadowContext())
                    .overlayEvidence(evidence.overlayEvidence())
                    .beforeState(evidence.beforeState())
                    .afterState(evidence.afterState())
                    .previousStep(evidence.previousStep())
                    .nextStep(evidence.nextStep())
                    .screenshotPath(evidence.screenshotPath())
                    .previousAttempts(attempts)
                    .timestamp(evidence.timestamp())
                    .durationMs(evidence.durationMs())
                    .extras(evidence.extras())
                    .build();
        }
        return evidence;
    }

    public static String hostOf(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (Exception ex) {
            return "";
        }
    }

    public static boolean sameRegistrableDomain(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
            return false;
        }
        String ra = registrable(a);
        String rb = registrable(b);
        return !ra.isBlank() && ra.equalsIgnoreCase(rb);
    }

    private static String registrable(String host) {
        String[] parts = host.toLowerCase(Locale.ROOT).split("\\.");
        if (parts.length < 2) {
            return host;
        }
        return parts[parts.length - 2] + "." + parts[parts.length - 1];
    }

    private static String safeUrl(Page page) {
        try {
            return page == null ? "" : page.url();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static String safeTitle(Page page) {
        try {
            return page == null ? "" : page.title();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static String safeVisibleText(Page page, int max) {
        if (page == null) {
            return "";
        }
        try {
            Object text = page.evaluate("() => (document.body && document.body.innerText) ? document.body.innerText.slice(0, "
                    + max + ") : ''");
            return text == null ? "" : String.valueOf(text);
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static String safeDomExcerpt(Page page, int max) {
        if (page == null) {
            return "";
        }
        try {
            Object html = page.evaluate("""
                    () => {
                      const nodes = Array.from(document.querySelectorAll(
                        'h1,h2,h3,[role="heading"],[role="alert"],form,input,button,a,[aria-invalid="true"],[class*="error" i],[class*="invalid" i]'
                      )).slice(0, 40);
                      return nodes.map(n => n.outerHTML.slice(0, 180)).join('\\n');
                    }
                    """);
            String value = html == null ? "" : String.valueOf(html);
            return value.length() <= max ? value : value.substring(0, max);
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static String safeAriaExcerpt(Page page, int max) {
        if (page == null) {
            return "";
        }
        try {
            Object aria = page.evaluate("""
                    () => Array.from(document.querySelectorAll('[aria-label],[role],[aria-invalid]'))
                      .slice(0, 30)
                      .map(n => (n.getAttribute('role')||'') + '|' + (n.getAttribute('aria-label')||'') + '|' + (n.getAttribute('aria-invalid')||''))
                      .join('\\n')
                    """);
            String value = aria == null ? "" : String.valueOf(aria);
            return value.length() <= max ? value : value.substring(0, max);
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static String detectOverlayHint(Page page) {
        if (page == null) {
            return "";
        }
        try {
            Object hint = page.evaluate("""
                    () => {
                      const dialogs = document.querySelectorAll('[role="dialog"], .modal, .overlay, [class*="cookie"]');
                      return dialogs.length > 0 ? ('overlayCandidates=' + dialogs.length) : '';
                    }
                    """);
            return hint == null ? "" : String.valueOf(hint);
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }
}
