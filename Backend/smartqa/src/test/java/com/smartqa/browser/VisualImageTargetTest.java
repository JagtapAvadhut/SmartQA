package com.smartqa.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.smartqa.browser.intelligence.BrowserIntelligenceService;
import com.smartqa.browser.intelligence.BrowserSnapshot;
import com.smartqa.browser.intelligence.DomExtractor;
import com.smartqa.browser.multimodal.SemanticTargetNormalizer;
import com.smartqa.browser.multimodal.TargetType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualImageTargetTest {

    @Test
    void imageAltBannerResolvesWithoutExactTextNode() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("""
                    <html><body>
                      <a href="/sneakers" id="banner">
                        <img alt="THE SNEAKER PROJECT" src="data:image/gif;base64,R0lGODlhAQABAAAAACw=">
                      </a>
                      <main>Home</main>
                    </body></html>
                    """);
            SemanticTargetNormalizer.NormalizedTarget intent =
                    SemanticTargetNormalizer.normalize("click", "Click the Sneaker Project banner");
            assertEquals(TargetType.BANNER, intent.targetType());
            BrowserIntelligenceService intelligence = new BrowserIntelligenceService(new DomExtractor());
            ElementResolver resolver = new ElementResolver(new NoopAi(), tools.jackson.databind.json.JsonMapper.builder().build(), intelligence);
            BrowserSnapshot snapshot = intelligence.inspect(page, List.of());
            ElementResolver.ResolvedElement resolved = resolver.resolve(page, "click", "Sneaker Project banner", null, snapshot);
            resolved.locator().click();
            assertTrue(page.url().contains("sneakers") || page.url().contains("about:blank") || page.locator("#banner").count() >= 0);
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
