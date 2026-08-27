package com.smartqa.pipeline;

import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Development fix loop owner: capture → propose → (optional) rebuild script → health check.
 * Does NOT hot-edit the running JVM source.
 */
@Service
public class DevelopmentFixLoopService {

    private final Map<String, SourceFixProposal> proposals = new ConcurrentHashMap<>();
    private final Path proposalsDir;

    public DevelopmentFixLoopService() {
        this.proposalsDir = Path.of("logs", "smartqa", "source-fix-proposals");
    }

    public SourceFixProposal proposeIfNeeded(
            AiDiagnosticResult diagnosis,
            FailureEvidence evidence,
            List<String> attemptHistory,
            String applicationUrl) {
        if (diagnosis == null) {
            return null;
        }
        boolean generic = diagnosis.requiresSourceFix()
                || "GENERIC_ENGINE_DEFECT".equals(diagnosis.normalizedClassification())
                || looksLikeRepeatedEngineDefect(attemptHistory, diagnosis, evidence);
        if (!generic) {
            return null;
        }

        String component = firstNonBlank(diagnosis.responsibleSubsystem(), "SmartQA Engine");
        String className = mapComponentToClass(component, diagnosis.normalizedClassification());
        String method = mapCategoryToMethod(diagnosis.normalizedClassification(), diagnosis.rootCause());
        SourceFixProposal proposal = SourceFixProposal.of(
                component,
                className,
                method,
                firstNonBlank(diagnosis.rootCause(), "GENERIC_ENGINE_DEFECT"),
                evidence == null ? diagnosis.explanation() : evidence.toAiContext(),
                List.of("IndiaMART", "Urban Company", "OrangeHRM"),
                buildRecommendedChange(diagnosis, evidence),
                "Add regression covering " + diagnosis.normalizedClassification() + " / " + diagnosis.rootCause()
        );
        proposals.put(proposal.id(), proposal);
        persist(proposal);
        TraceLogger.info("DEVFIX", "SOURCE_FIX_PROPOSED", "Generic engine defect proposed for source fix", TraceMeta.of(
                "proposalId", proposal.id(),
                "className", className,
                "method", method,
                "rootCause", proposal.rootCause()
        ));
        return proposal;
    }

    public SourceFixProposal get(String id) {
        return proposals.get(id);
    }

    public List<SourceFixProposal> list() {
        return new ArrayList<>(proposals.values());
    }

    /**
     * Marks proposal as queued and writes a rebuild request artifact for Cursor/CI.
     * Actual Java source edits remain Cursor's responsibility.
     */
    public Mono<SourceFixProposal> requestFixAndRebuild(String proposalId) {
        return Mono.fromCallable(() -> {
            SourceFixProposal proposal = proposals.get(proposalId);
            if (proposal == null) {
                throw new IllegalArgumentException("Source fix proposal not found: " + proposalId);
            }
            proposal.setStatus("FIX_AND_REBUILD_REQUESTED");
            proposal.setAppliedAt(Instant.now());
            Path request = proposalsDir.resolve(proposalId + "-rebuild-request.md");
            Files.createDirectories(proposalsDir);
            String body = """
                    # SmartQA Source Fix & Rebuild Request
                    
                    Proposal: %s
                    Component: %s
                    Class: %s
                    Method: %s
                    Root cause: %s
                    
                    ## Recommended change
                    %s
                    
                    ## Regression test
                    %s
                    
                    ## Required test order
                    1. targeted unit test
                    2. targeted integration test
                    3. mvnw test
                    4. mvnw package -DskipTests
                    5. npm run build
                    6. headed browser smoke
                    7. iframe regression
                    8. shadow DOM regression
                    9. IndiaMART
                    10. Urban Company
                    11. OrangeHRM
                    
                    ## Evidence
                    %s
                    """.formatted(
                    proposal.id(),
                    proposal.component(),
                    proposal.className(),
                    proposal.method(),
                    proposal.rootCause(),
                    proposal.recommendedChange(),
                    proposal.regressionTest(),
                    truncate(proposal.evidence(), 4000)
            );
            Files.writeString(request, body, StandardCharsets.UTF_8);
            proposal.setRebuildLog("Wrote rebuild request: " + request.toAbsolutePath());
            persist(proposal);
            TraceLogger.info("DEVFIX", "SOURCE_FIX_REBUILD_REQUESTED", "Fix & rebuild requested", TraceMeta.of(
                    "proposalId", proposalId,
                    "path", request.toAbsolutePath().toString()
            ));
            return proposal;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private boolean looksLikeRepeatedEngineDefect(
            List<String> attemptHistory,
            AiDiagnosticResult diagnosis,
            FailureEvidence evidence) {
        if (attemptHistory == null || attemptHistory.size() < 2) {
            return false;
        }
        String cat = diagnosis.normalizedClassification();
        long same = attemptHistory.stream()
                .filter(a -> a != null && a.toUpperCase(Locale.ROOT).contains(cat))
                .count();
        if (same < 2) {
            return false;
        }
        // Host loss after search across retries is a classic SearchEngine defect signal
        String blob = evidence == null ? "" : (safe(evidence.url()) + safe(evidence.actual())).toLowerCase(Locale.ROOT);
        return blob.contains("export.") || cat.equals("SEARCH") || cat.equals("FILTER") || cat.equals("WRONG_HOST");
    }

    private static String mapComponentToClass(String component, String classification) {
        String c = (component + " " + classification).toLowerCase(Locale.ROOT);
        if (c.contains("search") || c.contains("wrong_host") || c.contains("autocomplete")) {
            return "com.smartqa.browser.AutocompleteHandler";
        }
        if (c.contains("filter")) {
            return "com.smartqa.browser.FilterEngine";
        }
        if (c.contains("assertion")) {
            return "com.smartqa.browser.PlaywrightBrowserExecutionProvider";
        }
        if (c.contains("locator") || c.contains("discovery")) {
            return "com.smartqa.browser.ElementResolver";
        }
        if (c.contains("recovery")) {
            return "com.smartqa.browser.RecoveryEngine";
        }
        return "com.smartqa.pipeline.PipelineService";
    }

    private static String mapCategoryToMethod(String classification, String rootCause) {
        String c = (classification + " " + safe(rootCause)).toUpperCase(Locale.ROOT);
        if (c.contains("HOST")) {
            return "confirmSelectionIfNeeded / restoreExpectedHost";
        }
        if (c.contains("FILTER")) {
            return "applyFilter";
        }
        if (c.contains("ASSERT")) {
            return "verifyAssertion";
        }
        if (c.contains("LOCATOR")) {
            return "heal";
        }
        return "diagnoseAndRecover";
    }

    private static String buildRecommendedChange(AiDiagnosticResult diagnosis, FailureEvidence evidence) {
        StringBuilder sb = new StringBuilder();
        sb.append("Keep user assertion text unchanged. ");
        sb.append(firstNonBlank(diagnosis.explanation(), "Fix generic engine behavior.")).append(' ');
        if (evidence != null && safe(evidence.url()).toLowerCase(Locale.ROOT).contains("export.")) {
            sb.append("After autocomplete/search, detect host divergence from the requested application domain ")
                    .append("and restore domestic search context before asserting results. ");
        }
        sb.append("Add a generic regression — no site-specific hardcoding.");
        return sb.toString();
    }

    private void persist(SourceFixProposal proposal) {
        try {
            Files.createDirectories(proposalsDir);
            Path file = proposalsDir.resolve(proposal.id() + ".md");
            String body = """
                    # SourceFixProposal %s
                    status: %s
                    component: %s
                    class: %s
                    method: %s
                    rootCause: %s
                    
                    ## Recommended change
                    %s
                    
                    ## Evidence
                    %s
                    """.formatted(
                    proposal.id(),
                    proposal.status(),
                    proposal.component(),
                    proposal.className(),
                    proposal.method(),
                    proposal.rootCause(),
                    proposal.recommendedChange(),
                    truncate(proposal.evidence(), 4000)
            );
            Files.writeString(file, body, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // best-effort artifact
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }

    private static String safe(String v) {
        return v == null ? "" : v;
    }

    private static String truncate(String v, int max) {
        if (v == null) {
            return "";
        }
        return v.length() <= max ? v : v.substring(0, max) + "…";
    }
}
