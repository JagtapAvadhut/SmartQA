package com.smartqa.rag;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Seeds compact GLOBAL_GENERIC patterns once (idempotent via content prefix check).
 */
@Component
public class RagSeedService {

    private final RagIngestionService ingestionService;
    private final RagKnowledgeRepository repository;

    public RagSeedService(RagIngestionService ingestionService, RagKnowledgeRepository repository) {
        this.ingestionService = ingestionService;
        this.repository = repository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedOnReady() {
        seedDefaults().subscribe();
    }

    public Mono<Long> seedDefaults() {
        return repository.countAll()
                .flatMap(count -> {
                    // Always attempt seeds; duplicates are skipped by ingest.
                    return Flux.fromIterable(seeds())
                            .concatMap(seed -> ingestionService.ingest(
                                    KnowledgeScope.GLOBAL_GENERIC,
                                    "global",
                                    seed.type(),
                                    seed.content(),
                                    "seed",
                                    null,
                                    null))
                            .then(repository.countAll());
                });
    }

    private static java.util.List<Seed> seeds() {
        return java.util.List.of(
                new Seed(KnowledgeContentType.GENERIC_BROWSER_PATTERN,
                        "Icon-only account controls may require SVG + actionable parent + aria-label + nearby header context."),
                new Seed(KnowledgeContentType.LOCATOR_PATTERN,
                        "Account/profile controls often expose person/account semantics; cart/bag is adjacent — prefer account semantics for profile instructions and verify with live Safety Gate."),
                new Seed(KnowledgeContentType.SEARCH_PATTERN,
                        "Autocomplete often requires waiting for visible suggestions before selecting an option."),
                new Seed(KnowledgeContentType.FILTER_PATTERN,
                        "Filters may be implemented as accordion + checkbox + chip; expand closed panels before selecting options."),
                new Seed(KnowledgeContentType.RECOVERY_PATTERN,
                        "Pointer interception may indicate an overlay/backdrop; dismiss visible non-business blocking overlays then rediscover."),
                new Seed(KnowledgeContentType.TAB_PATTERN,
                        "New tab detection should compare page sets before and after action; never use fixed tab index."),
                new Seed(KnowledgeContentType.ASSERTION_PATTERN,
                        "Explicit visible-text assertions must be verified against fresh DOM; never rewrite expected assertion text."),
                new Seed(KnowledgeContentType.RECOVERY_PATTERN,
                        "If search redirects to a different subdomain of the same site, restore the expected application host before asserting domestic results."),
                new Seed(KnowledgeContentType.FILTER_PATTERN,
                        "Filter recovery flow: FILTER → CONTAINER → OPTION; inspect current DOM ownership rather than replaying historical site selectors."),
                new Seed(KnowledgeContentType.SEARCH_PATTERN,
                        "Search verification requires SEARCH_INPUT_STATE plus suggestion/selection state plus result context. If requested Samsung and actual Micromax, classify SEARCH_STATE_MISMATCH and recover before continuing to filter."),
                new Seed(KnowledgeContentType.SEARCH_PATTERN,
                        "Location selection must match the requested city in visible selected value, URL/query, and result heading. A different city is LOCATION_STATE_MISMATCH."),
                new Seed(KnowledgeContentType.SEARCH_PATTERN,
                        "Autocomplete must not accept a suggestion that shares only generic category tokens. Distinctive tokens of the requested value must be present."),
                new Seed(KnowledgeContentType.FILTER_PATTERN,
                        "Filter chrome is a compact interactive icon or button in header/sidebar with nearby filter controls. De-rank long content titles that merely contain the word filter."),
                new Seed(KnowledgeContentType.FILTER_PATTERN,
                        "Native select matching should inspect options and compare normalized numeric labels such as 40000, 40000 formatted with currency, 40,000, or 40K. If the control is not a native select, use custom dropdown, range, slider, or input."),
                new Seed(KnowledgeContentType.TAB_PATTERN,
                        "switch_to_new_tab, switch tab, open new tab, and new browser tab are one context action: pagesBefore, action, popup detection, pagesAfter, switch to the discovered Page. Never use a fixed tab index.")
        );
    }

    private record Seed(KnowledgeContentType type, String content) {
    }
}
