package com.smartqa.project;

import jakarta.validation.constraints.NotBlank;

public record ProjectRequest(
        @NotBlank(message = "Project name is required") String name,
        String description,
        @NotBlank(message = "Application URL is required") String applicationUrl,
        String environment
) {
}
