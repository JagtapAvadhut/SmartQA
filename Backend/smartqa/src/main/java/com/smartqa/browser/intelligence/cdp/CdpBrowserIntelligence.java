package com.smartqa.browser.intelligence.cdp;

import com.microsoft.playwright.CDPSession;
import com.microsoft.playwright.Page;
import com.smartqa.common.config.SmartQaProperties;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import org.springframework.stereotype.Component;


/**
 * Real Chromium CDP evidence. Playwright remains the executor.
 * Firefox/WebKit: {@link CdpCapture#unavailable(String)} — never fail the run.
 */
@Component
public class CdpBrowserIntelligence {

    private final SmartQaProperties properties;

    public CdpBrowserIntelligence(SmartQaProperties properties) {
        this.properties = properties;
    }

    public CdpCapture capture(Page page) {
        return capture(page, null);
    }

    public CdpCapture capture(Page page, CDPSession existing) {
        if (page == null) {
            return CdpCapture.unavailable("page_null");
        }
        if (properties != null && !properties.getIntelligence().isCdpEnabled()) {
            return CdpCapture.unavailable("cdp_disabled");
        }
        CDPSession session = existing;
        boolean owned = false;
        try {
            if (session == null) {
                session = page.context().newCDPSession(page);
                owned = true;
                session.send("DOM.enable");
                session.send("Accessibility.enable");
            }
            com.google.gson.JsonObject params = new com.google.gson.JsonObject();
            params.add("computedStyles", new com.google.gson.JsonArray());
            params.addProperty("includePaintOrder", true);
            params.addProperty("includeDOMRects", true);
            com.google.gson.JsonObject snapshot = session.send("DOMSnapshot.captureSnapshot", params);
            com.google.gson.JsonObject ax = session.send("Accessibility.getFullAXTree");
            CdpCapture capture = CdpSnapshotParser.parseJson(
                    snapshot == null ? "{}" : snapshot.toString(),
                    ax == null ? "{}" : ax.toString(),
                    safeUrl(page),
                    safeTitle(page));
            TraceLogger.info("CDP", "CDP_SNAPSHOT_CAPTURED", "Chromium CDP snapshot captured", TraceMeta.of(
                    "nodeCount", capture.nodeCount(),
                    "axCount", capture.accessibility() == null ? 0 : capture.accessibility().size(),
                    "url", capture.documentUrl()
            ));
            return capture;
        } catch (RuntimeException ex) {
            TraceLogger.warn("CDP", "CDP_SNAPSHOT_UNAVAILABLE", "CDP snapshot unavailable; Playwright DOM remains source of truth", TraceMeta.of(
                    "reason", ex.getClass().getSimpleName()
            ));
            return CdpCapture.unavailable(ex.getClass().getSimpleName());
        } finally {
            if (owned && session != null) {
                try {
                    session.detach();
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

    private static String safeUrl(Page page) {
        try {
            return page.url();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static String safeTitle(Page page) {
        try {
            return page.title();
        } catch (RuntimeException ex) {
            return "";
        }
    }
}
