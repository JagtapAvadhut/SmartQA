package com.smartqa.intent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IntentContract(
        String status,
        String testName,
        Double confidence,
        List<IntentScenario> scenarios,
        List<ClarificationQuestion> clarifications
) {
    public static final String READY = "READY";
    public static final String NEEDS_CLARIFICATION = "NEEDS_CLARIFICATION";
}
