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

class FilterOwnedOptionRegressionTest {

    @Test
    void prefersBrandAkCheckboxOverHeaderText() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("""
                    <html><body>
                      <header>
                        <a href="/login">AK</a>
                        <button>Cart</button>
                      </header>
                      <aside aria-label="Filters">
                        <section>
                          <h3>Brand</h3>
                          <label><input type="checkbox" name="brand" value="AK"> AK</label>
                          <label><input type="checkbox" name="brand" value="AKRAFT"> AKRAFT</label>
                        </section>
                      </aside>
                      <main>
                        <article>Women Striped Round Neck Pure Cotton Pink</article>
                      </main>
                    </body></html>
                    """);
            BrowserIntelligenceService intelligence = new BrowserIntelligenceService(new DomExtractor());
            ElementResolver resolver = new ElementResolver(new NoopAi(), tools.jackson.databind.json.JsonMapper.builder().build(), intelligence);
            FilterEngine engine = new FilterEngine(intelligence, resolver);

            FilterEngine.Discovery brand = engine.discover(page, "Brand", "equals", "AK");
            assertNotNull(brand.optionCandidate());
            assertTrue(brand.optionCandidate().headingContext().toLowerCase().contains("brand")
                    || brand.optionCandidate().ownershipContext().contains("brand"));
            assertTrue(brand.optionCandidate().text().contains("AK")
                    || brand.optionCandidate().accessibleName().contains("AK"));
            assertEquals(SupportedActions.CLICK, brand.bindAction());
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
            return reactor.core.publisher.Mono.empty();
        }
    }
}
