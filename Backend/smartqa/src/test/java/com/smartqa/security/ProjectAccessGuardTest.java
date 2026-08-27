package com.smartqa.security;

import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.SmartQaException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectAccessGuardTest {

    @Test
    void blocksCrossProjectAccess() {
        SmartQaException thrown = assertThrows(SmartQaException.class, () ->
                ProjectAccessGuard.assertSameProject(UUID.randomUUID(), UUID.randomUUID(), "memory"));
        assertEquals(ErrorCode.AUTHORIZATION_FAILED, thrown.errorCode());
    }
}
