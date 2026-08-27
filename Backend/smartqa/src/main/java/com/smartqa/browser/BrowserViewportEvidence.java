package com.smartqa.browser;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Real browser window/viewport metrics captured from the live page (never fabricated).
 */
public record BrowserViewportEvidence(
        String browser,
        boolean headless,
        boolean maximizeRequested,
        int innerWidth,
        int innerHeight,
        int outerWidth,
        int outerHeight,
        int screenWidth,
        int screenHeight,
        int availableScreenWidth,
        int availableScreenHeight,
        double devicePixelRatio
) {
    public Map<String, Object> toTraceMeta() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("browser", browser);
        meta.put("headless", headless);
        meta.put("maximizeRequested", maximizeRequested);
        meta.put("innerWidth", innerWidth);
        meta.put("innerHeight", innerHeight);
        meta.put("outerWidth", outerWidth);
        meta.put("outerHeight", outerHeight);
        meta.put("screenWidth", screenWidth);
        meta.put("screenHeight", screenHeight);
        meta.put("availableScreenWidth", availableScreenWidth);
        meta.put("availableScreenHeight", availableScreenHeight);
        meta.put("devicePixelRatio", devicePixelRatio);
        return meta;
    }
}
