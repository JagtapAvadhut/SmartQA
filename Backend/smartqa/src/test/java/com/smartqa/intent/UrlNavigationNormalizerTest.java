package com.smartqa.intent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlNavigationNormalizerTest {

    @Test
    void rewritesClickUrlToNavigate() {
        IntentStep rewritten = UrlNavigationNormalizer.rewrite(
                new IntentStep("s1", "click", "https://www.flipkart.com/", null, null));
        assertEquals("navigate", rewritten.action());
        assertEquals("https://www.flipkart.com/", rewritten.target());
        assertTrue(UrlNavigationNormalizer.looksLikeHttpUrl(rewritten.target()));
    }

    @Test
    void doesNotTreatHttpUrlAsLocatorShape() {
        assertTrue(UrlNavigationNormalizer.looksLikeHttpUrl("https://www.flipkart.com/"));
        assertFalse(UrlNavigationNormalizer.looksLikeHttpUrl("xpath=//button[1]"));
        assertFalse(UrlNavigationNormalizer.looksLikeHttpUrl("Login"));
    }

    @Test
    void leavesOrdinaryClickUnchanged() {
        IntentStep original = new IntentStep("s1", "click", "Fashion", null, null);
        IntentStep rewritten = UrlNavigationNormalizer.rewrite(original);
        assertEquals(original, rewritten);
    }

    @Test
    void bindToApplicationRewritesForeignNavigateHost() {
        IntentContract contract = new IntentContract(
                IntentContract.READY,
                "Banner",
                1.0,
                List.of(new IntentScenario("s1", "Main", List.of(
                        new IntentStep("s1", "navigate", "Semantic target", "https://www.flipkart.com/", null)
                ))),
                List.of()
        );
        IntentContract bound = UrlNavigationNormalizer.bindToApplication(contract, "http://127.0.0.1:8765/");
        IntentStep step = bound.scenarios().getFirst().steps().getFirst();
        assertEquals("navigate", step.action());
        assertEquals("http://127.0.0.1:8765/", step.value());
        assertEquals("127.0.0.1", UrlNavigationNormalizer.hostOf(step.value()));
    }

    @Test
    void rewritesContractClickUrl() {
        IntentContract contract = new IntentContract(
                IntentContract.READY,
                "Open",
                0.9,
                List.of(new IntentScenario("s1", "Main", List.of(
                        new IntentStep("s1_step1", "click", "https://example.com", null, null)
                ))),
                List.of()
        );
        IntentContract rewritten = UrlNavigationNormalizer.rewrite(contract);
        assertEquals("navigate", rewritten.scenarios().getFirst().steps().getFirst().action());
    }
}
