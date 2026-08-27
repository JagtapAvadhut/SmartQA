package com.smartqa.browser.mcp;

import com.smartqa.browser.BrowserExecutionProvider;
import com.smartqa.browser.ExecutionPlan;
import com.smartqa.browser.LocatorMemoryDocument;
import com.smartqa.common.config.SmartQaProperties;
import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.SmartQaException;
import com.smartqa.event.ProgressEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * Optional Playwright MCP execution provider. SmartQA does not spawn an MCP server;
 * it connects to an already-running one via SMARTQA_MCP_URL.
 */
@Component
public class PlaywrightMcpBrowserExecutionProvider implements BrowserExecutionProvider {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightMcpBrowserExecutionProvider.class);

    private final SmartQaProperties properties;
    private final WebClient webClient;

    public PlaywrightMcpBrowserExecutionProvider(SmartQaProperties properties) {
        this.properties = properties;
        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create()))
                .build();
    }

    @Override
    public String id() {
        return "mcp";
    }

    @Override
    public LocatorMemoryDocument execute(ExecutionPlan plan, Consumer<ProgressEvent> progress) {
        if (!properties.getMcp().isEnabled()) {
            throw new SmartQaException(
                    ErrorCode.EXECUTION_FAILED,
                    "Playwright MCP is disabled. Set smartqa.mcp.enabled=true and SMARTQA_MCP_URL.");
        }
        String url = properties.getMcp().getUrl();
        if (url == null || url.isBlank()) {
            throw new SmartQaException(ErrorCode.EXECUTION_FAILED, "SMARTQA_MCP_URL is not configured");
        }
        if (!reachable(url)) {
            throw new SmartQaException(
                    ErrorCode.EXECUTION_FAILED,
                    "Playwright MCP is not reachable at " + url
                            + ". Start the existing MCP server (do not create a second one) and keep provider=playwright until it is up.");
        }
        log.warn("mcp_provider_selected testCaseId={} tools={}", plan.testCaseId(), BoundedMcpTools.NAVIGATE);
        throw new SmartQaException(
                ErrorCode.EXECUTION_FAILED,
                "Playwright MCP is reachable at " + url
                        + ", but SmartQA v1 executes through the Playwright Java provider. Set smartqa.browser.provider=playwright.");
    }

    private boolean reachable(String url) {
        try {
            webClient.get()
                    .uri(url)
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(3))
                    .block();
            return true;
        } catch (RuntimeException ex) {
            log.info("mcp_unreachable url={}", url);
            return false;
        }
    }
}
