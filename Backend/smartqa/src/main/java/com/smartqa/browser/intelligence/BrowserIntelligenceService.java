package com.smartqa.browser.intelligence;

import com.microsoft.playwright.Page;
import com.smartqa.browser.SafeClick;
import com.smartqa.browser.intelligence.cdp.CdpCandidateEnricher;
import com.smartqa.browser.multimodal.CandidateRelationshipGraph;
import com.smartqa.debug.DomTraceStats;
import com.smartqa.debug.PipelineTimer;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class BrowserIntelligenceService {

    private static final long SAME_MOMENT_TTL_MS = 450;

    private final DomExtractor domExtractor;
    private final BrowserEvidenceOrchestrator evidenceOrchestrator;
    private final AtomicInteger stateVersion = new AtomicInteger(1);
    private final ThreadLocal<CachedMoment> sameMoment = new ThreadLocal<>();

    public BrowserIntelligenceService(DomExtractor domExtractor) {
        this(domExtractor, null);
    }

    @Autowired
    public BrowserIntelligenceService(
            DomExtractor domExtractor,
            ObjectProvider<BrowserEvidenceOrchestrator> evidenceOrchestrator
    ) {
        this.domExtractor = domExtractor;
        this.evidenceOrchestrator = evidenceOrchestrator == null ? null : evidenceOrchestrator.getIfAvailable();
    }

    public void invalidateEvidence() {
        sameMoment.remove();
        stateVersion.incrementAndGet();
    }

    public int currentStateVersion() {
        return stateVersion.get();
    }

    public BrowserSnapshot inspect(Page page, List<String> consoleErrors) {
        return inspect(page, consoleErrors, false);
    }

    public BrowserSnapshot inspect(Page page, List<String> consoleErrors, boolean captureCdp) {
        return PipelineTimer.time("DOM_EXTRACT", () -> inspectNow(page, consoleErrors, captureCdp));
    }

    private BrowserSnapshot inspectNow(Page page, List<String> consoleErrors, boolean captureCdp) {
        String url = safeUrl(page);
        CachedMoment cached = sameMoment.get();
        long now = System.currentTimeMillis();
        if (!captureCdp && cached != null && cached.matches(url, now)) {
            TraceLogger.info("DOM", "EVIDENCE_CACHE_HIT", "Reused same-moment evidence", TraceMeta.of(
                    "url", url,
                    "ageMs", now - cached.atMs,
                    "stateVersion", cached.stateVersion,
                    "evidenceMomentId", cached.snapshot.evidenceMomentId()
            ));
            return cached.snapshot;
        }
        List<ElementCandidate> elements = List.of();
        try {
            elements = domExtractor.extract(page);
        } catch (RuntimeException ex) {
            if (SafeClick.isTransientNavigation(ex)) {
                SafeClick.settle(page);
                try {
                    elements = domExtractor.extract(page);
                } catch (RuntimeException ignored) {
                    elements = List.of();
                }
            } else {
                throw ex;
            }
        }
        String momentId = null;
        if (evidenceOrchestrator != null) {
            try {
                BrowserEvidenceOrchestrator.EvidenceMoment moment = evidenceOrchestrator.captureMoment(page, captureCdp);
                momentId = moment.momentId();
                if (moment.cdp() != null && moment.cdp().captured()) {
                    elements = CdpCandidateEnricher.enrich(elements, moment.cdp());
                }
            } catch (RuntimeException ignored) {
            }
        }
        String title = "";
        try {
            title = page.title();
        } catch (RuntimeException ignored) {
        }
        TraceLogger.info("DOM", "DOM_STATS", "Live DOM statistics", DomTraceStats.summarize(url, elements));
        if (momentId == null || momentId.isBlank()) {
            momentId = java.util.UUID.randomUUID().toString();
        }
        ElementTree tree = ElementTree.build(elements, momentId);
        elements = tree.stamp(elements);
        CandidateRelationshipGraph.Graph graph = CandidateRelationshipGraph.build(elements, tree);
        TreeGraphReconciler.Result reconciled = TreeGraphReconciler.reconcile(elements, tree, graph);
        elements = reconciled.stamped();
        int interactive = 0;
        for (ElementCandidate el : elements) {
            if (el.structureOrEmpty().isActionableKind() || el.clickable()) {
                interactive++;
            }
        }
        BrowserSnapshot snapshot = new BrowserSnapshot(
                url,
                title,
                interactive,
                elements,
                consoleErrors == null ? List.of() : List.copyOf(consoleErrors),
                graph,
                momentId,
                PhysicalControl.fromAll(elements),
                tree,
                momentId,
                momentId
        );
        sameMoment.set(new CachedMoment(url, now, stateVersion.get(), snapshot));
        return snapshot;
    }

    private static String safeUrl(Page page) {
        try {
            return page == null ? "" : page.url();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private record CachedMoment(String url, long atMs, int stateVersion, BrowserSnapshot snapshot) {
        boolean matches(String currentUrl, long now) {
            return snapshot != null
                    && snapshot.interactiveCount() > 0
                    && url != null
                    && url.equals(currentUrl)
                    && now - atMs <= SAME_MOMENT_TTL_MS;
        }
    }

    public LocatorRanker.RankedElement resolveFromDom(BrowserSnapshot snapshot, String action, String target) {
        List<LocatorRanker.RankedElement> ranked = LocatorRanker.rank(snapshot.elements(), action, target);
        if (LocatorRanker.uniqueWinner(ranked)) {
            return ranked.getFirst();
        }
        return null;
    }

    public String compactForAi(BrowserSnapshot snapshot) {
        return RelevantDomExtractor.compact(snapshot.elements(), "", "", 60);
    }

    public String compactForAi(BrowserSnapshot snapshot, String target, String ownerHint) {
        return RelevantDomExtractor.compact(snapshot.elements(), target, ownerHint, 40);
    }

    public List<String> ambiguityOptions(BrowserSnapshot snapshot, String action, String target) {
        if (snapshot == null) {
            return List.of();
        }
        return ambiguityOptions(LocatorRanker.rank(snapshot.elements(), action, target), target);
    }

    public List<String> ambiguityOptions(List<LocatorRanker.RankedElement> ranked, String target) {
        List<String> options = new ArrayList<>();
        if (ranked == null) {
            return options;
        }
        String hint = target == null ? "" : target.trim();
        for (int i = 0; i < Math.min(5, ranked.size()); i++) {
            ElementCandidate element = ranked.get(i).element();
            if (!hint.isBlank() && !LocatorRanker.optionMatches(element, hint.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim())) {
                continue;
            }
            String label = firstNonBlank(element.accessibleName(), element.text(), element.ariaLabel(), element.testId());
            if (!label.isBlank() && !options.contains(label)) {
                options.add(label);
            }
        }
        return options;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
