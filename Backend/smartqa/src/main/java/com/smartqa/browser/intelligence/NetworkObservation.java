package com.smartqa.browser.intelligence;

import java.time.Instant;

/**
 * Masked network fact. Bodies and secrets are never stored.
 */
public record NetworkObservation(
        String method,
        String url,
        int status,
        String resourceType,
        boolean failed,
        String failure,
        Instant at
) {
    public String classification() {
        if (failed || status == 0) {
            return "NETWORK_FAILURE";
        }
        if (status == 401 || status == 403) {
            return "AUTH_FAILURE";
        }
        if (status >= 500) {
            return "API_FAILURE";
        }
        if (status >= 400) {
            return "RESOURCE_FAILURE";
        }
        return "OK";
    }

    public String compact() {
        return method + " " + url + " status=" + status + (failed ? " FAILED" : "");
    }
}
