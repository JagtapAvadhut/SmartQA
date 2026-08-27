package com.smartqa.browser;

public record BrowserExecutionOptions(
        String provider,
        Boolean headless
) {
}
