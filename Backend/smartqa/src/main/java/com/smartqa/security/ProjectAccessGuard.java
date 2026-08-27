package com.smartqa.security;

import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.SmartQaException;

import java.util.UUID;

/**
 * Service-layer project isolation. Controllers must not be the only check.
 */
public final class ProjectAccessGuard {

    private ProjectAccessGuard() {
    }

    public static void assertSameProject(UUID expectedProjectId, UUID actualProjectId, String resource) {
        if (expectedProjectId == null || actualProjectId == null || !expectedProjectId.equals(actualProjectId)) {
            throw new SmartQaException(
                    ErrorCode.AUTHORIZATION_FAILED,
                    "Cross-project access blocked for " + (resource == null ? "resource" : resource));
        }
    }
}
