package com.smartqa.browser.intelligence;

public record RankedLocator(
        String locatorType,
        String resolvedLocator,
        double confidence,
        String reason,
        double stabilityScore
) {
}
