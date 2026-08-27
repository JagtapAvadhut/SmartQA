package com.smartqa.execution;

public record ExecutionStartRequest(
        String executionProvider,
        String browserMode,
        Boolean headless
) {
}
