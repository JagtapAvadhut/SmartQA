package com.smartqa.browser.intelligence;

import com.microsoft.playwright.CDPSession;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Browser diagnostics collected through Playwright events, with CDP enablement
 * for Chromium when available. Callers never talk to CDP directly.
 */
public final class PageDiagnostics {

    private final List<String> consoleErrors = new CopyOnWriteArrayList<>();
    private final List<String> failedRequests = new CopyOnWriteArrayList<>();
    private final List<String> networkEvents = new CopyOnWriteArrayList<>();
    private final List<NetworkObservation> observations = new CopyOnWriteArrayList<>();
    private CDPSession cdpSession;

    public void attach(Page page) {
        page.onConsoleMessage(message -> {
            String type = message.type() == null ? "" : message.type().toLowerCase();
            if ("error".equals(type) || "assert".equals(type)) {
                consoleErrors.add(message.text());
            }
        });
        page.onPageError(error -> consoleErrors.add(String.valueOf(error)));
        page.onRequestFailed(request -> {
            String masked = SensitiveDataMasker.maskUrl(request.url());
            failedRequests.add(masked + " " + (request.failure() == null ? "" : request.failure()));
            observations.add(new NetworkObservation(
                    request.method(),
                    masked,
                    0,
                    request.resourceType(),
                    true,
                    request.failure(),
                    java.time.Instant.now()
            ));
            trimObservations();
        });
        page.onResponse(response -> {
            int status = response.status();
            String masked = SensitiveDataMasker.maskUrl(response.url());
            if (status >= 400) {
                networkEvents.add(response.request().method() + " " + masked + " status=" + status);
            }
            if (status >= 400 || "xhr".equalsIgnoreCase(response.request().resourceType())
                    || "fetch".equalsIgnoreCase(response.request().resourceType())) {
                observations.add(new NetworkObservation(
                        response.request().method(),
                        masked,
                        status,
                        response.request().resourceType(),
                        status == 0,
                        null,
                        java.time.Instant.now()
                ));
                trimObservations();
            }
        });
        try {
            cdpSession = page.context().newCDPSession(page);
            cdpSession.send("Runtime.enable");
            cdpSession.send("Network.enable");
            cdpSession.send("Page.enable");
        } catch (RuntimeException ignored) {
            // Firefox / WebKit do not expose CDP.
        }
    }

    public List<String> consoleErrors() {
        return List.copyOf(consoleErrors);
    }

    public List<String> failedRequests() {
        return List.copyOf(failedRequests);
    }

    public List<String> networkEvents() {
        return List.copyOf(networkEvents);
    }

    public List<NetworkObservation> observations() {
        return List.copyOf(observations);
    }

    public CDPSession cdpSession() {
        return cdpSession;
    }

    public String compactNetwork(int limit) {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (int i = observations.size() - 1; i >= 0 && n < Math.max(1, limit); i--) {
            NetworkObservation obs = observations.get(i);
            if (!"OK".equals(obs.classification())) {
                sb.append(obs.compact()).append('\n');
                n++;
            }
        }
        return sb.toString();
    }

    private void trimObservations() {
        while (observations.size() > 80) {
            observations.remove(0);
        }
    }

    public List<String> compactErrors() {
        List<String> all = new ArrayList<>();
        all.addAll(consoleErrors);
        all.addAll(failedRequests);
        all.addAll(networkEvents);
        return all.size() > 12 ? all.subList(0, 12) : all;
    }

    public void close() {
        try {
            if (cdpSession != null) {
                cdpSession.detach();
            }
        } catch (RuntimeException ignored) {
            // already closed
        }
    }
}
