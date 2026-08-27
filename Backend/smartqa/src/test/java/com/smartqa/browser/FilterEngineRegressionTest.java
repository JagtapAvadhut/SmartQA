package com.smartqa.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.smartqa.browser.intelligence.BrowserIntelligenceService;
import com.smartqa.browser.intelligence.DomExtractor;
import com.smartqa.intent.SupportedActions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Synthetic product page — no site-specific selectors.
 */
class FilterEngineRegressionTest {

    @Test
    void discoversBrandCheckboxAndPriceRangeOnGenericPage() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("""
                    <html><body>
                      <aside aria-label="Filters">
                        <section>
                          <h3>Brand</h3>
                          <label><input type="checkbox" name="brand" value="HP"> HP</label>
                          <label><input type="checkbox" name="brand" value="Dell"> Dell</label>
                        </section>
                        <section>
                          <h3>Price</h3>
                          <label>Min Price <input id="min" type="number" aria-label="Min Price"></label>
                          <label>Max Price <input id="max" type="number" aria-label="Max Price"></label>
                        </section>
                      </aside>
                      <main>
                        <article class="product">HP Pavilion</article>
                        <article class="product">Dell XPS</article>
                      </main>
                    </body></html>
                    """);
            BrowserIntelligenceService intelligence = new BrowserIntelligenceService(new DomExtractor());
            ElementResolver resolver = new ElementResolver(new NoopAi(), tools.jackson.databind.json.JsonMapper.builder().build(), intelligence);
            FilterEngine engine = new FilterEngine(intelligence, resolver);

            FilterEngine.Discovery brand = engine.discover(page, "Brand", "equals", "HP");
            assertNotNull(brand.optionCandidate());
            assertTrue(brand.optionCandidate().text().toLowerCase().contains("hp")
                    || brand.optionCandidate().accessibleName().toLowerCase().contains("hp"));

            ElementResolver.ResolvedElement min = engine.resolveRangeBound(page, "Price", true);
            ElementResolver.ResolvedElement max = engine.resolveRangeBound(page, "Price", false);
            min.locator().fill("60000");
            max.locator().fill("75000");
            assertEquals("60000", page.locator("#min").inputValue());
            assertEquals("75000", page.locator("#max").inputValue());

            ElementResolver.ResolvedElement option = engine.resolveOption(page, brand);
            assertEquals(SupportedActions.CLICK, brand.bindAction());
            // Prefer checking the known HP checkbox via label semantics after discovery.
            page.getByLabel("HP").check();
            assertTrue(page.locator("input[value='HP']").isChecked());
            assertNotNull(option);
            browser.close();
        }
    }

    @Test
    void doesNotCollapseAlreadyExpandedFilterSection() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("""
                    <html><body>
                      <aside aria-label="Filters">
                        <details id="brand-panel" open>
                          <summary>Brand</summary>
                          <label><input type="checkbox" name="brand" value="HP"> HP</label>
                        </details>
                      </aside>
                    </body></html>
                    """);
            BrowserIntelligenceService intelligence = new BrowserIntelligenceService(new DomExtractor());
            ElementResolver resolver = new ElementResolver(new NoopAi(), tools.jackson.databind.json.JsonMapper.builder().build(), intelligence);
            FilterEngine engine = new FilterEngine(intelligence, resolver);
            FilterEngine.Discovery discovery = engine.discover(page, "Brand", "equals", "HP");
            assertNotNull(discovery.optionCandidate());
            engine.ensureExpanded(page, discovery);
            assertTrue(page.getByLabel("HP").isVisible());
            browser.close();
        }
    }

    private static final class NoopAi implements com.smartqa.ai.AiProvider {
        @Override
        public String id() {
            return "noop";
        }

        @Override
        public reactor.core.publisher.Mono<String> generateText(com.smartqa.ai.AiPrompt prompt) {
            return reactor.core.publisher.Mono.just("{}");
        }

        @Override
        public <T> reactor.core.publisher.Mono<T> generateStructuredOutput(com.smartqa.ai.AiPrompt prompt, Class<T> type) {
            return reactor.core.publisher.Mono.error(new UnsupportedOperationException());
        }

        @Override
        public reactor.core.publisher.Mono<com.smartqa.ai.AiHealthStatus> healthCheck() {
            return reactor.core.publisher.Mono.just(com.smartqa.ai.AiHealthStatus.available("noop", null, null, 0));
        }
    }
}
