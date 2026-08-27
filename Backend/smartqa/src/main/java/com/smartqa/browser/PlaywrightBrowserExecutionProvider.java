package com.smartqa.browser;

import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.smartqa.browser.intelligence.BrowserIntelligenceService;
import com.smartqa.browser.intelligence.BrowserSnapshot;
import com.smartqa.browser.intelligence.ControlClassifier;
import com.smartqa.browser.intelligence.ControlType;
import com.smartqa.browser.intelligence.PageDiagnostics;
import com.smartqa.browser.intelligence.PageReadinessContract;
import com.smartqa.browser.intelligence.PageStateWatcher;
import com.smartqa.browser.intelligence.StateSnapshot;
import com.smartqa.browser.intelligence.StateTransitionVerifier;
import com.smartqa.common.config.SmartQaProperties;
import com.smartqa.common.error.ErrorCode;
import com.smartqa.execution.cancel.ExecutionCancelledException;
import com.smartqa.common.error.SmartQaException;
import com.smartqa.execution.RuntimeExecutionContext;
import com.smartqa.debug.DomTraceStats;
import com.smartqa.debug.SecretMasker;
import com.smartqa.debug.TraceContext;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import com.smartqa.event.ProgressEvent;
import com.smartqa.execution.cancel.CancellationToken;
import com.smartqa.execution.event.EventComponent;
import com.smartqa.execution.event.EventLevel;
import com.smartqa.execution.event.EventType;
import com.smartqa.execution.event.ExecutionEvent;
import com.smartqa.execution.event.ExecutionEventStore;
import com.smartqa.execution.screenshot.ScreenshotService;
import com.smartqa.intent.IntentFilter;
import com.smartqa.intent.LocationHint;
import com.smartqa.intent.SupportedActions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

@Component
public class PlaywrightBrowserExecutionProvider implements BrowserExecutionProvider {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightBrowserExecutionProvider.class);
    private static final String PROVIDER_ID = "PLAYWRIGHT_JAVA";
    private static final ThreadLocal<AtomicReference<Page>> ACTIVE_PAGE = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, LocatorMemoryEntry>> ACTIVE_KNOWN = new ThreadLocal<>();
    private static final ThreadLocal<com.smartqa.browser.intelligence.recovery.BrowserStateHistory> STATE_HISTORY = new ThreadLocal<>();
    private static final ThreadLocal<String> ACTIVE_EVIDENCE_MOMENT = new ThreadLocal<>();

    private final ElementResolver elementResolver;
    private final BrowserIntelligenceService intelligence;
    private final SmartQaProperties properties;
    private final ScreenshotService screenshotService;
    private final ExecutionEventStore eventStore;
    private final FilterEngine filterEngine;
    private final com.smartqa.browser.intelligence.memory.ExecutionMemoryService executionMemory;

    public PlaywrightBrowserExecutionProvider(
            ElementResolver elementResolver,
            BrowserIntelligenceService intelligence,
            SmartQaProperties properties,
            ScreenshotService screenshotService,
            ExecutionEventStore eventStore) {
        this(elementResolver, intelligence, properties, screenshotService, eventStore, null, null);
    }

    public PlaywrightBrowserExecutionProvider(
            ElementResolver elementResolver,
            BrowserIntelligenceService intelligence,
            SmartQaProperties properties,
            ScreenshotService screenshotService,
            ExecutionEventStore eventStore,
            org.springframework.beans.factory.ObjectProvider<com.smartqa.browser.multimodal.MultimodalTargetDiscoveryEngine> multimodal) {
        this(elementResolver, intelligence, properties, screenshotService, eventStore, multimodal, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public PlaywrightBrowserExecutionProvider(
            ElementResolver elementResolver,
            BrowserIntelligenceService intelligence,
            SmartQaProperties properties,
            ScreenshotService screenshotService,
            ExecutionEventStore eventStore,
            org.springframework.beans.factory.ObjectProvider<com.smartqa.browser.multimodal.MultimodalTargetDiscoveryEngine> multimodal,
            org.springframework.beans.factory.ObjectProvider<com.smartqa.browser.intelligence.memory.ExecutionMemoryService> memory) {
        this.elementResolver = elementResolver;
        this.intelligence = intelligence;
        this.properties = properties;
        this.screenshotService = screenshotService;
        this.eventStore = eventStore;
        this.filterEngine = new FilterEngine(
                intelligence,
                elementResolver,
                multimodal == null ? null : multimodal.getIfAvailable());
        this.executionMemory = memory == null ? null : memory.getIfAvailable();
    }

    @Override
    public String id() {
        return "playwright";
    }

    @Override
    public LocatorMemoryDocument execute(ExecutionPlan plan, Consumer<ProgressEvent> progress) {
        return execute(plan, progress, null);
    }

    @Override
    public LocatorMemoryDocument execute(ExecutionPlan plan, Consumer<ProgressEvent> progress, CancellationToken cancellationToken) {
        return execute(plan, progress, cancellationToken, null);
    }

    @Override
    public LocatorMemoryDocument execute(
            ExecutionPlan plan,
            Consumer<ProgressEvent> progress,
            CancellationToken cancellationToken,
            BrowserExecutionOptions options) {
        List<LocatorMemoryEntry> entries = new ArrayList<>();
        Map<String, LocatorMemoryEntry> known = new HashMap<>();
        String traceId = TraceContext.current();
        int totalSteps = plan.steps().size();
        try (Playwright playwright = Playwright.create()) {
            long browserStarted = System.nanoTime();
            Boolean headlessOverride = options == null ? null : options.headless();
            boolean headless = PlaywrightBrowserLauncher.resolveHeadless(properties.getBrowser(), headlessOverride);
            boolean maximize = PlaywrightBrowserLauncher.resolveMaximize(properties.getBrowser(), headless);
            TraceLogger.info("BROWSER", "BROWSER_START", "Launching browser", TraceMeta.of(
                    "browser", properties.getBrowser().getType(),
                    "headless", headless,
                    "maximizeRequested", maximize,
                    "requestedZoom", BrowserPageZoom.resolveZoomPercent(properties.getBrowser(), headless)
            ));
            checkCancellation(cancellationToken);
            PlaywrightBrowserLauncher.Session session =
                    PlaywrightBrowserLauncher.open(playwright, properties.getBrowser(), headlessOverride);
            Page page = session.page();
            if (!PlaywrightBrowserLauncher.isPageAlive(page)) {
                throw new SmartQaException(ErrorCode.BROWSER_ERROR, "Target page, context or browser has been closed");
            }
            BrowserLifecycle.info(BrowserLifecycle.EXECUTION_START, "Browser execution started",
                    BrowserLifecycle.identity(session.browser(), session.context(), page,
                            "PlaywrightBrowserExecutionProvider", null, null));
            AtomicReference<Page> activePage = new AtomicReference<>(page);
            ACTIVE_PAGE.set(activePage);
            ACTIVE_KNOWN.set(known);
            int maxBacktrack = properties.getRecovery() == null ? 3 : properties.getRecovery().getMaxBacktrackSteps();
            STATE_HISTORY.set(new com.smartqa.browser.intelligence.recovery.BrowserStateHistory(maxBacktrack));
            SearchStateContract.begin();
            try {
            TraceLogger.info("BROWSER", "BROWSER_STARTED", "Browser launched",
                    (System.nanoTime() - browserStarted) / 1_000_000,
                    TraceMeta.of(
                            "browser", properties.getBrowser().getType(),
                            "headless", session.headless(),
                            "maximizeRequested", session.maximizeRequested(),
                            "requestedZoom", session.zoomPercent(),
                            "effectiveZoom", session.zoom() == null ? "" : session.zoom().effectiveZoomPercent()
                    ));
            TraceLogger.info("BROWSER", "CONTEXT_CREATED", "Browser context ready", TraceMeta.of("pages", 1));
            TraceLogger.info("BROWSER", "PAGE_CREATED", "Page created");
            PageDiagnostics diagnostics = new PageDiagnostics();
            diagnostics.attach(page);
            Map<String, Object> startedMeta = new LinkedHashMap<>();
            if (session.viewport() != null) {
                startedMeta.putAll(session.viewport().toTraceMeta());
            }
            if (session.zoom() != null) {
                startedMeta.putAll(session.zoom().toTraceMeta());
            }
            emitRich(progress, plan, "BROWSER_STARTED", "Opening " + properties.getBrowser().getType() + " browser",
                    startedMeta,
                    0, totalSteps, null, null, null);
            storeEvent(traceId, plan, EventType.BROWSER_STARTED, EventComponent.BROWSER, "Browser started",
                    null, 0, null, null, null, null, 0, null);
            log.info("browser_session_started testCaseId={} engine={} headless={} maximize={} zoom={} inner={}x{}",
                    plan.testCaseId(),
                    properties.getBrowser().getType(),
                    session.headless(),
                    session.maximizeRequested(),
                    session.zoomPercent(),
                    session.viewport() == null ? 0 : session.viewport().innerWidth(),
                    session.viewport() == null ? 0 : session.viewport().innerHeight());
            int stepNumber = 0;
            java.util.Set<String> completedStepIds = new java.util.HashSet<>();
            for (ExecutionPlan.PlannedStep step : plan.steps()) {
                stepNumber++;
                checkCancellation(cancellationToken);
                page = activePage.get();
                if (!PlaywrightBrowserLauncher.isPageAlive(page)) {
                    throw new SmartQaException(ErrorCode.BROWSER_ERROR, "Target page, context or browser has been closed");
                }
                BrowserLifecycle.info(BrowserLifecycle.BROWSER_OPERATION_STARTED, "Starting step",
                        BrowserLifecycle.correlation(step.id(), null, stepNumber));
                ACTIVE_EVIDENCE_MOMENT.set(java.util.UUID.randomUUID().toString());
                if (!com.smartqa.intent.IntentPlanDag.prerequisitesMet(step.dependsOn(), completedStepIds)) {
                    emitRich(progress, plan, "STEP_SKIPPED",
                            "Prerequisite state was not satisfied for " + step.action(),
                            Map.of("stepId", step.id(), "dependsOn", step.dependsOn(),
                                    "category", "DEPENDENCY_FAILED",
                                    "evidenceMomentId", nullToEmpty(ACTIVE_EVIDENCE_MOMENT.get())),
                            stepNumber, totalSteps, safeUrl(page), safeTitle(page), null);
                    continue;
                }
                com.smartqa.browser.intelligence.PageReadinessContract.awaitInteractive(
                        page, plan.testCaseId(), progress);
                emitRich(progress, plan, "STEP_STARTED", "Running " + step.action() + " " + nullToEmpty(step.target()),
                        Map.of("stepId", step.id()), stepNumber, totalSteps, safeUrl(page), safeTitle(page), null);
                log.info("step_started testCaseId={} stepId={} action={}", plan.testCaseId(), step.id(), step.action());
                try {
                    LocatorMemoryEntry entry = perform(page, plan, step, progress, diagnostics, known, cancellationToken, stepNumber, totalSteps, traceId);
                    recordBrowserState(page, step, stepNumber, true);
                    page = activePage.get();
                    if (!Objects.equals(page, session.page()) && page != null) {
                        diagnostics.attach(page);
                    }
                    if (SupportedActions.NAVIGATE.equalsIgnoreCase(nullToEmpty(step.action()))) {
                        BrowserViewportEvidence afterNav = PlaywrightBrowserLauncher.captureViewport(
                                page,
                                PlaywrightBrowserLauncher.resolveBrowserType(properties.getBrowser()),
                                session.headless(),
                                session.maximizeRequested());
                        PlaywrightBrowserLauncher.emitViewportReady(afterNav);
                        if (session.zoom() != null) {
                            emitRich(progress, plan, "BROWSER_ZOOM_CONFIGURED", "Page zoom after navigation",
                                    session.zoom().toTraceMeta(), stepNumber, totalSteps, safeUrl(page), safeTitle(page), null);
                        }
                    }
                    entries.add(entry);
                    if (entry.semanticTarget() != null && entry.resolvedLocator() != null) {
                        known.put(key(step.action(), step.target()), entry);
                    }
                    if (entry.resolvedLocator() != null) {
                        java.util.Map<String, Object> locatorDetails = new java.util.HashMap<>();
                        locatorDetails.put("stepId", step.id());
                        locatorDetails.put("locator", entry.resolvedLocator());
                        locatorDetails.put("locatorType", nullToEmpty(entry.locatorType()));
                        locatorDetails.put("confidence", entry.confidence());
                        locatorDetails.put("controlType", nullToEmpty(entry.controlType()));
                        locatorDetails.put("candidateId", nullToEmpty(entry.resolvedLocator()));
                        locatorDetails.put("evidenceMomentId", nullToEmpty(ACTIVE_EVIDENCE_MOMENT.get()));
                        locatorDetails.put("executionPath", "SEMANTIC_INTENT");
                        emitRich(progress, plan, "LOCATOR_SELECTED", "Selected locator for " + step.target(), locatorDetails,
                                stepNumber, totalSteps, safeUrl(page), safeTitle(page), null);
                        emitRich(progress, plan, "LOCATOR_RESOLVED", "Resolved " + step.target(), Map.of(
                                "stepId", step.id(),
                                "locator", entry.resolvedLocator(),
                                "locatorType", nullToEmpty(entry.locatorType()),
                                "evidenceMomentId", nullToEmpty(ACTIVE_EVIDENCE_MOMENT.get())
                        ), stepNumber, totalSteps, safeUrl(page), safeTitle(page), null);
                        log.info("locator_resolved testCaseId={} stepId={} locator={}", plan.testCaseId(), step.id(), entry.resolvedLocator());
                    }
                    emitRich(progress, plan, "STEP_COMPLETED", "Completed " + step.action(),
                            Map.of("stepId", step.id(), "url", safeUrl(page), "title", safeTitle(page)),
                            stepNumber, totalSteps, safeUrl(page), safeTitle(page), null);
                    log.info("step_completed testCaseId={} stepId={}", plan.testCaseId(), step.id());
                    completedStepIds.add(step.id());
                    if (shouldRememberSuccess(step, entry) && executionMemory != null) {
                        executionMemory.rememberSuccess(
                                safeUrl(page),
                                plan.testCaseId() == null ? "" : plan.testCaseId().toString(),
                                plan.executionRunId() == null ? "" : plan.executionRunId().toString(),
                                step.action(),
                                step.target(),
                                entry.locatorType(),
                                nullToEmpty(step.containerContext()),
                                nullToEmpty(entry.frameContext()),
                                "",
                                entry.locatorType(),
                                entry.resolvedLocator(),
                                entry.confidence(),
                                nullToEmpty(entry.controlType()),
                                nullToEmpty(step.containerContext()),
                                nullToEmpty(entry.locatorType()),
                                nullToEmpty(ACTIVE_EVIDENCE_MOMENT.get())
                        );
                    }
                } catch (RuntimeException ex) {
                    publishDiagnostics(plan, progress, diagnostics, traceId, step.id(), stepNumber);
                    String failShot = captureShot(page, plan, step.id(), stepNumber, "FAILURE");
                    emitRich(progress, plan, "FAILURE", ex.getMessage() == null ? "Step failed" : ex.getMessage(),
                            Map.of("stepId", step.id()), stepNumber, totalSteps, safeUrl(page), safeTitle(page), failShot);
                    // Canonical ScreenshotService only — ad-hoc provider screenshots removed.
                    storeEvent(traceId, plan, EventType.ACTION_FAILED, EventComponent.PLAYWRIGHT,
                            ex.getMessage(), step.id(), stepNumber, step.action(), step.target(),
                            null, null, 0, ex.getMessage());
                    if (ex instanceof ExecutionCancelledException cancelled) {
                        throw cancelled;
                    }
                    if (BrowserLifecycle.isClosedTargetFailure(ex)) {
                        emitRich(progress, plan, "FAILURE", ex.getMessage() == null ? "Browser closed" : ex.getMessage(),
                                java.util.Map.of("stepId", step.id(), "screenshotAvailable", failShot != null),
                                stepNumber, totalSteps, null, null, failShot);
                        throw new SmartQaException(ErrorCode.BROWSER_ERROR,
                                ex.getMessage() == null ? "Target page, context or browser has been closed" : ex.getMessage(),
                                ex);
                    }
                    if (ex instanceof SmartQaException smartQaException) {
                        throw smartQaException;
                    }
                    throw new SmartQaException(ErrorCode.EXECUTION_FAILED, ex.getMessage(), ex);
                }
            }
            captureShot(page, plan, "final", totalSteps, "TEST_COMPLETED");
            diagnostics.close();
            } finally {
                PlaywrightBrowserLauncher.closeQuietly(session, BrowserLifecycle.CLOSE_EXECUTION_COMPLETE,
                        "PlaywrightBrowserExecutionProvider.execute");
                BrowserLifecycle.info(BrowserLifecycle.EXECUTION_END, "Browser execution ended");
                BrowserLifecycle.info(BrowserLifecycle.BROWSER_OPERATION_FINISHED, "Browser operations finished");
                ACTIVE_PAGE.remove();
                ACTIVE_KNOWN.remove();
                STATE_HISTORY.remove();
                ACTIVE_EVIDENCE_MOMENT.remove();
                SearchStateContract.end();
            }
        }
        return new LocatorMemoryDocument(entries);
    }

    BrowserType.LaunchOptions launchOptions(BrowserExecutionOptions options) {
        Boolean headlessOverride = options == null ? null : options.headless();
        return PlaywrightBrowserLauncher.launchOptions(properties.getBrowser(), headlessOverride);
    }

    private LocatorMemoryEntry perform(
            Page page,
            ExecutionPlan plan,
            ExecutionPlan.PlannedStep step,
            Consumer<ProgressEvent> progress,
            PageDiagnostics diagnostics,
            Map<String, LocatorMemoryEntry> known,
            CancellationToken cancellationToken,
            int stepNumber,
            int totalSteps,
            String traceId) {
        String action = SupportedActions.canonicalize(step.action().toLowerCase(Locale.ROOT));
        return switch (action) {
            case SupportedActions.NAVIGATE -> {
                String url = resolveUrl(plan.baseUrl(), step);
                long navStarted = System.nanoTime();
                TraceLogger.info("BROWSER", "NAVIGATION_STARTED", "Navigating", TraceMeta.of("url", url, "stepId", step.id()));
                storeEvent(traceId, plan, EventType.PAGE_NAVIGATION_STARTED, EventComponent.BROWSER,
                        "Navigating to " + url, step.id(), stepNumber, "navigate", url, null, null, 0, null);
                try {
                    BrowserNavigation.navigate(page, url);
                    page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                    PageStateWatcher.waitUntilInteractive(
                            page,
                            () -> PageReadinessContract.countInteractive(page),
                            plan.testCaseId(),
                            progress
                    );
                    BrowserSnapshot snapshot = inspect(page, plan, progress, diagnostics);
                    long navDuration = (System.nanoTime() - navStarted) / 1_000_000;
                    TraceLogger.info("BROWSER", "NAVIGATION_COMPLETED", "Navigation completed",
                            navDuration,
                            TraceMeta.of("url", url, "finalUrl", snapshot.url(), "title", snapshot.title()));
                    emitRich(progress, plan, "PAGE_LOADED", "Loaded " + snapshot.title(),
                            Map.of("url", snapshot.url(), "title", snapshot.title()),
                            stepNumber, totalSteps, snapshot.url(), snapshot.title(),
                            captureShot(page, plan, step.id(), stepNumber, "PAGE_LOADED"));
                    storeEvent(traceId, plan, EventType.PAGE_LOADED, EventComponent.BROWSER,
                            "Loaded " + snapshot.title(), step.id(), stepNumber, "navigate", url, null, null, navDuration, null);
                    yield memory(step, null, null, 1.0, page, false, null, null);
                } catch (RuntimeException ex) {
                    TraceLogger.error("BROWSER", "NAVIGATION_FAILED", "Navigation failed", ex,
                            (System.nanoTime() - navStarted) / 1_000_000, TraceMeta.of("url", url));
                    throw new SmartQaException(ErrorCode.NAVIGATION_FAILURE, "Navigation failed for URL: " + url, ex);
                }
            }
            case SupportedActions.WAIT, SupportedActions.WAIT_FOR_STATE -> {
                waitFor(page, plan, step, progress, diagnostics);
                yield memory(step, null, null, 1.0, page, false, null, null);
            }
            case SupportedActions.SWITCH_TO_NEW_TAB -> {
                Page switched = switchToNewTab(page);
                yield memory(step, null, null, 1.0, switched == null ? page : switched, false, null, null);
            }
            case SupportedActions.SCROLL -> {
                String scrollTarget = firstNonBlank(step.target(), step.value(), "down");
                boolean found = ScrollIntelligence.scrollToTarget(page, scrollTarget);
                known.clear();
                inspect(page, plan, progress, diagnostics);
                TraceLogger.info("BROWSER", "SCROLL_COMPLETED", "Scroll step finished", TraceMeta.of(
                        "target", scrollTarget, "found", found
                ));
                yield memory(step, "scroll|" + scrollTarget, "scroll", found ? 0.9 : 0.5, page, false, scrollTarget, null);
            }
            case SupportedActions.PRESS_KEY -> {
                page.keyboard().press(firstNonBlank(step.value(), step.target()));
                yield memory(step, null, null, 1.0, page, false, null, null);
            }
            case SupportedActions.FILTER -> applyFilter(page, plan, step, progress, diagnostics, known, cancellationToken, stepNumber, totalSteps, traceId);
            case SupportedActions.VERIFY -> {
                if (VerifyExpectation.isTitleTarget(step.target())) {
                    String expected = VerifyExpectation.expectedText(step.assertion(), step.value());
                    if (expected == null) {
                        expected = firstNonBlank(step.value(), step.assertion());
                    }
                    if (expected != null && !safeTitle(page).toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT))) {
                        throw new SmartQaException(ErrorCode.ASSERTION_FAILED, "Page title did not contain: " + expected);
                    }
                    yield memory(step, null, "title", 1.0, page, false, safeTitle(page), null);
                }
                LocatorMemoryEntry textMatch = verifyVisibleText(page, plan, step, progress);
                if (textMatch != null) {
                    storeEvent(traceId, plan, EventType.ASSERTION_VERIFIED, EventComponent.ASSERTION,
                            "Assertion passed", step.id(), stepNumber, "verify", step.target(), null, null, 0, null);
                    yield textMatch;
                }
                String expectedForError = VerifyExpectation.expectedText(step.assertion(), step.value());
                if (!VerifyExpectation.isSpecificExpectedText(expectedForError)
                        && VerifyExpectation.isSpecificExpectedText(step.target())
                        && !VerifyExpectation.isPageLevelTarget(step.target())) {
                    expectedForError = step.target();
                }
                if (VerifyExpectation.isSpecificExpectedText(expectedForError)) {
                    // Attempt host restore before declaring assertion failure (never change expected text)
                    if (HostContextGuard.hostDiverged(plan.baseUrl(), safeUrl(page))) {
                        HostContextGuard.restoreExpectedHostIfNeeded(page, plan.baseUrl());
                        LocatorMemoryEntry retry = verifyVisibleText(page, plan, step, progress);
                        if (retry != null) {
                            storeEvent(traceId, plan, EventType.ASSERTION_VERIFIED, EventComponent.ASSERTION,
                                    "Assertion passed after host restore", step.id(), stepNumber, "verify", step.target(),
                                    null, null, 0, null);
                            yield retry;
                        }
                    }
                    String screenshotId = captureShot(page, plan, step.id(), stepNumber, "FAILURE");
                    String hostNote = HostContextGuard.hostDiverged(plan.baseUrl(), safeUrl(page))
                            || HostContextGuard.isExportLikeHost(safeUrl(page))
                            ? " | wrong_host=" + safeUrl(page)
                            : "";
                    storeEvent(traceId, plan, EventType.ASSERTION_FAILED, EventComponent.ASSERTION,
                            "Assertion failed: expected text not found", step.id(), stepNumber, "verify", step.target(), null, null, 0,
                            "Expected: " + expectedForError + " | URL: " + safeUrl(page) + " | Title: " + safeTitle(page) + hostNote);
                    throw assertionTruthFailure(page, plan, expectedForError, hostNote);
                }
                if (VerifyExpectation.isPageLevelTarget(step.target())) {
                    throw new SmartQaException(ErrorCode.ASSERTION_FAILED, "Page did not contain: " + firstNonBlank(expectedForError, step.target()));
                }
                ElementResolver.ResolvedElement resolved = resolveWithHealing(page, plan, step, progress, diagnostics, known);
                apply(resolved.locator(), action, step, resolved.controlType(), page, cancellationToken, stepNumber, totalSteps, traceId, plan, progress);
                yield memory(step, resolved.resolvedLocator(), resolved.locatorType(), resolved.confidence(), page,
                        resolved.healed(), safeText(resolved.locator()), resolved.locatorCloud(), resolved.controlType(), resolved);
            }
            default -> {
                checkCancellation(cancellationToken);
                BrowserSnapshot beforeSnap = inspect(page, plan, progress, diagnostics);
                StateSnapshot stateBefore = StateSnapshot.capture(page, beforeSnap.interactiveCount());
                StateSnapshot.emitBefore(stateBefore);
                PageStateWatcher.Observation before = PageStateWatcher.capture(page, beforeSnap.interactiveCount());
                ElementResolver.ResolvedElement resolved = resolveWithHealing(page, plan, step, progress, diagnostics, known);
                ControlType ct = resolved.controlType();
                String ctName = ct != null ? ct.name() : "UNKNOWN";
                emitRich(progress, plan, "ELEMENT_DISCOVERED", "Discovered " + step.target(), Map.of(
                        "stepId", step.id(),
                        "locator", resolved.resolvedLocator(),
                        "locatorType", resolved.locatorType(),
                        "controlType", ctName,
                        "confidence", resolved.confidence(),
                        "frameContext", nullToEmpty(resolved.frameContext()),
                        "frameUrl", nullToEmpty(resolved.frameUrl()),
                        "frameName", nullToEmpty(resolved.frameName()),
                        "parentFrameContext", nullToEmpty(resolved.parentFrameContext()),
                        "targetPath", nullToEmpty(resolved.targetPath())
                ), stepNumber, totalSteps, safeUrl(page), safeTitle(page), null);
                storeEvent(traceId, plan, EventType.ELEMENT_DISCOVERED, EventComponent.DOM,
                        "Discovered " + step.target(), step.id(), stepNumber, step.action(), step.target(),
                        resolved.resolvedLocator(), ctName, 0, null);
                storeEvent(traceId, plan, EventType.CONTROL_CLASSIFIED, EventComponent.DOM,
                        "Classified as " + ctName, step.id(), stepNumber, step.action(), step.target(),
                        resolved.resolvedLocator(), ctName, 0, null);

                highlightElement(page, resolved.locator());
                emitRich(progress, plan, "ELEMENT_HIGHLIGHTED", "Highlighted " + step.target(), Map.of(
                        "stepId", step.id(),
                        "action", step.action(),
                        "target", nullToEmpty(step.target()),
                        "locator", nullToEmpty(resolved.resolvedLocator()),
                        "confidence", resolved.confidence(),
                        "executionProvider", PROVIDER_ID
                ), stepNumber, totalSteps, safeUrl(page), safeTitle(page), null);
                storeEvent(traceId, plan, EventType.ELEMENT_HIGHLIGHTED, EventComponent.DOM,
                        "Highlighted " + step.target(), step.id(), stepNumber, step.action(), step.target(),
                        resolved.resolvedLocator(), ctName, 0, null);
                captureShot(page, plan, step.id(), stepNumber, "BEFORE_ACTION");

                String observedText = safeText(resolved.locator());
                apply(resolved.locator(), action, step, resolved.controlType(), page, cancellationToken, stepNumber, totalSteps, traceId, plan, progress);

                removeHighlight(page);
                String afterShot = captureShot(page, plan, step.id(), stepNumber, "AFTER_ACTION");
                emitRich(progress, plan, "AFTER_ACTION", "Completed " + step.action(),
                        Map.of("stepId", step.id()), stepNumber, totalSteps, safeUrl(page), safeTitle(page), afterShot);
                waitForAuthMenuIfNeeded(page, step);

                if (step.filter() != null) {
                    emitRich(progress, plan, "FILTER_APPLIED", "Applied filter " + step.filter().field(), Map.of(
                            "field", step.filter().field(),
                            "value", nullToEmpty(step.filter().displayValue())
                    ), stepNumber, totalSteps, safeUrl(page), safeTitle(page), null);
                }
                PageStateWatcher.waitForChange(
                        page,
                        before,
                        () -> PageReadinessContract.countInteractive(page),
                        plan.testCaseId(),
                        progress,
                        2_500
                );
                intelligence.invalidateEvidence();
                StateSnapshot stateAfter = StateSnapshot.capture(page,
                        PageReadinessContract.countInteractive(page));
                boolean changed = stateBefore.meaningfullyDifferent(stateAfter);
                StateSnapshot.emitAfter(stateAfter, changed);
                StateTransitionVerifier.Signals signals = StateTransitionVerifier.inspect(page, action, step.target());
                boolean widgetOk = StateTransitionVerifier.widgetStateMatches(
                        page, resolved.locator(), action, step.value());
                signals = StateTransitionVerifier.withIntendedState(
                        signals, widgetOk || signals.intendedStatePresent());
                StateTransitionVerifier.Verdict verdict = StateTransitionVerifier.verify(stateBefore, stateAfter, signals);
                if (!verdict.passAllowed()) {
                    throw new SmartQaException(ErrorCode.BUSINESS_STATE_MISMATCH, verdict.reason());
                }
                emitRich(progress, plan, "STATE_CHANGE_DETECTED",
                        changed ? "Meaningful state change detected" : "No strong state change detected",
                        Map.of("changed", changed, "stepId", step.id()),
                        stepNumber, totalSteps, safeUrl(page), safeTitle(page), null);
                TraceLogger.info("PLAYWRIGHT", "STATE_VERIFIED", "Post-action state verified", TraceMeta.of(
                        "changed", changed,
                        "stepId", step.id(),
                        "action", action,
                        "passAllowed", verdict.passAllowed(),
                        "url", safeUrl(page),
                        "title", safeTitle(page)
                ));
                if ("click".equalsIgnoreCase(action) && ct == ControlType.BUTTON) {
                    waitForResultSurface(page);
                }
                storeEvent(traceId, plan, EventType.STATE_CHANGED, EventComponent.BROWSER,
                        "Page state changed", step.id(), stepNumber, step.action(), step.target(),
                        resolved.resolvedLocator(), ctName, 0, null);
                yield memory(step, resolved.resolvedLocator(), resolved.locatorType(), resolved.confidence(), page,
                        resolved.healed(), observedText, resolved.locatorCloud(), ct, resolved);
            }
        };
    }

    private LocatorMemoryEntry applyFilter(
            Page page,
            ExecutionPlan plan,
            ExecutionPlan.PlannedStep step,
            Consumer<ProgressEvent> progress,
            PageDiagnostics diagnostics,
            Map<String, LocatorMemoryEntry> known,
            CancellationToken cancellationToken,
            int stepNumber,
            int totalSteps,
            String traceId) {
        IntentFilter filter = step.filter();
        String field = filter == null ? step.target() : firstNonBlank(filter.field(), step.target());
        String operator = filter == null ? "equals" : firstNonBlank(filter.operator(), "equals");
        String normalizedOperator = operator.toLowerCase(Locale.ROOT);
        String expectedValue = filter == null ? step.value() : firstNonBlank(filter.value(), step.value(), field);
        SearchStateContract.verifyReadyForFilter(page);
        BrowserSnapshot beforeSnap = inspect(page, plan, progress, diagnostics);
        PageStateWatcher.Observation before = PageStateWatcher.capture(page, beforeSnap.interactiveCount());
        String beforeResults = captureResultSignature(page);

        emitFilterEvent(progress, plan, stepNumber, totalSteps, "FILTER_DISCOVERY",
                "Discovering filter controls for " + field,
                Map.of("filterIntent", describeFilterIntent(field, operator, expectedValue, filter),
                        "field", nullToEmpty(field), "operator", operator, "value", nullToEmpty(expectedValue)));

        FilterEngine.Discovery discovery = null;
        try {
            discovery = filterEngine.discover(page, field, operator, expectedValue);
            filterEngine.ensureExpanded(page, discovery);
            inspect(page, plan, progress, diagnostics);
            discovery = filterEngine.discover(page, field, operator, expectedValue);
        } catch (RuntimeException ex) {
            TraceLogger.warn("FILTER", "FILTER_DISCOVERY_FALLBACK", "FilterEngine discovery fell back",
                    TraceMeta.of("field", nullToEmpty(field), "message", ex.getMessage() == null ? "" : ex.getMessage()));
        }

        if ("between".equals(normalizedOperator) && filter != null) {
            String min = filter.min() == null ? step.value() : String.valueOf(filter.min().longValue());
            String max = filter.max() == null ? "" : String.valueOf(filter.max().longValue());
            ElementResolver.ResolvedElement minField = resolveRangeBound(page, plan, step, progress, diagnostics, known, field, true);
            applyBoundValue(page, minField, min);
            ElementResolver.ResolvedElement maxField = resolveRangeBound(page, plan, step, progress, diagnostics, known, field, false);
            applyBoundValue(page, maxField, max);
            emitFilterEvent(progress, plan, stepNumber, totalSteps, "FILTER_CONTROL_SELECTED",
                    "Selected range controls",
                    Map.of("field", nullToEmpty(field), "minLocator", nullToEmpty(minField.resolvedLocator()), "maxLocator", nullToEmpty(maxField.resolvedLocator())));
            emitFilterEvent(progress, plan, stepNumber, totalSteps, "FILTER_APPLIED",
                    "Applied " + field + " between " + min + " and " + max,
                    Map.of("field", nullToEmpty(field), "min", min, "max", max, "operator", operator));
            PageStateWatcher.waitForChange(
                    page,
                    before,
                    () -> PageReadinessContract.countInteractive(page),
                    plan.testCaseId(),
                    progress
            );
            emitFilterEvent(progress, plan, stepNumber, totalSteps, "FILTER_RESULTS_REFRESHED",
                    "Filter refresh check completed", Map.of("field", field));
            verifyFilterStateOrThrow(minField.locator(), min);
            verifyFilterStateOrThrow(maxField.locator(), max);
            emitFilterEvent(progress, plan, stepNumber, totalSteps, "FILTER_STATE_VERIFIED",
                    "Filter control state verified", Map.of("field", field, "operator", operator));
            verifyFilterResultsOrThrow(page, field, normalizedOperator, min + "-" + max, min, max, beforeResults,
                    progress, plan, stepNumber, totalSteps);
            return memory(step, minField.resolvedLocator(), minField.locatorType(), minField.confidence(), page, minField.healed(), min + "-" + max, minField.locatorCloud());
        }
        if (isNumericValue(expectedValue) && looksLikeRangeField(field) && !"equals".equals(normalizedOperator)) {
            return applyNumericRangeBound(page, plan, step, progress, diagnostics, known, cancellationToken,
                    stepNumber, totalSteps, traceId, field, expectedValue, operator, normalizedOperator,
                    before, beforeResults);
        }
        if (isNumericValue(expectedValue) && looksLikeRangeField(field)) {
            return applyNumericRangeBound(page, plan, step, progress, diagnostics, known, cancellationToken,
                    stepNumber, totalSteps, traceId, field, expectedValue, operator, normalizedOperator,
                    before, beforeResults);
        }
        String value = expectedValue;
        String fieldTarget = firstNonBlank(step.target(), humanizeField(field));
        ElementResolver.ResolvedElement resolved;
        String bindAction;
        if (discovery != null && discovery.optionCandidate() != null) {
            resolved = filterEngine.resolveOption(page, discovery);
            bindAction = discovery.bindAction();
        } else {
            resolved = resolveFilterField(page, plan, step, progress, diagnostics, known, fieldTarget);
            bindAction = actionForFilterControl(resolved.controlType());
        }
        ExecutionPlan.PlannedStep bindStep = new ExecutionPlan.PlannedStep(
                step.id(), bindAction, fieldTarget, value, step.assertion(), step.filter());
        apply(resolved.locator(), bindAction, bindStep, resolved.controlType(), page,
                cancellationToken, stepNumber, totalSteps, traceId, plan, progress);
        emitFilterEvent(progress, plan, stepNumber, totalSteps, "FILTER_CONTROL_SELECTED",
                "Selected filter control",
                Map.of("field", nullToEmpty(field), "control", nullToEmpty(resolved.resolvedLocator()), "confidence", resolved.confidence()));
        emitFilterEvent(progress, plan, stepNumber, totalSteps, "FILTER_APPLIED", "Applied " + field + "=" + value,
                Map.of("field", nullToEmpty(field), "value", nullToEmpty(value), "operator", operator));
        PageStateWatcher.waitForChange(
                page,
                before,
                () -> PageReadinessContract.countInteractive(page),
                plan.testCaseId(),
                progress
        );
        emitFilterEvent(progress, plan, stepNumber, totalSteps, "FILTER_RESULTS_REFRESHED",
                "Filter refresh check completed", Map.of("field", field));
        if (!resolved.locator().isVisible()) {
            throw new SmartQaException(ErrorCode.FILTER_APPLICATION_FAILURE,
                    "Filter control became unavailable after applying: " + field);
        }
        verifyFilterStateOrThrow(resolved.locator(), value);
        emitFilterEvent(progress, plan, stepNumber, totalSteps, "FILTER_STATE_VERIFIED",
                "Filter control state verified", Map.of("field", field, "operator", operator));
        verifyFilterResultsOrThrow(page, field, normalizedOperator, value, null, null, beforeResults, progress, plan, stepNumber, totalSteps);
        return memory(step, resolved.resolvedLocator(), resolved.locatorType(), resolved.confidence(), page,
                resolved.healed(), safeText(resolved.locator()), resolved.locatorCloud(), resolved.controlType(), resolved);
    }

    private void verifyFilterResultsOrThrow(
            Page page,
            String field,
            String operator,
            String value,
            String min,
            String max,
            String beforeResults,
            Consumer<ProgressEvent> progress,
            ExecutionPlan plan,
            int stepNumber,
            int totalSteps) {
        String afterResults = captureResultSignature(page);
        boolean refreshed = !beforeResults.equals(afterResults);
        if (!refreshed) {
            emitFilterEvent(progress, plan, stepNumber, totalSteps, "FILTER_APPLY_DEFERRED",
                    "Filter control set; result container unchanged pending search/apply",
                    Map.of("field", nullToEmpty(field), "operator", operator));
            return;
        }
        boolean semanticOk = verifyResultSemantics(page, operator, value, min, max);
        if (!semanticOk) {
            emitFilterEvent(progress, plan, stepNumber, totalSteps, "FILTER_VALIDATION_FAILED",
                    "Filtered results failed semantic verification",
                    Map.of("field", nullToEmpty(field), "operator", operator, "value", nullToEmpty(value)));
            throw new SmartQaException(ErrorCode.FILTER_VALIDATION_FAILURE,
                    "FILTER_VALIDATION_INCONCLUSIVE: results could not be semantically verified");
        }
        emitFilterEvent(progress, plan, stepNumber, totalSteps, "FILTER_RESULTS_VERIFIED",
                "Filtered result semantics verified",
                Map.of("field", nullToEmpty(field), "operator", operator, "value", nullToEmpty(value)));
    }

    private static String describeFilterIntent(String field, String operator, String value, IntentFilter filter) {
        if ("between".equalsIgnoreCase(operator) && filter != null) {
            return nullToEmpty(field) + " between " + filter.min() + " and " + filter.max();
        }
        return nullToEmpty(field) + " " + nullToEmpty(operator) + " " + nullToEmpty(value);
    }

    private static void verifyFilterStateOrThrow(Locator locator, String expected) {
        try {
            if (locator.isChecked()) {
                return;
            }
        } catch (RuntimeException ignored) {
        }
        String text = safeText(locator).toLowerCase(Locale.ROOT);
        String expectedLower = nullToEmpty(expected).toLowerCase(Locale.ROOT);
        if (!expectedLower.isBlank() && text.contains(expectedLower)) {
            return;
        }
        try {
            String inputValue = locator.inputValue().toLowerCase(Locale.ROOT);
            if (!expectedLower.isBlank() && inputValue.contains(expectedLower)) {
                return;
            }
            if (NativeSelectHandler.sameLogicalValue(expected, locator.inputValue())) {
                return;
            }
        } catch (RuntimeException ignored) {
        }
        throw new SmartQaException(ErrorCode.FILTER_VALIDATION_FAILURE,
                "Filter control state does not reflect expected value: " + expected);
    }

    private boolean verifyResultSemantics(Page page, String operator, String value, String min, String max) {
        Locator resultNodes = page.locator("[data-testid*='result'], [class*='result'], [class*='product'], [class*='item'], table tbody tr, [role='row']");
        int count = Math.min(resultNodes.count(), 15);
        if (count <= 0) {
            return false;
        }
        List<String> samples = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Locator row = resultNodes.nth(i);
            if (!row.isVisible()) {
                continue;
            }
            String text = safeText(row);
            if (!text.isBlank()) {
                samples.add(text.toLowerCase(Locale.ROOT));
            }
        }
        if (samples.isEmpty()) {
            return false;
        }
        if ("between".equals(operator) && min != null && max != null) {
            double minV = Double.parseDouble(min);
            double maxV = Double.parseDouble(max);
            for (String sample : samples) {
                List<Double> nums = extractNumbers(sample);
                if (nums.stream().noneMatch(v -> v >= minV && v <= maxV)) {
                    return false;
                }
            }
            return true;
        }
        String expected = nullToEmpty(value).toLowerCase(Locale.ROOT);
        if (expected.isBlank()) {
            return false;
        }
        return switch (operator) {
            case "equals", "contains", "starts_with", "ends_with", "in" ->
                    samples.stream().allMatch(s -> s.contains(expected));
            case "not_equals", "not_contains", "not_in" ->
                    samples.stream().noneMatch(s -> s.contains(expected));
            default -> false;
        };
    }

    private static List<Double> extractNumbers(String text) {
        List<Double> values = new ArrayList<>();
        String normalized = text.replaceAll("[^0-9.]+", " ").trim();
        if (normalized.isBlank()) {
            return values;
        }
        for (String token : normalized.split("\\s+")) {
            try {
                values.add(Double.parseDouble(token));
            } catch (NumberFormatException ignored) {
            }
        }
        return values;
    }

    private String captureResultSignature(Page page) {
        Locator resultNodes = page.locator("[data-testid*='result'], [class*='result'], [class*='product'], [class*='item'], table tbody tr, [role='row']");
        int count = Math.min(resultNodes.count(), 20);
        StringBuilder signature = new StringBuilder();
        signature.append("count=").append(count).append('|');
        for (int i = 0; i < count; i++) {
            Locator row = resultNodes.nth(i);
            if (!row.isVisible()) {
                continue;
            }
            signature.append(safeText(row)).append('|');
        }
        return signature.toString();
    }

    private void emitFilterEvent(
            Consumer<ProgressEvent> progress,
            ExecutionPlan plan,
            int stepNumber,
            int totalSteps,
            String type,
            String message,
            Map<String, Object> details) {
        emitRich(progress, plan, type, message, details, stepNumber, totalSteps, null, null, null);
    }

    private ElementResolver.ResolvedElement resolveFilterField(
            Page page,
            ExecutionPlan plan,
            ExecutionPlan.PlannedStep step,
            Consumer<ProgressEvent> progress,
            PageDiagnostics diagnostics,
            Map<String, LocatorMemoryEntry> known,
            String fieldTarget) {
        try {
            ElementResolver.ResolvedElement asInput = resolveHint(
                    page, plan, step, progress, diagnostics, known, fieldTarget, SupportedActions.INPUT);
            if (asInput.controlType() == null || asInput.controlType().supportsInput()) {
                return asInput;
            }
        } catch (SmartQaException ignored) {
        }
        return resolveHint(page, plan, step, progress, diagnostics, known, fieldTarget, SupportedActions.SELECT);
    }

    private static String actionForFilterControl(ControlType controlType) {
        if (controlType != null && controlType.supportsSelect()) {
            return SupportedActions.SELECT;
        }
        if (controlType == ControlType.CHECKBOX) {
            return SupportedActions.CHECKBOX;
        }
        return SupportedActions.INPUT;
    }

    private static String humanizeField(String field) {
        if (field == null || field.isBlank()) {
            return "";
        }
        String spaced = field.replace('_', ' ').replace('-', ' ').trim();
        if (spaced.isBlank()) {
            return field;
        }
        String[] parts = spaced.split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                out.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return out.toString();
    }

    private ElementResolver.ResolvedElement resolveHint(
            Page page,
            ExecutionPlan plan,
            ExecutionPlan.PlannedStep step,
            Consumer<ProgressEvent> progress,
            PageDiagnostics diagnostics,
            Map<String, LocatorMemoryEntry> known,
            String hint,
            String action) {
        ExecutionPlan.PlannedStep synthetic = new ExecutionPlan.PlannedStep(
                step.id(), action, hint, step.value(), step.assertion(), step.filter());
        return resolveWithHealing(page, plan, synthetic, progress, diagnostics, known);
    }

    private void apply(Locator locator, String action, ExecutionPlan.PlannedStep step) {
        apply(locator, action, step, null, null, null, 0, 0, null, null, null);
    }

    private void apply(Locator locator, String action, ExecutionPlan.PlannedStep step,
                       ControlType controlType, Page page) {
        apply(locator, action, step, controlType, page, null, 0, 0, null, null, null);
    }

    private void apply(Locator locator, String action, ExecutionPlan.PlannedStep step,
                       ControlType controlType, Page page, CancellationToken cancellationToken,
                       int stepNumber, int totalSteps, String traceId, ExecutionPlan plan,
                       Consumer<ProgressEvent> progress) {
        long started = System.nanoTime();
        String maskedValue = SecretMasker.maskValue(
                step.target() == null ? action : step.target(),
                step.value());

        if (controlType == null) {
            try {
                controlType = ControlClassifier.classify(locator);
            } catch (RuntimeException ignored) {
                controlType = ControlType.OTHER;
            }
        }

        checkCancellation(cancellationToken);

        String ctName = controlType.name();
        TraceLogger.info("PLAYWRIGHT", "ACTION_STARTED", "Playwright action started", TraceMeta.of(
                "stepId", step.id(),
                "instruction", step.target(),
                "actionType", action,
                "controlType", ctName,
                "value", maskedValue
        ));
        if (traceId != null && plan != null) {
            storeEvent(traceId, plan, EventType.ACTION_STARTED, EventComponent.PLAYWRIGHT,
                    "Action started: " + action, step.id(), stepNumber, action, step.target(),
                    null, ctName, 0, null);
            emitRich(progress, plan, "ACTION_STARTED", action + " on " + nullToEmpty(step.target()),
                    Map.of("stepId", step.id(), "action", action, "controlType", ctName,
                            "executionProvider", PROVIDER_ID),
                    stepNumber, totalSteps, page != null ? safeUrl(page) : null,
                    page != null ? safeTitle(page) : null, null);
        }
        try {
            switch (action) {
                case SupportedActions.CLICK, SupportedActions.FILTER,
                     SupportedActions.EXPAND, SupportedActions.COLLAPSE,
                     SupportedActions.ADD_TO_CART, SupportedActions.QUANTITY,
                     SupportedActions.SUBMIT, SupportedActions.VISUAL_TARGET,
                     SupportedActions.CLEAR_FILTERS -> {
                    String clickPrefix = clickIntentPrefix(step.target());
                    Locator clickTarget = locator;
                    if ("LOGIN".equals(clickPrefix) && page != null) {
                        Locator refreshed = refreshCompactAuthLocator(page, step.target());
                        if (refreshed != null) {
                            clickTarget = refreshed;
                        }
                    }
                    if (clickPrefix != null) {
                        logClickActionability(clickPrefix, clickTarget, step);
                        TraceLogger.info("PLAYWRIGHT", clickPrefix + "_CLICK_STARTED",
                                clickPrefix + " click started", TraceMeta.of(
                                        "stepId", step.id(),
                                        "target", step.target(),
                                        "locatorVisible", safeVisible(clickTarget),
                                        "locatorEnabled", safeEnabled(clickTarget)
                                ));
                    }
                    ResultingPageAfterClick.Armed armed = ResultingPageAfterClick.arm(page);
                    SearchStateContract.rememberNewPageWatch(armed.before(), armed.popup());
                    if (SearchStateContract.isFilterTarget(step.target())) {
                        SearchStateContract.verifyReadyForFilter(page);
                    }
                    QuantityIntelligence.Snapshot qtyBefore = page != null
                            && (SupportedActions.QUANTITY.equals(action)
                            || QuantityIntelligence.looksLikeIncrement(step.target()))
                            ? QuantityIntelligence.capture(page)
                            : null;
                    boolean sidebarNav = page != null && looksLikeSidebarLocation(step.location());
                    if (sidebarNav) {
                        Locator nav = firstNavLocator(page, step.target());
                        if (nav != null) {
                            clickTarget = nav;
                        }
                    }
                    String urlBeforeClick = page == null ? "" : safeUrl(page);
                    clickWithActionability(clickTarget, page, step);
                    if ("LOGIN".equals(clickPrefix) && page != null) {
                        confirmLoginState(page);
                    } else if (page != null && (sidebarNav || looksLikeNavigatingControl(controlType))) {
                        waitForPossibleNavigation(page, urlBeforeClick);
                    }
                    if (qtyBefore != null) {
                        QuantityIntelligence.ensureIncremented(page, qtyBefore);
                    }
                    if (page != null && (SupportedActions.ADD_TO_CART.equals(action)
                            || CartIntelligence.looksLikeAddToCart(step.target()))) {
                        CartIntelligence.ensureAdded(page);
                    }
                    String expectedAppUrl = plan == null ? null : plan.baseUrl();
                    Page afterClick = ResultingPageAfterClick.resolve(page, armed, expectedAppUrl);
                    if (afterClick != null && afterClick != page) {
                        AtomicReference<Page> active = ACTIVE_PAGE.get();
                        if (active != null) {
                            active.set(afterClick);
                        }
                        Map<String, LocatorMemoryEntry> known = ACTIVE_KNOWN.get();
                        if (known != null) {
                            known.clear();
                        }
                    } else if ("click".equalsIgnoreCase(action)) {
                        // Fresh DOM for next resolve — do not reuse pre-click known locators after menu opens
                        Map<String, LocatorMemoryEntry> known = ACTIVE_KNOWN.get();
                        if (known != null && clickPrefix != null) {
                            known.clear();
                        }
                    }
                    if (afterClick != null && expectedAppUrl != null && !expectedAppUrl.isBlank()) {
                        Page restored = HostContextGuard.recoverIfLeftApplication(afterClick, expectedAppUrl);
                        if (restored != afterClick) {
                            AtomicReference<Page> active = ACTIVE_PAGE.get();
                            if (active != null) {
                                active.set(restored);
                            }
                            Map<String, LocatorMemoryEntry> known = ACTIVE_KNOWN.get();
                            if (known != null) {
                                known.clear();
                            }
                            afterClick = restored;
                        }
                        HostContextGuard.assertStillInApplication(afterClick, expectedAppUrl);
                    }
                    if (afterClick != null) {
                        Page restoredAuth = HostContextGuard.recoverIfUnexpectedAuthPage(afterClick, clickPrefix);
                        if (restoredAuth != afterClick) {
                            AtomicReference<Page> active = ACTIVE_PAGE.get();
                            if (active != null) {
                                active.set(restoredAuth);
                            }
                            afterClick = restoredAuth;
                        } else if (AssertionTruthEngine.looksLikeLoginUrl(safeUrl(afterClick))
                                && !"LOGIN".equalsIgnoreCase(clickPrefix == null ? "" : clickPrefix)) {
                            AtomicReference<Page> active = ACTIVE_PAGE.get();
                            if (active != null) {
                                active.set(afterClick);
                            }
                        }
                        HostContextGuard.assertNotUnexpectedAuthPage(afterClick, clickPrefix);
                    }
                    if (clickPrefix != null) {
                        TraceLogger.info("PLAYWRIGHT", clickPrefix + "_CLICK_COMPLETED",
                                clickPrefix + " click completed", TraceMeta.of(
                                        "stepId", step.id(),
                                        "target", step.target(),
                                        "url", afterClick == null ? "" : safeUrl(afterClick),
                                        "pageTitle", afterClick == null ? "" : safeTitle(afterClick)
                                ));
                    }
                }
                case SupportedActions.SEARCH -> {
                    if (page != null) {
                        String expectedAppUrl = plan == null ? null : plan.baseUrl();
                        SearchIntelligence.Result searchResult = RecoveryEngine.withRetry(page, () ->
                                SearchIntelligence.execute(page, locator, step.value(), expectedAppUrl));
                        TraceLogger.info("PLAYWRIGHT", "SEARCH_EXECUTED", "Search action completed", TraceMeta.of(
                                "stepId", step.id(),
                                "stateChanged", searchResult.stateChanged(),
                                "strategy", searchResult.strategy()
                        ));
                    } else {
                        locator.fill(step.value());
                    }
                }
                case SupportedActions.INPUT, SupportedActions.SET_VALUE -> {
                    locator.fill(step.value());
                    if (page != null) {
                        String expectedAppUrl = plan == null ? null : plan.baseUrl();
                        AutocompleteHandler.confirmSelectionIfNeeded(page, locator, step.value(), expectedAppUrl);
                        if (HostContextGuard.hostDiverged(expectedAppUrl, safeUrl(page))) {
                            HostContextGuard.restoreExpectedHostIfNeeded(page, expectedAppUrl);
                        }
                        if (SearchStateContract.looksLikeLocationControl(locator)) {
                            SearchStateContract.verifyLocation(page, step.value());
                        }
                    }
                }
                case SupportedActions.SELECT -> SelectControlDispatcher.select(page, locator, controlType, step.value());
                case SupportedActions.CHECKBOX, SupportedActions.RADIO ->
                        CustomToggleState.ensure(locator, !"false".equalsIgnoreCase(step.value()));
                case SupportedActions.HOVER -> locator.hover();
                case SupportedActions.VERIFY -> {
                    if (!locator.isVisible()) {
                        throw new SmartQaException(ErrorCode.ACTIONABILITY_FAILURE, "Expected element is not visible: " + step.target());
                    }
                    String expected = VerifyExpectation.expectedText(step.assertion(), step.value());
                    if (VerifyExpectation.isSpecificExpectedText(expected)
                            && !safeText(locator).toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT))) {
                        throw new SmartQaException(ErrorCode.ASSERTION_FAILED, "Verification failed for: " + step.target());
                    }
                }
                default -> throw new SmartQaException(ErrorCode.INTENT_INVALID, "Unsupported browser action: " + action);
            }
            PostActionContextCapture.capture(page);
            intelligence.invalidateEvidence();
            long durationMs = (System.nanoTime() - started) / 1_000_000;
            TraceLogger.info("PLAYWRIGHT", "ACTION_COMPLETED", "Playwright action completed",
                    durationMs,
                    TraceMeta.of("stepId", step.id(), "actionType", action,
                            "controlType", ctName, "result", "SUCCESS"));
            TraceLogger.info("PLAYWRIGHT", "ACTION_EXECUTED", "Playwright action executed",
                    durationMs,
                    TraceMeta.of("stepId", step.id(), "actionType", action,
                            "target", step.target(),
                            "controlType", ctName,
                            "url", page == null ? "" : safeUrl(page),
                            "title", page == null ? "" : safeTitle(page),
                            "result", "SUCCESS"));
            if (traceId != null && plan != null) {
                storeEvent(traceId, plan, EventType.ACTION_COMPLETED, EventComponent.PLAYWRIGHT,
                        "Action completed: " + action, step.id(), stepNumber, action, step.target(),
                        null, ctName, durationMs, null);
                emitRich(progress, plan, "ACTION_COMPLETED", action + " completed",
                        Map.of("stepId", step.id(), "action", action, "durationMs", durationMs,
                                "controlType", ctName, "executionProvider", PROVIDER_ID),
                        stepNumber, totalSteps, page != null ? safeUrl(page) : null,
                        page != null ? safeTitle(page) : null, null);
            }
        } catch (RuntimeException ex) {
            TraceLogger.error("PLAYWRIGHT", "ACTION_FAILED", "Playwright action failed", ex,
                    (System.nanoTime() - started) / 1_000_000,
                    TraceMeta.of("stepId", step.id(), "actionType", action,
                            "controlType", ctName, "result", "FAILED"));
            throw ex;
        }
    }

    private Page switchToNewTab(Page page) {
        SearchStateContract.Session session = SearchStateContract.current();
        NewPageTracker.Capture before = session.lastCapture() != null
                ? session.lastCapture()
                : NewPageTracker.capture(page);
        Page switched = NewPageTracker.switchToNewTab(page, before, session.lastPopup(), 5_000);
        AtomicReference<Page> active = ACTIVE_PAGE.get();
        if (active != null) {
            active.set(switched);
        }
        Map<String, LocatorMemoryEntry> known = ACTIVE_KNOWN.get();
        if (known != null) {
            known.clear();
        }
        TraceLogger.info("BROWSER", "CONTEXT_SWITCHED_NEW_PAGE", "Switched to newly opened page", TraceMeta.of(
                "oldUrl", safeUrl(page),
                "newUrl", safeUrl(switched)
        ));
        return switched;
    }

    private void waitFor(
            Page page,
            ExecutionPlan plan,
            ExecutionPlan.PlannedStep step,
            Consumer<ProgressEvent> progress,
            PageDiagnostics diagnostics) {
        BrowserSnapshot snapshot = intelligence.inspect(page, diagnostics.consoleErrors());
        PageStateWatcher.Observation before = PageStateWatcher.capture(page, snapshot.interactiveCount());
        PageStateWatcher.waitForChange(
                page,
                before,
                () -> PageReadinessContract.countInteractive(page),
                plan.testCaseId(),
                progress
        );
        if (step.target() != null && !step.target().isBlank()) {
            elementResolver.resolve(page, SupportedActions.VERIFY, step.target());
        }
    }

    private BrowserSnapshot inspect(
            Page page,
            ExecutionPlan plan,
            Consumer<ProgressEvent> progress,
            PageDiagnostics diagnostics) {
        long started = System.nanoTime();
        TraceLogger.info("DOM", "DOM_FETCH_STARTED", "Fetching DOM snapshot", TraceMeta.of("url", page.url()));
        PageStateWatcher.waitUntilInteractive(
                page,
                () -> PageReadinessContract.countInteractive(page),
                plan.testCaseId(),
                progress
        );
        BrowserSnapshot snapshot = null;
        int attempts = 0;
        int maxAttempts = 6;
        int pollMs = 200;
        while (attempts < maxAttempts) {
            snapshot = intelligence.inspect(page, diagnostics.consoleErrors());
            if (snapshot.interactiveCount() > 0 || attempts == maxAttempts - 1) {
                break;
            }
            attempts++;
            TraceLogger.info("DOM", "DOM_FETCH_RETRY", "Retrying DOM snapshot with zero interactive elements", TraceMeta.of(
                    "attempt", attempts,
                    "url", snapshot.url(),
                    "interactiveCount", snapshot.interactiveCount()
            ));
            try {
                page.waitForFunction(
                        "() => document.querySelectorAll('a,button,input,select,textarea,[role],[data-testid]').length > 0",
                        new Page.WaitForFunctionOptions().setTimeout(pollMs));
            } catch (RuntimeException ignored) {
            }
            pollMs = Math.min(pollMs * 2, 2000);
        }
        TraceLogger.info("DOM", "DOM_FETCH_COMPLETED", "DOM snapshot fetched",
                (System.nanoTime() - started) / 1_000_000,
                DomTraceStats.summarize(snapshot.url(), snapshot.elements()));
        emitRich(progress, plan, "DOM_FETCHED", "Inspected " + snapshot.interactiveCount() + " interactive elements", Map.of(
                "url", snapshot.url(),
                "title", snapshot.title(),
                "interactiveCount", snapshot.interactiveCount(),
                "attempts", attempts + 1,
                "evidenceMomentId", nullToEmpty(snapshot.evidenceMomentId()),
                "treeVersion", nullToEmpty(snapshot.treeVersion()),
                "graphVersion", nullToEmpty(snapshot.graphVersion())
        ), 0, 0, snapshot.url(), snapshot.title(), null);
        emitRich(progress, plan, "ELEMENT_INVENTORY_BUILT", "Built element inventory", Map.of(
                "count", snapshot.elements() == null ? 0 : snapshot.elements().size(),
                "interactiveCount", snapshot.interactiveCount()
        ), 0, 0, snapshot.url(), snapshot.title(), null);
        emitRich(progress, plan, "ELEMENT_TREE_BUILT", "Built element tree", Map.of(
                "nodes", snapshot.treeOrBuild().nodes() == null ? 0 : snapshot.treeOrBuild().nodes().size()
        ), 0, 0, snapshot.url(), snapshot.title(), null);
        emitRich(progress, plan, "ELEMENT_GRAPH_BUILT", "Built relationship graph", Map.of(
                "edges", snapshot.graphOrBuild().edges() == null ? 0 : snapshot.graphOrBuild().edges().size()
        ), 0, 0, snapshot.url(), snapshot.title(), null);
        emitRich(progress, plan, "TREE_GRAPH_RECONCILED", "Reconciled tree and graph", Map.of(
                "evidenceMomentId", nullToEmpty(snapshot.evidenceMomentId())
        ), 0, 0, snapshot.url(), snapshot.title(), null);
        return snapshot;
    }

    private String resolveUrl(String baseUrl, ExecutionPlan.PlannedStep step) {
        if (looksLikeUrl(step.value())) {
            return step.value();
        }
        if (looksLikeUrl(step.target())) {
            return step.target();
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new SmartQaException(ErrorCode.INTENT_INVALID, "No URL available for navigate");
        }
        return baseUrl;
    }

    private static boolean looksLikeUrl(String value) {
        return value != null && (value.startsWith("http://") || value.startsWith("https://"));
    }

    private LocatorMemoryEntry memory(
            ExecutionPlan.PlannedStep step,
            String locator,
            String locatorType,
            double confidence,
            Page page,
            boolean healed,
            String text,
            String locatorCloud) {
        return memory(step, locator, locatorType, confidence, page, healed, text, locatorCloud, null);
    }

    private LocatorMemoryEntry memory(
            ExecutionPlan.PlannedStep step,
            String locator,
            String locatorType,
            double confidence,
            Page page,
            boolean healed,
            String text,
            String locatorCloud,
            ControlType controlType) {
        return memory(step, locator, locatorType, confidence, page, healed, text, locatorCloud, controlType, null);
    }

    private LocatorMemoryEntry memory(
            ExecutionPlan.PlannedStep step,
            String locator,
            String locatorType,
            double confidence,
            Page page,
            boolean healed,
            String text,
            String locatorCloud,
            ControlType controlType,
            ElementResolver.ResolvedElement resolved) {
        return new LocatorMemoryEntry(
                step.id(),
                step.action(),
                step.target(),
                locator,
                locatorType,
                confidence,
                text,
                null,
                page.url(),
                healed,
                step.value(),
                null,
                locatorCloud,
                controlType != null ? controlType.name() : null,
                resolved == null ? "main" : nullToEmpty(resolved.frameContext()),
                resolved == null ? "" : nullToEmpty(resolved.frameUrl()),
                resolved == null ? "" : nullToEmpty(resolved.frameName()),
                resolved == null ? "" : nullToEmpty(resolved.parentFrameContext()),
                resolved == null ? "" : nullToEmpty(resolved.targetPath())
        );
    }

    private static final Duration HEALING_TIMEOUT = Duration.ofSeconds(30);

    private ElementResolver.ResolvedElement resolveWithHealing(
            Page page,
            ExecutionPlan plan,
            ExecutionPlan.PlannedStep step,
            Consumer<ProgressEvent> progress,
            PageDiagnostics diagnostics,
            Map<String, LocatorMemoryEntry> known) {
        ElementResolver.ResolvedElement tableScoped = tryTableScopedResolution(page, step);
        if (tableScoped != null) {
            return tableScoped;
        }
        BrowserSnapshot snapshot = inspect(page, plan, progress, diagnostics);
        LocatorMemoryEntry previous = known.get(key(step.action(), step.target()));
        if (previous != null && previous.resolvedLocator() != null) {
            java.util.Optional<ElementResolver.ResolvedElement> verified = elementResolver.verifyKnownLocator(
                    page, previous.resolvedLocator(), previous.locatorType());
            if (verified.isPresent()) {
                return verified.get();
            }
        }
        RuntimeExecutionContext.bind(plan.testCaseId(), plan.executionRunId(), step.id());
        try {
            return elementResolver.resolve(page, step.action(), step.target(), step.location(), snapshot, step.containerContext());
        } catch (SmartQaException firstResolveError) {
            if (firstResolveError.errorCode() == ErrorCode.CLARIFICATION_REQUIRED) {
                throw firstResolveError;
            }
            if (tryRecoverWrongPage(page, plan, step, progress, diagnostics, firstResolveError)) {
                BrowserSnapshot recovered = inspect(page, plan, progress, diagnostics);
                return elementResolver.resolve(page, step.action(), step.target(), step.location(), recovered, step.containerContext());
            }
            // Generic WAIT_STATE recovery: menus/popovers often appear after the previous click.
            // Fresh DOM once before healing — no site-specific selectors.
            try {
                SafeClick.settle(page);
                com.smartqa.browser.intelligence.PageReadinessContract.boundedMicroSettle(page, 450);
                PageStateWatcher.waitUntilInteractive(
                        page,
                        () -> PageReadinessContract.countInteractive(page),
                        plan.testCaseId(),
                        progress
                );
                BrowserSnapshot fresh = inspect(page, plan, progress, diagnostics);
                TraceLogger.info("LOCATOR", "DOM_REDISCOVERY", "Re-resolving after post-action DOM wait", TraceMeta.of(
                        "stepId", step.id(),
                        "target", step.target(),
                        "interactiveCount", fresh.interactiveCount()
                ));
                return elementResolver.resolve(page, step.action(), step.target(), step.location(), fresh, step.containerContext());
            } catch (RuntimeException ignored) {
                // fall through to healing
            }
            emit(progress, plan, "HEALING_STARTED", "Trying alternative locators for " + step.target(), Map.of("stepId", step.id()));
            TraceLogger.info("LOCATOR", "HEALING_STARTED", "Locator healing started", TraceMeta.of(
                    "stepId", step.id(),
                    "originalTarget", step.target(),
                    "originalLocator", previous == null ? null : previous.resolvedLocator()
            ));
            log.info("healing_attempted testCaseId={} stepId={}", plan.testCaseId(), step.id());
            if (previous != null && previous.locatorCloud() != null && !previous.locatorCloud().isBlank()) {
                java.util.Optional<LocatorHealingResolver.Hit> cloudHit =
                        LocatorHealingResolver.firstHit(page, previous.locatorCloud());
                if (cloudHit.isPresent()) {
                    LocatorHealingResolver.Hit hit = cloudHit.get();
                    return new ElementResolver.ResolvedElement(
                            hit.type(), hit.value(), Math.max(0.6, hit.confidence()), true, hit.locator(),
                            previous.locatorCloud());
                }
            }
            try {
                ElementResolver.ResolvedElement healed = elementResolver.heal(
                        page,
                        step.action(),
                        step.target(),
                        previous == null ? null : previous.resolvedLocator(),
                        previous == null ? "css" : previous.locatorType(),
                        HEALING_TIMEOUT
                );
                TraceLogger.info("LOCATOR", "HEALING_COMPLETED", "Locator healing succeeded", TraceMeta.of(
                        "stepId", step.id(),
                        "newLocator", healed.resolvedLocator(),
                        "locatorType", healed.locatorType(),
                        "confidence", healed.confidence(),
                        "status", "SUCCESS"
                ));
                emit(progress, plan, "HEALING_SUCCESS", "Healed locator for " + step.target(), Map.of("stepId", step.id()));
                emit(progress, plan, "HEALING_COMPLETED", "Healed locator for " + step.target(), Map.of("stepId", step.id()));
                return healed;
            } catch (RuntimeException healError) {
                TraceLogger.error("LOCATOR", "HEALING_FAILED", "Locator healing failed", healError, null, TraceMeta.of(
                        "stepId", step.id(),
                        "target", step.target()
                ));
                ElementResolver.ResolvedElement probed = tryOutcomeProbe(page, step);
                if (probed != null) {
                    return probed;
                }
                throw firstResolveError;
            }
        } finally {
            RuntimeExecutionContext.clear();
        }
    }

    private ElementResolver.ResolvedElement tryOutcomeProbe(Page page, ExecutionPlan.PlannedStep step) {
        if (page == null || step == null || step.expectedState() == null || step.expectedState().isBlank()
                || step.target() == null || step.target().isBlank()) {
            return null;
        }
        Locator candidate = page.getByText(step.target());
        Locator probed = OutcomeProbeResolver.resolve(
                page,
                List.of(new OutcomeProbeResolver.Probe(candidate, step.target())),
                step.expectedState());
        if (probed == null) {
            return null;
        }
        TraceLogger.info("LOCATOR", "OUTCOME_PROBE_RESOLVED", "Resolved via outcome probe", TraceMeta.of(
                "stepId", step.id(),
                "target", step.target(),
                "expectedState", step.expectedState()
        ));
        return new ElementResolver.ResolvedElement("text", step.target(), 0.62, true, probed, null);
    }

    private ElementResolver.ResolvedElement tryTableScopedResolution(Page page, ExecutionPlan.PlannedStep step) {
        if (!SupportedActions.CLICK.equalsIgnoreCase(step.action()) || step.target() == null) {
            return null;
        }
        String target = step.target().trim();
        int idx = target.toLowerCase(Locale.ROOT).indexOf(" for ");
        if (idx <= 0 || idx >= target.length() - 5) {
            return null;
        }
        String actionPart = target.substring(0, idx).trim();
        String rowPart = target.substring(idx + 5).trim();
        if (actionPart.isBlank() || rowPart.isBlank()) {
            return null;
        }
        try {
            Locator rowAction = page.locator("tr", new Page.LocatorOptions().setHasText(rowPart))
                    .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(actionPart));
            if (rowAction.count() == 1 && rowAction.first().isVisible() && rowAction.first().isEnabled()) {
                return new ElementResolver.ResolvedElement("role", "table-row:" + rowPart + " -> button:" + actionPart,
                        0.92, false, rowAction.first(), "table-scoped", ControlType.BUTTON,
                        "main", "", "", "", "");
            }
        } catch (RuntimeException ignored) {
        }
        try {
            Locator rowAction = page.locator("tr", new Page.LocatorOptions().setHasText(rowPart)).getByText(actionPart);
            if (rowAction.count() == 1 && rowAction.first().isVisible() && rowAction.first().isEnabled()) {
                return new ElementResolver.ResolvedElement("text", "table-row:" + rowPart + " -> text:" + actionPart,
                        0.86, false, rowAction.first(), "table-scoped", ControlType.BUTTON,
                        "main", "", "", "", "");
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    private void highlightElement(Page page, Locator locator) {
        try {
            locator.evaluate("el => { el.__smartqa_outline = el.style.outline; el.style.outline = '3px solid #2563eb'; }");
        } catch (RuntimeException ignored) {
        }
    }

    private void removeHighlight(Page page) {
        try {
            page.evaluate("() => { const el = document.querySelector('[style*=\"3px solid\"]'); if(el) el.style.outline = el.__smartqa_outline || ''; }");
        } catch (RuntimeException ignored) {
        }
    }

    private void checkCancellation(CancellationToken token) {
        if (token != null) {
            token.throwIfStopped();
        }
    }

    private void storeEvent(String traceId, ExecutionPlan plan, EventType eventType, EventComponent component,
                            String message, String stepId, int stepNumber, String action, String target,
                            String locator, String controlType, long durationMs, String errorMessage) {
        ExecutionEvent event = ExecutionEvent.builder()
                .traceId(traceId)
                .testCaseId(plan.testCaseId())
                .stepId(stepId)
                .stepNumber(stepNumber)
                .component(component)
                .eventType(eventType)
                .message(message)
                .action(action)
                .target(target)
                .locator(locator)
                .controlType(controlType)
                .durationMs(durationMs)
                .errorMessage(errorMessage)
                .executionProvider(PROVIDER_ID)
                .build();
        eventStore.add(traceId, event);
    }

    private void emit(Consumer<ProgressEvent> progress, ExecutionPlan plan, String type, String message) {
        emit(progress, plan, type, message, Map.of());
    }

    private void emit(
            Consumer<ProgressEvent> progress,
            ExecutionPlan plan,
            String type,
            String message,
            Map<String, Object> details) {
        if (progress != null) {
            progress.accept(ProgressEvent.generation(type, message, plan.testCaseId(), details));
        }
    }

    private void emitRich(
            Consumer<ProgressEvent> progress,
            ExecutionPlan plan,
            String type,
            String message,
            Map<String, Object> details,
            int stepNumber,
            int totalSteps,
            String currentUrl,
            String pageTitle,
            String screenshotId) {
        if (progress != null) {
            Map<String, Object> payload = new LinkedHashMap<>();
            if (details != null) {
                payload.putAll(details);
            }
            UUID screenshotRun = screenshotRunId(plan);
            payload.putIfAbsent("traceId", TraceContext.current());
            payload.putIfAbsent("runId", screenshotRun == null ? "" : screenshotRun.toString());
            payload.putIfAbsent("stepNumber", stepNumber);
            payload.putIfAbsent("totalSteps", totalSteps);
            payload.putIfAbsent("action", payload.getOrDefault("action", ""));
            payload.putIfAbsent("target", payload.getOrDefault("target", ""));
            payload.putIfAbsent("currentUrl", currentUrl == null ? "" : currentUrl);
            payload.putIfAbsent("pageTitle", pageTitle == null ? "" : pageTitle);
            payload.putIfAbsent("locator", payload.getOrDefault("locator", ""));
            payload.putIfAbsent("confidence", payload.getOrDefault("confidence", 0.0));
            payload.putIfAbsent("executionProvider", PROVIDER_ID);
            progress.accept(ProgressEvent.rich(type, message, plan.testCaseId(), screenshotRun, payload,
                    stepNumber, totalSteps, currentUrl, pageTitle, PROVIDER_ID, screenshotId));
        }
    }

    private UUID screenshotRunId(ExecutionPlan plan) {
        if (plan != null && plan.executionRunId() != null) {
            return plan.executionRunId();
        }
        return plan == null ? null : plan.testCaseId();
    }

    private String captureShot(Page page, ExecutionPlan plan, String stepId, int stepNumber, String eventType) {
        return screenshotService.capture(
                page,
                TraceContext.current(),
                screenshotRunId(plan),
                stepId,
                stepNumber,
                eventType,
                safeUrl(page),
                ACTIVE_EVIDENCE_MOMENT.get());
    }

    static boolean shouldRememberSuccess(ExecutionPlan.PlannedStep step, LocatorMemoryEntry entry) {
        if (step == null || entry == null) {
            return false;
        }
        String assertion = step.assertion();
        if (assertion != null && !assertion.isBlank()
                && !"verify".equalsIgnoreCase(nullToEmpty(step.action()))) {
            return false;
        }
        return entry.resolvedLocator() != null || "navigate".equalsIgnoreCase(nullToEmpty(step.action()))
                || "verify".equalsIgnoreCase(nullToEmpty(step.action()));
    }

    private static String key(String action, String target) {
        return (action == null ? "" : action) + "|" + (target == null ? "" : target);
    }

    private static String safeText(Locator locator) {
        try {
            String text = locator.innerText(new Locator.InnerTextOptions().setTimeout(1000));
            return text == null ? "" : text.trim();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static String safeUrl(Page page) {
        try {
            return page.url();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static String safeTitle(Page page) {
        try {
            return page.title();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static boolean looksLikeSidebarLocation(String location) {
        String hint = LocationHint.normalize(location);
        return LocationHint.SIDEBAR_LEFT.equals(hint) || LocationHint.SIDEBAR_RIGHT.equals(hint);
    }

    private static boolean looksLikeNavigatingControl(ControlType controlType) {
        return controlType == ControlType.LINK
                || controlType == ControlType.MENU
                || controlType == ControlType.MENU_BUTTON
                || controlType == ControlType.TAB;
    }

    private static Locator firstNavLocator(Page page, String target) {
        if (page == null || target == null || target.isBlank()) {
            return null;
        }
        Locator link = firstCompactVisible(page.getByRole(
                AriaRole.LINK, new Page.GetByRoleOptions().setName(target)));
        if (link != null) {
            return link;
        }
        return firstCompactVisible(page.getByRole(
                AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(target)));
    }

    private static void waitForPossibleNavigation(Page page, String urlBefore) {
        if (page == null || urlBefore == null || urlBefore.isBlank()) {
            return;
        }
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            String url = safeUrl(page);
            if (!url.isBlank() && !url.equalsIgnoreCase(urlBefore)) {
                try {
                    page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED,
                            new Page.WaitForLoadStateOptions().setTimeout(8000));
                } catch (RuntimeException ignored) {
                }
                return;
            }
            com.smartqa.browser.intelligence.PageReadinessContract.boundedMicroSettle(page, 250);
        }
    }

    private static void waitForResultSurface(Page page) {
        try {
            Locator tableBody = page.locator("[role='table'] [role='rowgroup'], table tbody, [class*='table'] [class*='body']");
            if (tableBody.count() > 0) {
                tableBody.first().waitFor(new Locator.WaitForOptions().setTimeout(8000));
            }
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(5000));
        } catch (RuntimeException ignored) {
        }
    }

    private LocatorMemoryEntry verifyVisibleText(
            Page page,
            ExecutionPlan plan,
            ExecutionPlan.PlannedStep step,
            Consumer<ProgressEvent> progress) {
        String expected = VerifyExpectation.expectedText(step.assertion(), step.value());
        if (!VerifyExpectation.isSpecificExpectedText(expected)
                && VerifyExpectation.isSpecificExpectedText(step.target())
                && !VerifyExpectation.isPageLevelTarget(step.target())) {
            expected = step.target().trim();
        }
        if (!VerifyExpectation.isSpecificExpectedText(expected)) {
            return null;
        }
        TraceLogger.info("ASSERTION", "ASSERTION_STARTED", "Assertion started", TraceMeta.of(
                "stepId", step.id(), "target", step.target(), "expectedText", expected));
        TraceLogger.info("ASSERTION", "ASSERTION_EXPECTED", "Expected assertion text", TraceMeta.of(
                "expectedText", expected));
        TraceLogger.info("ASSERTION", "VERIFY_STARTED", "Verifying visible text", TraceMeta.of(
                "expectedText", expected, "stepId", step.id()));
        long deadline = System.nanoTime() + 30_000_000_000L;
        try {
            for (String variant : VerifyExpectation.textVariants(expected)) {
                try {
                    page.getByText(variant, new Page.GetByTextOptions().setExact(true))
                            .first()
                            .waitFor(new Locator.WaitForOptions().setTimeout(2500));
                    break;
                } catch (RuntimeException ignored) {
                }
            }
        } catch (RuntimeException ignored) {
        }
        while (System.nanoTime() < deadline) {
            LocatorMemoryEntry result = checkVisibleTextOnce(page, plan, step, progress, expected);
            if (result != null) {
                TraceLogger.info("ASSERTION", "VERIFY_COMPLETED", "Text verified", TraceMeta.of(
                        "expectedText", expected,
                        "actualText", expected,
                        "visible", true,
                        "url", safeUrl(page),
                        "pageTitle", safeTitle(page),
                        "durationMs", (deadline - System.nanoTime()) / 1_000_000
                ));
                return result;
            }
            try {
                com.smartqa.browser.intelligence.PageReadinessContract.boundedMicroSettle(page, 500);
            } catch (RuntimeException ignored) {
            }
        }
        TraceLogger.warn("ASSERTION", "VERIFY_FAILED",
                "Expected text not visible within timeout",
                TraceMeta.of("expectedText", expected, "timeoutMs", 30000,
                        "currentUrl", safeUrl(page), "pageTitle", safeTitle(page)));
        TraceLogger.warn("ASSERTION", "ASSERTION_FAILED",
                "Assertion failed",
                TraceMeta.of("expectedText", expected, "url", safeUrl(page), "pageTitle", safeTitle(page)));
        return null;
    }

    private LocatorMemoryEntry checkVisibleTextOnce(
            Page page,
            ExecutionPlan plan,
            ExecutionPlan.PlannedStep step,
            Consumer<ProgressEvent> progress,
            String expected) {
        VerifyExpectation.RecordOutcome outcome = VerifyExpectation.recordOutcome(expected);
        if (outcome == VerifyExpectation.RecordOutcome.PRESENT) {
            if (hasNoRecordsMessage(page)) {
                return null;
            }
            Locator records = firstVisibleRecordsEvidence(page);
            if (records != null) {
                emitAssertionEvidence(page, plan, step, progress, expected, records,
                        "records-present", "outcome");
                return memory(step, "records-present", "outcome", 0.93, page, false, expected, null);
            }
            return null;
        }
        if (outcome == VerifyExpectation.RecordOutcome.ABSENT) {
            Locator empty = firstVisibleNoRecordsEvidence(page);
            if (empty != null) {
                emitAssertionEvidence(page, plan, step, progress, expected, empty,
                        "records-absent", "outcome");
                return memory(step, "records-absent", "outcome", 0.93, page, false, expected, null);
            }
            return null;
        }
        for (String variant : VerifyExpectation.textVariants(expected)) {
            try {
                Locator heading = page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(variant));
                if (heading.count() >= 1 && heading.first().isVisible()) {
                    emitAssertionEvidence(page, plan, step, progress, expected, heading.first(), "heading|" + variant, "role");
                    return memory(step, "heading|" + variant, "role", 0.98, page, false, expected, null);
                }
            } catch (RuntimeException ignored) {
            }
            try {
                Locator text = page.getByText(variant);
                for (int i = 0; i < text.count(); i++) {
                    if (text.nth(i).isVisible()) {
                        emitAssertionEvidence(page, plan, step, progress, expected, text.nth(i), variant, "text");
                        return memory(step, expected, "text", 0.95, page, false, variant, null);
                    }
                }
            } catch (RuntimeException ignored) {
            }
        }
        try {
            String body = page.locator("body").innerText();
            if (body != null) {
                String lower = body.toLowerCase(Locale.ROOT);
                for (String variant : VerifyExpectation.textVariants(expected)) {
                    if (lower.contains(variant.toLowerCase(Locale.ROOT))) {
                        emitAssertionEvidence(page, plan, step, progress, expected, page.locator("body"), variant, "text");
                        return memory(step, expected, "text", 0.9, page, false, variant, null);
                    }
                }
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private void emitAssertionEvidence(
            Page page,
            ExecutionPlan plan,
            ExecutionPlan.PlannedStep step,
            Consumer<ProgressEvent> progress,
            String expected,
            Locator element,
            String locatorValue,
            String locatorType) {
        String actual = safeText(element);
        boolean visible = safeVisible(element);
        String url = safeUrl(page);
        String title = safeTitle(page);
        String screenshotId = captureShot(page, plan, step.id(), 0, "ASSERTION_PASSED");
        TraceLogger.info("LOCATOR", "LOCATOR_SELECTED", "Verified visible text", TraceMeta.of(
                "locator", locatorValue,
                "type", locatorType,
                "verified", true
        ));
        TraceLogger.info("ASSERTION", "ASSERTION_ACTUAL", "Actual assertion text", TraceMeta.of("actualText", actual));
        TraceLogger.info("ASSERTION", "ASSERTION_ELEMENT", "Assertion element", TraceMeta.of(
                "tag", safeTag(element), "visible", visible, "enabled", safeEnabled(element)));
        TraceLogger.info("ASSERTION", "ASSERTION_LOCATOR", "Assertion locator", TraceMeta.of(
                "locator", locatorValue, "locatorType", locatorType));
        TraceLogger.info("ASSERTION", "ASSERTION_VISIBILITY", "Assertion visibility", TraceMeta.of("visible", visible));
        TraceLogger.info("ASSERTION", "ASSERTION_URL", "Assertion page URL", TraceMeta.of("url", url));
        TraceLogger.info("ASSERTION", "ASSERTION_PAGE_TITLE", "Assertion page title", TraceMeta.of("pageTitle", title));
        TraceLogger.info("ASSERTION", "ASSERTION_SCREENSHOT", "Assertion screenshot", TraceMeta.of("screenshotId", screenshotId));
        TraceLogger.info("ASSERTION", "ASSERTION_PASSED", "Assertion passed", TraceMeta.of(
                "expectedText", expected, "actualText", actual, "visible", visible));
        emitRich(progress, plan, "ASSERTION_PASSED", "Verified text: " + expected,
                Map.of("stepId", step.id()), 0, 0, url, title, screenshotId);
    }

    private static void waitForAuthMenuIfNeeded(Page page, ExecutionPlan.PlannedStep step) {
        if (page == null || step == null || step.target() == null) {
            return;
        }
        String target = step.target().toLowerCase(Locale.ROOT);
        // Only true auth-entry icons — not Username/User Role/menu items mid-flow.
        boolean authEntry = target.contains("profile")
                || target.contains("avatar")
                || target.contains("my account")
                || (target.contains("account") && (target.contains("icon") || target.contains("button")))
                || (target.contains("user") && target.contains("icon"));
        if (!authEntry || !"click".equalsIgnoreCase(step.action())) {
            return;
        }
        TraceLogger.info("BROWSER", "STATE_WAIT", "Waiting for auth menu after entry click", TraceMeta.of(
                "target", step.target()
        ));
        String beforeUrl = safeUrl(page);
        long started = System.nanoTime();
        long deadline = started + 8_000_000_000L;
        while (System.nanoTime() < deadline) {
            try {
                String url = safeUrl(page);
                if (looksLikeCartNavigation(beforeUrl, url)) {
                    TraceLogger.warn("BROWSER", "AUTH_ENTRY_WRONG_TARGET", "Auth entry navigated to cart", TraceMeta.of(
                            "beforeUrl", beforeUrl,
                            "url", url,
                            "target", step.target()
                    ));
                    try {
                        page.goBack(new Page.GoBackOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
                    } catch (RuntimeException ignored) {
                    }
                    throw new SmartQaException(ErrorCode.ACTIONABILITY_FAILURE,
                            "Auth entry click navigated to cart instead of exposing Login/Sign in");
                }
                boolean screenChanged = url.contains("screen=") && !url.equals(beforeUrl);
                Locator loginish = page.getByText(java.util.regex.Pattern.compile(
                        "^\\s*(Log\\s*in|Sign\\s*in|Login)\\s*$",
                        java.util.regex.Pattern.CASE_INSENSITIVE));
                Locator actionable = firstCompactVisible(loginish);
                if (actionable == null) {
                    // Soft match for login copy nested in short labels.
                    actionable = firstCompactVisible(page.getByText(
                            java.util.regex.Pattern.compile("\\b(Log\\s*in|Sign\\s*in|Login)\\b",
                                    java.util.regex.Pattern.CASE_INSENSITIVE)));
                }
                long waitedMs = (System.nanoTime() - started) / 1_000_000L;
                boolean drawerReady = actionable != null && waitedMs >= 800;
                if ((actionable != null && (screenChanged || looksLikeAuthOverlay(page) || drawerReady))
                        || (screenChanged && waitedMs >= 1200)) {
                    TraceLogger.info("BROWSER", "STATE_CHANGE_DETECTED", "Auth menu/control became visible", TraceMeta.of(
                            "matched", actionable == null ? "screen-param" : "login-or-signin",
                            "url", url,
                            "screenChanged", screenChanged,
                            "waitedMs", waitedMs
                    ));
                    return;
                }
            } catch (SmartQaException ex) {
                throw ex;
            } catch (RuntimeException ignored) {
            }
            com.smartqa.browser.intelligence.PageReadinessContract.boundedMicroSettle(page, 200);
        }
        TraceLogger.warn("BROWSER", "STATE_WAIT_TIMEOUT", "Auth menu did not expose Login/Sign in", TraceMeta.of(
                "target", step.target(),
                "url", safeUrl(page)
        ));
        throw new SmartQaException(ErrorCode.ACTIONABILITY_FAILURE,
                "Auth entry click did not expose Login/Sign in control | url=" + safeUrl(page));
    }

    private static boolean looksLikeCartNavigation(String beforeUrl, String afterUrl) {
        if (afterUrl == null || afterUrl.isBlank()) {
            return false;
        }
        String lower = afterUrl.toLowerCase(Locale.ROOT);
        if (!(lower.contains("/cart") || lower.contains("/bag") || lower.contains("/basket"))) {
            return false;
        }
        if (beforeUrl != null && beforeUrl.equalsIgnoreCase(afterUrl)) {
            return false;
        }
        return true;
    }

    private static boolean hasNoRecordsMessage(Page page) {
        return firstVisibleNoRecordsEvidence(page) != null;
    }

    private static Locator firstVisibleNoRecordsEvidence(Page page) {
        try {
            Locator empty = page.getByText(Pattern.compile(
                    "(?i)no records found|no matching records|no results found|no matching record"));
            if (empty.count() > 0 && empty.first().isVisible()) {
                return empty.first();
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    private static Locator firstVisibleRecordsEvidence(Page page) {
        try {
            Locator counted = page.getByText(Pattern.compile("(?i)records? found"));
            int n = Math.min(counted.count(), 8);
            for (int i = 0; i < n; i++) {
                Locator item = counted.nth(i);
                if (!item.isVisible()) {
                    continue;
                }
                String text = safeText(item).toLowerCase(Locale.ROOT);
                if (!text.contains("no record")) {
                    return item;
                }
            }
        } catch (RuntimeException ignored) {
        }
        try {
            Locator rows = page.locator("table tbody tr, [role='rowgroup'] [role='row'], [role='listitem']");
            int n = Math.min(rows.count(), 24);
            for (int i = 0; i < n; i++) {
                Locator row = rows.nth(i);
                if (!row.isVisible()) {
                    continue;
                }
                String text = safeText(row);
                if (text == null || text.isBlank()) {
                    continue;
                }
                String lower = text.toLowerCase(Locale.ROOT);
                if (lower.contains("no record") || lower.contains("no result")) {
                    continue;
                }
                if (looksLikeHeaderRow(lower)) {
                    continue;
                }
                return row;
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    private static boolean looksLikeHeaderRow(String lower) {
        return lower.equals("id")
                || lower.contains("first name")
                || lower.contains("last name")
                || (lower.contains("employee") && lower.contains("name") && !lower.contains(" "));
    }

    private static Locator firstCompactVisible(Locator locator) {
        if (locator == null) {
            return null;
        }
        try {
            int count = Math.min(locator.count(), 12);
            for (int i = 0; i < count; i++) {
                Locator item = locator.nth(i);
                if (!item.isVisible()) {
                    continue;
                }
                var box = item.boundingBox();
                if (box == null) {
                    continue;
                }
                String tag = safeTag(item);
                if (tag.matches("h[1-6]")) {
                    continue;
                }
                // Reject full-page wrappers that merely contain "Login" in footer copy.
                if (box.width > 0 && box.height > 0 && box.width * box.height < 80_000) {
                    return item;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    private static boolean looksLikeAuthOverlay(Page page) {
        try {
            Object raw = page.evaluate("""
                    () => {
                      const dialogs = document.querySelectorAll('[role="dialog"], [aria-modal="true"], dialog[open]').length;
                      const screen = (window.location.search || '').includes('screen=');
                      return dialogs > 0 || screen;
                    }
                    """);
            return raw instanceof Boolean b && b;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private SmartQaException assertionTruthFailure(Page page, ExecutionPlan plan, String expected, String hostNote) {
        String actual = safeBody(page);
        String url = safeUrl(page);
        String title = safeTitle(page);
        AssertionTruthEngine.Verdict verdict = AssertionTruthEngine.evaluate(expected, actual, url, title);
        TraceLogger.warn("ASSERTION", "ASSERTION_TRUTH", "Assertion truth classified", TraceMeta.of(
                "outcome", verdict.outcome().name(),
                "expected", expected == null ? "" : expected,
                "url", url,
                "title", title
        ));
        ErrorCode code = switch (verdict.outcome()) {
            case BUSINESS_STATE_MISMATCH -> ErrorCode.BUSINESS_STATE_MISMATCH;
            case LOGIN_STATE_FAILURE -> ErrorCode.LOGIN_STATE_FAILURE;
            case WRONG_PAGE, NOT_REACHED -> ErrorCode.WRONG_PAGE;
            default -> ErrorCode.ASSERTION_FAILED;
        };
        String message = verdict.userMessage() + (hostNote == null ? "" : hostNote);
        if (plan != null && HostContextGuard.leftApplication(plan.baseUrl(), url)) {
            return new SmartQaException(ErrorCode.WRONG_PAGE,
                    "WRONG_PAGE: expected application host was left.\n" + verdict.userMessage());
        }
        return new SmartQaException(code, message);
    }

    private static void confirmLoginState(Page page) {
        long deadline = System.currentTimeMillis() + 45_000;
        while (System.currentTimeMillis() < deadline) {
            String url = safeUrl(page).toLowerCase(Locale.ROOT);
            if (!AssertionTruthEngine.looksLikeLoginUrl(url)) {
                TraceLogger.info("PLAYWRIGHT", "LOGIN_STATE_CHANGED", "Left login URL after login click",
                        TraceMeta.of("url", safeUrl(page)));
                return;
            }
            if (authenticationRejected(page)) {
                TraceLogger.info("PLAYWRIGHT", "LOGIN_REJECTED",
                        "Login click stayed on login page with an authentication or validation error",
                        TraceMeta.of("url", safeUrl(page)));
                return;
            }
            try {
                if (page.getByText("Dashboard", new Page.GetByTextOptions().setExact(true)).count() > 0
                        && page.getByText("Dashboard", new Page.GetByTextOptions().setExact(true)).first().isVisible()) {
                    return;
                }
            } catch (RuntimeException ignored) {
            }
            com.smartqa.browser.intelligence.PageReadinessContract.boundedMicroSettle(page, 250);
        }
        if (authenticationRejected(page)) {
            return;
        }
        if (AssertionTruthEngine.looksLikeLoginUrl(safeUrl(page))) {
            throw new SmartQaException(ErrorCode.LOGIN_STATE_FAILURE,
                    "LOGIN_STATE_FAILURE: Login click completed but URL remains " + safeUrl(page));
        }
    }

    /**
     * Generic negative-auth outcome: invalid credentials, required field, locked account.
     * Used so Login click is not forced to leave the login page on expected failures.
     */
    private static boolean authenticationRejected(Page page) {
        if (page == null) {
            return false;
        }
        try {
            Object raw = page.evaluate("""
                    () => {
                      const text = ((document.body && document.body.innerText) || '').toLowerCase();
                      const errorEl = document.querySelector(
                        '[role="alert"], .error, .toast-error, [class*="error"], [aria-live="assertive"]'
                      );
                      const visible = !!(errorEl && errorEl.offsetParent !== null);
                      if (!visible) {
                        return false;
                      }
                      return /invalid (credential|username|password|login|user)/.test(text)
                        || /authentication failed|login failed|account locked|access denied/.test(text)
                        || /\\brequired\\b/.test(text)
                        || /\\binvalid\\b/.test(text);
                    }
                    """);
            return Boolean.TRUE.equals(raw);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static String safeBody(Page page) {
        try {
            return page.locator("body").innerText();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static void clickWithActionability(Locator locator, Page page, ExecutionPlan.PlannedStep step) {
        RecoveryEngine.withRetry(page, () -> {
            Locator target = SafeClick.preferActionableTarget(locator);
            if (page != null) {
                try {
                    target.scrollIntoViewIfNeeded();
                } catch (RuntimeException ignored) {
                    // continue
                }
                // Auth menu/drawer is often a large modal; dismissing "overlays" would close Login.
                boolean authSurfaceClick = step != null && "LOGIN".equals(clickIntentPrefix(step.target()));
                if (!authSurfaceClick) {
                    BlockingOverlayGuard.dismissConsentBanners(page);
                    BlockingOverlayGuard.dismissIfBlocking(page);
                }
            }
            ActionabilityVerifier.Result check = ActionabilityVerifier.verify(target, SupportedActions.CLICK);
            if (!check.ok() && target != locator) {
                // Promoted ancestor can be stale/empty for text locators — fall back to original.
                TraceLogger.warn("PLAYWRIGHT", "CLICK_PROMOTE_REVERTED",
                        "Promoted click target not actionable; using original locator",
                        TraceMeta.of("reason", check.reason() == null ? "" : check.reason()));
                target = locator;
                check = ActionabilityVerifier.verify(target, SupportedActions.CLICK);
            }
            emitActionabilityProgress(check, step);
            if (!check.ok()) {
                if (check.covered() && page != null) {
                    BlockingOverlayGuard.dismissCoveringElement(page, target);
                    BlockingOverlayGuard.dismissIfBlocking(page);
                    try {
                        target.scrollIntoViewIfNeeded();
                    } catch (RuntimeException ignored) {
                        // continue
                    }
                    com.smartqa.browser.intelligence.PageReadinessContract.boundedMicroSettle(page, 200);
                    ActionabilityVerifier.Result retry = ActionabilityVerifier.verify(target, SupportedActions.CLICK);
                    if (!retry.ok()) {
                        throw new SmartQaException(ErrorCode.ACTIONABILITY_FAILURE,
                                "Actionability failed after overlay dismiss: " + retry.reason());
                    }
                } else {
                    throw new SmartQaException(ErrorCode.ACTIONABILITY_FAILURE,
                            "Actionability failed: " + check.reason());
                }
            }
            // Avoid double-promotion inside SafeClick — target already resolved/verified.
            target.click(new Locator.ClickOptions().setNoWaitAfter(true).setTimeout(15_000));
            SafeClick.settle(page);
            return null;
        });
    }

    private static void emitActionabilityProgress(ActionabilityVerifier.Result check, ExecutionPlan.PlannedStep step) {
        TraceLogger.info("PLAYWRIGHT", "ACTIONABILITY_CHECK", "Actionability result", TraceMeta.of(
                "stepId", step == null ? "" : step.id(),
                "ok", check.ok(),
                "visible", check.visible(),
                "enabled", check.enabled(),
                "covered", check.covered(),
                "inViewport", check.inViewport(),
                "reason", check.reason() == null ? "" : check.reason()
        ));
    }

    /**
     * Prefer an actionable auth submit control (button/submit) over a heading or
     * other static text that happens to say "Login". getByText("Login") often matches
     * the page title first and never submits the form.
     */
    static Locator refreshCompactAuthLocator(Page page, String target) {
        if (page == null) {
            return null;
        }
        List<String> names = new ArrayList<>();
        if (target != null && !target.isBlank()) {
            names.add(target.trim());
        }
        for (String name : List.of("Login", "Log in", "Sign in", "Sign In", "Log In")) {
            if (names.stream().noneMatch(existing -> existing.equalsIgnoreCase(name))) {
                names.add(name);
            }
        }
        for (String name : names) {
            Locator roleButton = firstCompactVisible(page.getByRole(
                    AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name)));
            if (roleButton != null) {
                return roleButton;
            }
            Locator submit = firstCompactVisible(page.locator("input[type='submit']")
                    .filter(new Locator.FilterOptions().setHasText(name)));
            if (submit != null) {
                return submit;
            }
        }
        for (String name : names) {
            Locator text = firstCompactVisible(page.getByText(name, new Page.GetByTextOptions().setExact(true)));
            if (text != null) {
                return text;
            }
        }
        return null;
    }

    private static String clickIntentPrefix(String target) {
        if (target == null || target.isBlank()) {
            return null;
        }
        String hint = target.toLowerCase(Locale.ROOT).trim();
        if (hint.contains("profile") || hint.contains("account") || hint.contains("user")) {
            return "PROFILE";
        }
        if (hint.equals("login") || hint.equals("log in") || hint.equals("sign in") || hint.equals("signin")) {
            return "LOGIN";
        }
        return null;
    }

    private static void logClickActionability(String prefix, Locator locator, ExecutionPlan.PlannedStep step) {
        TraceLogger.info("PLAYWRIGHT", prefix + "_ACTIONABILITY_CHECK", prefix + " actionability check", TraceMeta.of(
                "stepId", step.id(),
                "target", step.target(),
                "visible", safeVisible(locator),
                "enabled", safeEnabled(locator),
                "boundingBox", safeBoundingBox(locator)
        ));
    }

    private static boolean safeVisible(Locator locator) {
        try {
            return locator.isVisible();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static boolean safeEnabled(Locator locator) {
        try {
            return locator.isEnabled();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static String safeTag(Locator locator) {
        try {
            Object tag = locator.evaluate("el => (el.tagName || '').toLowerCase()");
            return tag == null ? "" : String.valueOf(tag);
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static String safeBoundingBox(Locator locator) {
        try {
            var box = locator.boundingBox();
            if (box == null) {
                return "";
            }
            return Math.round(box.x) + "," + Math.round(box.y) + "," + Math.round(box.width) + "," + Math.round(box.height);
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private void publishDiagnostics(
            ExecutionPlan plan,
            Consumer<ProgressEvent> progress,
            PageDiagnostics diagnostics,
            String traceId,
            String stepId,
            int stepNumber) {
        List<String> console = diagnostics.consoleErrors();
        if (!console.isEmpty()) {
            String first = console.getFirst();
            emitRich(progress, plan, "CONSOLE_ERROR", "Browser console error detected",
                    Map.of("error", first), stepNumber, plan.steps().size(), "", "", null);
            storeEvent(traceId, plan, EventType.CONSOLE_ERROR, EventComponent.BROWSER,
                    "Console error: " + first, stepId, stepNumber, null, null, null, null, 0, first);
        }
        List<String> failedRequests = diagnostics.failedRequests();
        if (!failedRequests.isEmpty()) {
            String first = failedRequests.getFirst();
            emitRich(progress, plan, "REQUEST_FAILED", "Network request failed",
                    Map.of("request", first), stepNumber, plan.steps().size(), "", "", null);
            storeEvent(traceId, plan, EventType.REQUEST_FAILED, EventComponent.BROWSER,
                    "Request failed: " + first, stepId, stepNumber, null, null, null, null, 0, first);
        }
        List<String> networkErrors = diagnostics.networkEvents();
        if (!networkErrors.isEmpty()) {
            String first = networkErrors.getFirst();
            emitRich(progress, plan, "CDP_EVENT", "Network diagnostics captured",
                    Map.of("network", first), stepNumber, plan.steps().size(), "", "", null);
            storeEvent(traceId, plan, EventType.CDP_EVENT, EventComponent.BROWSER,
                    "Network diagnostics: " + first, stepId, stepNumber, null, null, null, null, 0, first);
        }
    }

    private ElementResolver.ResolvedElement resolveRangeBound(
            Page page,
            ExecutionPlan plan,
            ExecutionPlan.PlannedStep step,
            Consumer<ProgressEvent> progress,
            PageDiagnostics diagnostics,
            Map<String, LocatorMemoryEntry> known,
            String field,
            boolean min) {
        try {
            return filterEngine.resolveRangeBound(page, field, min);
        } catch (RuntimeException ignored) {
        }
        String hint = (min ? "Min " : "Max ") + humanizeField(field);
        try {
            return resolveHint(page, plan, step, progress, diagnostics, known, hint, SupportedActions.INPUT);
        } catch (RuntimeException ignored) {
            return resolveHint(page, plan, step, progress, diagnostics, known, hint, SupportedActions.SELECT);
        }
    }

    private static void applyBoundValue(Page page, ElementResolver.ResolvedElement bound, String value) {
        if (bound == null || value == null) {
            return;
        }
        SelectControlDispatcher.select(page, bound.locator(), bound.controlType(), value);
    }

    private LocatorMemoryEntry applyNumericRangeBound(
            Page page,
            ExecutionPlan plan,
            ExecutionPlan.PlannedStep step,
            Consumer<ProgressEvent> progress,
            PageDiagnostics diagnostics,
            Map<String, LocatorMemoryEntry> known,
            CancellationToken cancellationToken,
            int stepNumber,
            int totalSteps,
            String traceId,
            String field,
            String expectedValue,
            String operator,
            String normalizedOperator,
            PageStateWatcher.Observation before,
            String beforeResults) {
        ElementResolver.ResolvedElement minField = resolveRangeBound(
                page, plan, step, progress, diagnostics, known, field, true);
        ElementResolver.ResolvedElement maxField = resolveRangeBound(page, plan, step, progress, diagnostics, known, field, false);
        String minVal = safeInputValue(minField.locator());
        boolean minEmpty = minVal.isBlank() || "0".equals(minVal) || "min".equalsIgnoreCase(minVal);
        ElementResolver.ResolvedElement target = minEmpty ? minField : maxField;
        applyBoundValue(page, target, expectedValue);
        emitFilterEvent(progress, plan, stepNumber, totalSteps, "FILTER_CONTROL_SELECTED",
                "Selected numeric range bound",
                Map.of("field", nullToEmpty(field), "bound", minEmpty ? "min" : "max",
                        "control", nullToEmpty(target.resolvedLocator())));
        emitFilterEvent(progress, plan, stepNumber, totalSteps, "FILTER_APPLIED",
                "Applied " + field + " bound=" + expectedValue,
                Map.of("field", nullToEmpty(field), "value", nullToEmpty(expectedValue), "operator", operator));
        PageStateWatcher.waitForChange(
                page,
                before,
                () -> PageReadinessContract.countInteractive(page),
                plan.testCaseId(),
                progress
        );
        verifyFilterStateOrThrow(target.locator(), expectedValue);
        emitFilterEvent(progress, plan, stepNumber, totalSteps, "FILTER_STATE_VERIFIED",
                "Filter control state verified", Map.of("field", field, "operator", operator));
        verifyFilterResultsOrThrow(page, field, normalizedOperator, expectedValue, minEmpty ? expectedValue : null,
                minEmpty ? null : expectedValue, beforeResults, progress, plan, stepNumber, totalSteps);
        return memory(step, target.resolvedLocator(), target.locatorType(), target.confidence(), page,
                target.healed(), expectedValue, target.locatorCloud(), target.controlType(), target);
    }

    private static boolean looksLikeRangeField(String field) {
        String normalized = field == null ? "" : field.toLowerCase(Locale.ROOT);
        return normalized.contains("price")
                || normalized.contains("cost")
                || normalized.contains("amount")
                || normalized.contains("budget")
                || normalized.contains("range")
                || normalized.contains("min")
                || normalized.contains("max");
    }

    private static boolean isNumericValue(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String token = value.replace(",", "").replace("₹", "").replace("rs", "")
                .replace("RS", "").replace(" ", "").trim();
        return token.matches("\\d+(\\.\\d+)?");
    }

    private static String safeInputValue(Locator locator) {
        try {
            String value = locator.inputValue();
            return value == null ? "" : value.trim();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private void recordBrowserState(Page page, ExecutionPlan.PlannedStep step, int stepNumber, boolean succeeded) {
        com.smartqa.browser.intelligence.recovery.BrowserStateHistory history = STATE_HISTORY.get();
        if (history == null || page == null) {
            return;
        }
        try {
            BrowserSnapshot snap = intelligence.inspect(page, List.of());
            String compact = intelligence.compactForAi(snap, step == null ? "" : step.target(), "");
            history.record(new com.smartqa.browser.intelligence.recovery.BrowserStateRecord(
                    stepNumber,
                    safeUrl(page),
                    safeTitle(page),
                    java.time.Instant.now(),
                    "",
                    compact,
                    step == null ? "" : step.action(),
                    step == null ? "" : step.target(),
                    succeeded,
                    ""
            ));
            TraceLogger.info("BROWSER", "BROWSER_STATE_CAPTURED", "Recorded sanitized browser state", TraceMeta.of(
                    "step", stepNumber,
                    "url", safeUrl(page)
            ));
        } catch (RuntimeException ignored) {
        }
    }

    private boolean tryRecoverWrongPage(
            Page page,
            ExecutionPlan plan,
            ExecutionPlan.PlannedStep step,
            Consumer<ProgressEvent> progress,
            PageDiagnostics diagnostics,
            SmartQaException error) {
        com.smartqa.browser.intelligence.recovery.BrowserStateHistory history = STATE_HISTORY.get();
        if (history == null || page == null || step == null || error == null) {
            return false;
        }
        if (!PlaywrightBrowserLauncher.isPageAlive(page)) {
            BrowserLifecycle.warn("RECOVERY_SKIPPED_CLOSED_PAGE", "Recovery refused to use a closed page",
                    TraceMeta.of("stepId", step.id()));
            return false;
        }
        ErrorCode code = error.errorCode();
        if (code != ErrorCode.TARGET_NOT_PRESENT && code != ErrorCode.AMBIGUOUS_ELEMENT
                && code != ErrorCode.FILTER_TARGET_RESOLUTION && code != ErrorCode.ELEMENT_NOT_FOUND) {
            return false;
        }
        String compact = intelligence.compactForAi(inspect(page, plan, progress, diagnostics), step.target(), "");
        var decision = com.smartqa.browser.intelligence.recovery.PageRecoveryPlanner.recommend(
                history, step.target(), safeUrl(page), compact, 0);
        if (!decision.isBack()) {
            return false;
        }
        TraceLogger.info("RECOVERY", "BACKTRACK_EXECUTED", "Playwright goBack for wrong-page recovery", TraceMeta.of(
                "target", step.target(),
                "reason", decision.reason()
        ));
        try {
            page.goBack(new Page.GoBackOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
            SafeClick.settle(page);
            TraceLogger.info("RECOVERY", "RECOVERY_REINSPECTION", "Re-inspecting live page after backtrack", TraceMeta.of(
                    "url", safeUrl(page)
            ));
            return true;
        } catch (RuntimeException ex) {
            TraceLogger.warn("RECOVERY", "RECOVERY_EXHAUSTED", "Backtrack navigation failed", TraceMeta.of(
                    "message", ex.getMessage() == null ? "" : ex.getMessage()
            ));
            return false;
        }
    }
}
