package com.smartqa.intent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IntentScenario(
        String id,
        String name,
        List<IntentStep> steps
) {
}
