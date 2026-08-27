package com.smartqa.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.smartqa.ai.AiPrompt;
import com.smartqa.ai.AiProvider;
import com.smartqa.browser.intelligence.BrowserIntelligenceService;
import com.smartqa.browser.intelligence.BrowserSnapshot;
import com.smartqa.browser.intelligence.DomExtractor;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaywrightContextTraversalTest {

    @Test
    void resolvesAndActsInsideNestedIframe() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("""
                    <html><body>
                      <iframe id='outer' srcdoc="<html><body>
                        <iframe id='inner' srcdoc='<html><body>
                          <label for=&quot;approveCheck&quot;>Approve Item</label>
                          <input id=&quot;approveCheck&quot; type=&quot;checkbox&quot; />
                        </body></html>'></iframe>
                      </body></html>"></iframe>
                    </body></html>
                    """);
            page.frameLocator("#outer").frameLocator("#inner").locator("#approveCheck").waitFor();

            ElementResolver resolver = new ElementResolver(
                    new NoopAiProvider(),
                    JsonMapper.builder().build(),
                    new BrowserIntelligenceService(new DomExtractor())
            );
            BrowserSnapshot snapshot = new BrowserIntelligenceService(new DomExtractor()).inspect(page, List.of());

            ElementResolver.ResolvedElement resolved = resolver.resolve(page, "click", "Approve Item", snapshot);
            assertNotEquals("main", resolved.frameContext(), "Expected iframe-aware resolution");
            resolved.locator().click();

            assertTrue(page.frameLocator("#outer").frameLocator("#inner").locator("#approveCheck").isChecked());
            browser.close();
        }
    }

    @Test
    void resolvesAndActsInsideShadowDom() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("""
                    <html><body>
                      <div id="status">pending</div>
                      <shadow-host id="root-host"></shadow-host>
                      <script>
                        class ShadowHost extends HTMLElement {
                          connectedCallback() {
                            const root = this.attachShadow({ mode: 'open' });
                            const nested = document.createElement('nested-host');
                            root.appendChild(nested);
                          }
                        }
                        class NestedHost extends HTMLElement {
                          connectedCallback() {
                            const root = this.attachShadow({ mode: 'open' });
                            const btn = document.createElement('button');
                            btn.textContent = 'Shadow Save';
                            btn.setAttribute('data-testid', 'shadow-save');
                            btn.addEventListener('click', () => {
                              document.getElementById('status').textContent = 'saved';
                            });
                            root.appendChild(btn);
                          }
                        }
                        customElements.define('shadow-host', ShadowHost);
                        customElements.define('nested-host', NestedHost);
                      </script>
                    </body></html>
                    """);
            page.locator("[data-testid='shadow-save']").waitFor();

            ElementResolver resolver = new ElementResolver(
                    new NoopAiProvider(),
                    JsonMapper.builder().build(),
                    new BrowserIntelligenceService(new DomExtractor())
            );
            BrowserSnapshot snapshot = new BrowserIntelligenceService(new DomExtractor()).inspect(page, List.of());

            ElementResolver.ResolvedElement resolved = resolver.resolve(page, "click", "Shadow Save", snapshot);
            assertTrue(!resolved.targetPath().isBlank(), "Expected resolved target path evidence");
            resolved.locator().click();
            assertEquals("saved", page.locator("#status").innerText());
            browser.close();
        }
    }

    private static final class NoopAiProvider implements AiProvider {
        @Override
        public String id() {
            return "noop";
        }

        @Override
        public Mono<String> generateText(AiPrompt prompt) {
            return Mono.just("{\"locatorType\":\"css\",\"resolvedLocator\":\"\"}");
        }

        @Override
        public <T> Mono<T> generateStructuredOutput(AiPrompt prompt, Class<T> type) {
            return Mono.empty();
        }
    }
}
