package com.smartqa.generation;

import com.smartqa.ai.AiPrompt;
import com.smartqa.ai.AiProvider;
import com.smartqa.browser.LocatorMemoryDocument;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import com.smartqa.common.json.JsonSupport;
import com.smartqa.intent.IntentContract;
import com.smartqa.testcase.TestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class PlaywrightJavaCodeGenerator {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightJavaCodeGenerator.class);

    private static final String SYSTEM_PROMPT = """
            You generate a single Playwright for Java JUnit 5 test class.
            Use only the provided locator memory. Do not invent CSS selectors or XPath.
            Rules:
            - Output Java source only, no markdown unless fenced.
            - Class name must be exactly the provided class name.
            - Package: omit package declaration (default package).
            - Imports must include com.microsoft.playwright.* and org.junit.jupiter.api.*.
            - Use Playwright.create() and Chromium.
            - Respect System properties smartqa.browser.headless (default true), smartqa.browser.maximize-headed,
              smartqa.browser.zoom-percent (default 50 for headed page zoom — Chrome Zoom, NOT window resize),
              smartqa.browser.headless-viewport-width/height. When headless: set a fixed viewport. When headed and
              maximize-headed: use --start-maximized and setViewportSize(null). After page creation, apply Chromium
              page zoom via ControlOrMeta+0 then ControlOrMeta+- to reach zoom-percent (re-apply on navigation).
              Do not resize the OS window to 50%. Do not hardcode a small fixed viewport for headed.
            - Close browser and playwright in finally or try-with-resources.
            - One @Test method named the provided method name.
            - Map locatorType text -> page.getByText(...)
            - Map locatorType label -> page.getByLabel(...)
            - Map locatorType placeholder -> page.getByPlaceholder(...)
            - Map locatorType css -> page.locator(...)
            - Map locatorType role with value role|name -> page.getByRole(...)
            - Map locatorType title -> assert page.title()
            - Assertions via org.junit.jupiter.api.Assertions.
            - Do not invent selectors or elements.
            - Do not call Runtime, ProcessBuilder, files deletion, sockets, or native code.
            - Do not use Thread.sleep or waitForTimeout.
            - Clicks must use locator.click(new Locator.ClickOptions().setNoWaitAfter(true)) so a pending navigation cannot hang the test.
            - After a click that may navigate, wait for DOMCONTENTLOADED with a short timeout in try/catch. Do not wait for networkidle.
            - Prefer getByLabel / getByRole over class-based CSS. Never use a CSS locator that can match more than one element.
            - CRITICAL: If the locator memory entry has controlType=CUSTOM_DROPDOWN, COMBOBOX, or LISTBOX, \
            NEVER use selectOption(). Click the labeled control (getByLabel(semanticTarget)), wait for a visible option, \
            then click the option via getByRole(AriaRole.OPTION) or getByText().
            - Only use selectOption() when controlType=NATIVE_SELECT or controlType is absent.
            """;

    private final AiProvider aiProvider;

    public PlaywrightJavaCodeGenerator(AiProvider aiProvider) {
        this.aiProvider = aiProvider;
    }

    public Mono<String> generate(
            TestCase testCase,
            IntentContract intent,
            LocatorMemoryDocument locatorMemory,
            String className) {
        String user = AutomationPromptBuilder.build(testCase, intent, locatorMemory, className);
        if (user.isBlank()) {
            return Mono.error(new IllegalStateException("Code generation prompt was empty"));
        }
        log.info("code_generation_prompt_ready testCaseId={} chars={}", testCase.getId(), user.length());
        TraceLogger.info("CODEGEN", "CODE_GENERATION_PROMPT_BUILT", "Code generation prompt built", TraceMeta.of(
                "promptLength", SYSTEM_PROMPT.length() + user.length(),
                "className", className,
                "steps", locatorMemory == null || locatorMemory.entries() == null ? 0 : locatorMemory.entries().size()
        ));
        long started = System.nanoTime();
        return aiProvider.generateText(new AiPrompt(SYSTEM_PROMPT, user, false))
                .map(JsonSupport::extractJava)
                .doOnSuccess(code -> TraceLogger.info("CODEGEN", "CODE_GENERATION_COMPLETED", "AI code received",
                        (System.nanoTime() - started) / 1_000_000,
                        TraceMeta.of("codeLength", code == null ? 0 : code.length())))
                .doOnError(error -> TraceLogger.error("CODEGEN", "CODE_GENERATION_FAILED", "AI code generation failed", error,
                        (System.nanoTime() - started) / 1_000_000, TraceMeta.of("className", className)));
    }

    static String buildUserPrompt(
            TestCase testCase,
            IntentContract intent,
            LocatorMemoryDocument locatorMemory,
            String className) {
        return AutomationPromptBuilder.build(testCase, intent, locatorMemory, className);
    }
}
