package com.smartqa.testcase;

import jakarta.validation.constraints.NotBlank;

public record TestCaseRequest(
        @NotBlank(message = "Test name is required") String name,
        String description,
        @NotBlank(message = "Natural-language steps are required") String naturalLanguage
) {
}
