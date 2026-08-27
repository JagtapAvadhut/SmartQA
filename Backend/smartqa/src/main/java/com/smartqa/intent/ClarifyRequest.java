package com.smartqa.intent;

import java.util.List;

public record ClarifyRequest(List<ClarificationAnswer> answers) {
    public record ClarificationAnswer(String questionId, String selectedOption) {
    }
}
