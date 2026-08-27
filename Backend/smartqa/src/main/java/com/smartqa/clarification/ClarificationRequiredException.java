package com.smartqa.clarification;

import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.SmartQaException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ClarificationRequiredException extends SmartQaException {

    private final UUID clarificationId;
    private final List<Map<String, Object>> candidates;

    public ClarificationRequiredException(UUID clarificationId, String message, List<Map<String, Object>> candidates) {
        super(ErrorCode.CLARIFICATION_REQUIRED, message);
        this.clarificationId = clarificationId;
        this.candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    public UUID clarificationId() {
        return clarificationId;
    }

    public List<Map<String, Object>> candidates() {
        return candidates;
    }
}
