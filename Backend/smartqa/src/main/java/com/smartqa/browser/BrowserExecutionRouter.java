package com.smartqa.browser;

import com.smartqa.common.config.SmartQaProperties;
import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.SmartQaException;
import com.smartqa.event.ProgressEvent;
import com.smartqa.execution.cancel.CancellationToken;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.function.Consumer;

@Component
@Primary
public class BrowserExecutionRouter implements BrowserExecutionProvider {

    private final PlaywrightBrowserExecutionProvider playwright;
    private final SmartQaProperties properties;

    public BrowserExecutionRouter(
            PlaywrightBrowserExecutionProvider playwright,
            SmartQaProperties properties) {
        this.playwright = playwright;
        this.properties = properties;
    }

    @Override
    public String id() {
        return "router";
    }

    @Override
    public LocatorMemoryDocument execute(ExecutionPlan plan, Consumer<ProgressEvent> progress) {
        return execute(plan, progress, null);
    }

    @Override
    public LocatorMemoryDocument execute(ExecutionPlan plan, Consumer<ProgressEvent> progress, CancellationToken cancellationToken) {
        return execute(plan, progress, cancellationToken, null);
    }

    @Override
    public LocatorMemoryDocument execute(
            ExecutionPlan plan,
            Consumer<ProgressEvent> progress,
            CancellationToken cancellationToken,
            BrowserExecutionOptions options) {
        String provider = properties.getBrowser().getProvider();
        if (options != null && options.provider() != null && !options.provider().isBlank()) {
            provider = options.provider();
        }
        String id = provider == null ? "playwright" : provider.trim().toLowerCase(Locale.ROOT);
        return switch (id) {
            case "mcp", "playwright-mcp", "playwrightmcp" -> throw new SmartQaException(
                    ErrorCode.VALIDATION_FAILED,
                    "Playwright MCP execution is disabled in this demo build. Use provider=playwright.");
            case "playwright", "playwright-java", "playwright_java" -> playwright.execute(plan, progress, cancellationToken, options);
            default -> throw new SmartQaException(
                    ErrorCode.VALIDATION_FAILED,
                    "Unknown browser provider: " + provider + " (use playwright)");
        };
    }
}
