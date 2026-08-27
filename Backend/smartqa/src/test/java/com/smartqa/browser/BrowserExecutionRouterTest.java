package com.smartqa.browser;

import com.smartqa.common.config.SmartQaProperties;
import com.smartqa.common.error.SmartQaException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserExecutionRouterTest {

    @Test
    void rejectsMcpProviderForDemoBuild() {
        SmartQaProperties properties = new SmartQaProperties();
        properties.getBrowser().setProvider("playwright");
        BrowserExecutionRouter router = new BrowserExecutionRouter(
                new PlaywrightBrowserExecutionProvider(null, null, properties, null, null),
                properties
        );
        ExecutionPlan plan = new ExecutionPlan(UUID.randomUUID(), "demo", "https://example.com", List.of());

        SmartQaException ex = assertThrows(SmartQaException.class, () ->
                router.execute(plan, null, null, new BrowserExecutionOptions("mcp", true)));
        assertTrue(ex.getMessage().contains("disabled"), "Expected explicit MCP disabled reason");
    }
}
