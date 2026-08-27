package com.smartqa.browser;

public record LocatorMemoryEntry(
        String stepId,
        String action,
        String semanticTarget,
        String resolvedLocator,
        String locatorType,
        double confidence,
        String elementText,
        String attributes,
        String pageUrl,
        boolean healed,
        String value,
        String screenshotPath,
        String locatorCloud,
        String controlType,
        String frameContext,
        String frameUrl,
        String frameName,
        String parentFrameContext,
        String targetPath
) {
    public LocatorMemoryEntry(
            String stepId,
            String action,
            String semanticTarget,
            String resolvedLocator,
            String locatorType,
            double confidence,
            String elementText,
            String attributes,
            String pageUrl,
            boolean healed,
            String value,
            String screenshotPath) {
        this(stepId, action, semanticTarget, resolvedLocator, locatorType, confidence, elementText, attributes, pageUrl,
                healed, value, screenshotPath, null, null, "main", "", "", "", "");
    }

    public LocatorMemoryEntry(
            String stepId,
            String action,
            String semanticTarget,
            String resolvedLocator,
            String locatorType,
            double confidence,
            String elementText,
            String attributes,
            String pageUrl,
            boolean healed,
            String value,
            String screenshotPath,
            String locatorCloud) {
        this(stepId, action, semanticTarget, resolvedLocator, locatorType, confidence, elementText, attributes, pageUrl,
                healed, value, screenshotPath, locatorCloud, null, "main", "", "", "", "");
    }

    public LocatorMemoryEntry(
            String stepId,
            String action,
            String semanticTarget,
            String resolvedLocator,
            String locatorType,
            double confidence,
            String elementText,
            String attributes,
            String pageUrl,
            boolean healed,
            String value,
            String screenshotPath,
            String locatorCloud,
            String controlType) {
        this(stepId, action, semanticTarget, resolvedLocator, locatorType, confidence, elementText, attributes, pageUrl,
                healed, value, screenshotPath, locatorCloud, controlType, "main", "", "", "", "");
    }
}
