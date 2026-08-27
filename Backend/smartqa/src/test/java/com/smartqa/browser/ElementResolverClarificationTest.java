package com.smartqa.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.smartqa.browser.intelligence.BrowserIntelligenceService;
import com.smartqa.browser.intelligence.DomExtractor;
import com.smartqa.clarification.ClarificationRequiredException;
import com.smartqa.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElementResolverClarificationTest {

    @Test
    void twoEquallySupportedSaveButtonsDoNotClickEither() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("""
                    <html><body>
                      <form id="dialog"><button type="submit">Save</button></form>
                      <header><button type="button">Save</button></header>
                      <script>window.__clicked = [];
                        document.querySelectorAll('button').forEach(btn => {
                          btn.addEventListener('click', () => window.__clicked.push(btn.parentElement.id || btn.parentElement.tagName));
                        });
                      </script>
                    </body></html>
                    """);
            BrowserIntelligenceService intelligence = new BrowserIntelligenceService(new DomExtractor());
            ElementResolver resolver = new ElementResolver(
                    new NoopAi(),
                    tools.jackson.databind.json.JsonMapper.builder().build(),
                    intelligence
            );
            ClarificationRequiredException thrown = assertThrows(
                    ClarificationRequiredException.class,
                    () -> resolver.resolve(page, "click", "Save")
            );
            assertEquals(ErrorCode.CLARIFICATION_REQUIRED, thrown.errorCode());
            assertTrue(thrown.candidates().size() >= 2);
            assertEquals(0, page.evaluate("window.__clicked.length"));
            assertNotEquals("clicked", page.evaluate("document.body.dataset.state || ''"));
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
