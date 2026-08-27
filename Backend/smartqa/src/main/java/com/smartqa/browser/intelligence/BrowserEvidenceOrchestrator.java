package com.smartqa.browser.intelligence;

import com.microsoft.playwright.Page;
import com.smartqa.browser.intelligence.cdp.CdpBrowserIntelligence;
import com.smartqa.browser.intelligence.cdp.CdpCapture;
import com.smartqa.common.config.SmartQaProperties;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Correlates Playwright DOM, CDP, accessibility, and network from one moment.
 */
@Component
public class BrowserEvidenceOrchestrator {

    private final CdpBrowserIntelligence cdp;
    private final SmartQaProperties properties;

    public BrowserEvidenceOrchestrator(CdpBrowserIntelligence cdp, SmartQaProperties properties) {
        this.cdp = cdp;
        this.properties = properties;
    }

    public CdpCapture captureCdpIfEnabled(Page page, boolean escalate) {
        return captureMoment(page, escalate).cdp();
    }

    /**
     * One coherent evidence moment: CDP/AX either captured now or marked unavailable.
     * Never pairs a later DOM with an older CDP snapshot.
     */
    public EvidenceMoment captureMoment(Page page, boolean escalate) {
        String momentId = UUID.randomUUID().toString();
        CdpCapture cdpCapture = captureCdpNow(page, escalate);
        String status = cdpCapture == null || !cdpCapture.captured()
                ? (cdpCapture == null ? "unavailable" : "unavailable:" + cdpCapture.fallbackReason())
                : "captured";
        TraceLogger.info("EVIDENCE", "EVIDENCE_MOMENT", "Captured coherent browser moment", TraceMeta.of(
                "momentId", momentId,
                "cdpStatus", status,
                "escalate", escalate
        ));
        return new EvidenceMoment(momentId, cdpCapture, status);
    }

    public record EvidenceMoment(String momentId, CdpCapture cdp, String cdpStatus) {
    }

    private CdpCapture captureCdpNow(Page page, boolean escalate) {
        if (properties == null || !properties.getIntelligence().isCdpEnabled()) {
            return CdpCapture.unavailable("cdp_disabled");
        }
        if (!escalate && !properties.getIntelligence().captureCdpOnInspect()) {
            return CdpCapture.unavailable("cdp_deferred");
        }
        if (escalate && !properties.getIntelligence().captureCdpOnEscalate()) {
            return CdpCapture.unavailable("cdp_off");
        }
        return cdp.capture(page);
    }
}
