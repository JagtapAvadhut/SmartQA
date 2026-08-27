package com.smartqa.intent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClarificationQuestion(
        String id,
        String question,
        List<String> options
) {
}
