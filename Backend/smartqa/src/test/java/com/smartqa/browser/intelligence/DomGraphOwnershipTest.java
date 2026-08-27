package com.smartqa.browser.intelligence;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomGraphOwnershipTest {

    @Test
    void brandOwnedHpOutranksProductCardHp() {
        ElementCandidate brandHp = candidate("brand-hp", "HP", "Brand", "Brand | filter panel", "FILTER_PANEL", "checkbox");
        ElementCandidate productHp = candidate("product-hp", "HP Pavilion", "Recommended products", "product card", "MAIN", "link");

        List<LocatorRanker.RankedElement> ranked = LocatorRanker.rankOwned(
                List.of(brandHp, productHp), "click", "HP", "Brand", null);

        assertEquals("brand-hp", ranked.getFirst().element().candidateId());
        assertTrue(ranked.getFirst().score() > ranked.get(1).score());
    }

    @Test
    void underBrandCompositeHintSelectsOwnedOption() {
        ElementCandidate brandHp = candidate("brand-hp", "HP", "Brand", "aside Brand filter", "FILTER_PANEL", "checkbox");
        ElementCandidate relatedHp = candidate("related-hp", "HP", "Related search", "related chips", "CONTENT", "button");

        List<LocatorRanker.RankedElement> ranked = LocatorRanker.rank(
                List.of(relatedHp, brandHp), "click", "HP under Brand");

        assertEquals("brand-hp", ranked.getFirst().element().candidateId());
    }

    @Test
    void sameTextOutsideOwnerIsDemoted() {
        ElementCandidate owned = candidate("owned", "Samsung", "Brand", "Brand filters", "FILTER_PANEL", "checkbox");
        ElementCandidate stray = candidate("stray", "Samsung", "Sponsored", "ad card", "CONTENT", "link");

        List<LocatorRanker.RankedElement> ranked = LocatorRanker.rank(
                List.of(stray, owned), "click", "Samsung under Brand");

        assertEquals("owned", ranked.getFirst().element().candidateId());
        assertTrue(DomEvidence.ownsContext(owned, "Brand"));
        assertFalse(DomEvidence.ownsContext(stray, "Brand"));
    }

    @Test
    void relevantDomExtractorKeepsOwnedOptions() {
        ElementCandidate brandHp = candidate("brand-hp", "HP", "Brand", "Brand filters", "FILTER_PANEL", "checkbox");
        ElementCandidate product = candidate("product", "HP Laptop", "Results", "results grid", "MAIN", "link");
        List<ElementCandidate> owned = RelevantDomExtractor.ownedOptions(
                List.of(brandHp, product), "HP", "Brand");
        assertEquals(1, owned.size());
        assertEquals("brand-hp", owned.getFirst().candidateId());
    }

    @Test
    void evidenceQualityRewardsAccessibilityAndContext() {
        ElementCandidate rich = candidate("rich", "Account", "HEADER", "header nav", "HEADER", "button");
        ElementCandidate poor = ElementCandidate.fromMap(Map.of(
                "candidateId", "poor",
                "tag", "div",
                "text", "x",
                "visible", true,
                "enabled", true
        ), 1);
        assertTrue(rich.evidenceQuality() > poor.evidenceQuality());
    }

    private static ElementCandidate candidate(
            String id, String text, String heading, String ancestors, String region, String role) {
        Map<String, Object> map = new HashMap<>();
        map.put("candidateId", id);
        map.put("tag", "checkbox".equals(role) ? "input" : "button".equals(role) ? "button" : "a");
        map.put("role", role);
        map.put("accessibleName", text);
        map.put("text", text);
        map.put("visible", true);
        map.put("enabled", true);
        map.put("headingContext", heading);
        map.put("ancestorContext", ancestors);
        map.put("parentContext", heading + " " + ancestors);
        map.put("nearbyText", heading);
        map.put("region", region);
        map.put("clickable", true);
        if ("checkbox".equals(role)) {
            map.put("inputType", "checkbox");
        }
        return ElementCandidate.fromMap(map, 0);
    }
}
