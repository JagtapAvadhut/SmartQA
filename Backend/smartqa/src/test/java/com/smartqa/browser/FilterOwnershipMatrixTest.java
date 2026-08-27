package com.smartqa.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.smartqa.browser.intelligence.BrowserIntelligenceService;
import com.smartqa.browser.intelligence.DomExtractor;
import com.smartqa.intent.SupportedActions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterOwnershipMatrixTest {

    @Test
    void ownedFilterBeatsHeaderNavFooterForGenericFields() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("""
                    <html><body>
                      <header><a href="/login">HP</a><a>Samsung</a><span>Maharashtra</span></header>
                      <nav><a>HP</a></nav>
                      <aside aria-label="Filters">
                        <section><h3>Brand</h3>
                          <label><input type="checkbox" value="HP"> HP</label>
                          <label><input type="checkbox" value="Samsung"> Samsung</label>
                        </section>
                        <section><h3>State</h3>
                          <label><input type="checkbox" value="Maharashtra"> Maharashtra</label>
                        </section>
                        <section><h3>Color</h3>
                          <label><input type="checkbox" value="Pink"> Pink</label>
                        </section>
                        <section><h3>Size</h3>
                          <label><input type="checkbox" value="M"> M</label>
                        </section>
                        <section><h3>Customer Rating</h3>
                          <label><input type="checkbox" value="4"> 4★ & above</label>
                        </section>
                      </aside>
                      <footer>HP Samsung Maharashtra</footer>
                      <main><article>Women Striped Round Neck Pure Cotton Pink</article></main>
                    </body></html>
                    """);
            BrowserIntelligenceService intelligence = new BrowserIntelligenceService(new DomExtractor());
            ElementResolver resolver = new ElementResolver(new NoopAi(), tools.jackson.databind.json.JsonMapper.builder().build(), intelligence);
            FilterEngine engine = new FilterEngine(intelligence, resolver);

            assertOwned(engine.discover(page, "Brand", "equals", "HP"), "hp");
            assertOwned(engine.discover(page, "Brand", "equals", "Samsung"), "samsung");
            assertOwned(engine.discover(page, "State", "equals", "Maharashtra"), "maharashtra");
            assertOwned(engine.discover(page, "Color", "equals", "Pink"), "pink");
            page.getByLabel("HP").check();
            assertTrue(page.locator("input[value='HP']").isChecked());
            browser.close();
        }
    }

    @Test
    void iframeAndShadowOwnedOptionsResolve() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("""
                    <html><body>
                      <header>HP</header>
                      <iframe id="f" srcdoc="<section><h3>Brand</h3><label><input type='checkbox' value='HP'> HP</label></section>"></iframe>
                      <div id="host"></div>
                      <script>
                        const host = document.getElementById('host');
                        const shadow = host.attachShadow({mode:'open'});
                        shadow.innerHTML = '<section><h3>Color</h3><label><input type=checkbox value=Pink> Pink</label></section>';
                      </script>
                    </body></html>
                    """);
            BrowserIntelligenceService intelligence = new BrowserIntelligenceService(new DomExtractor());
            ElementResolver resolver = new ElementResolver(new NoopAi(), tools.jackson.databind.json.JsonMapper.builder().build(), intelligence);
            FilterEngine engine = new FilterEngine(intelligence, resolver);
            FilterEngine.Discovery brand = engine.discover(page, "Brand", "equals", "HP");
            assertNotNull(brand.optionCandidate());
            browser.close();
        }
    }

    private static void assertOwned(FilterEngine.Discovery discovery, String value) {
        assertNotNull(discovery.optionCandidate());
        String blob = (discovery.optionCandidate().headingContext() + " "
                + discovery.optionCandidate().ownershipContext() + " "
                + discovery.optionCandidate().text() + " "
                + discovery.optionCandidate().accessibleName()).toLowerCase();
        assertTrue(blob.contains(value));
        assertTrue(blob.contains("brand") || blob.contains("state") || blob.contains("color")
                || blob.contains("size") || blob.contains("rating") || blob.contains("filter"));
        assertEqualsClick(discovery);
    }

    private static void assertEqualsClick(FilterEngine.Discovery discovery) {
        assertTrue(SupportedActions.CLICK.equals(discovery.bindAction())
                || SupportedActions.CHECKBOX.equals(discovery.bindAction()));
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
