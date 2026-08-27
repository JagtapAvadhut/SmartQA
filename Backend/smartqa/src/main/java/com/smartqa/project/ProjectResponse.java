package com.smartqa.project;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String name,
        String description,
        String applicationUrl,
        String environment,
        long testCaseCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ProjectResponse from(Project project, long testCaseCount) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getApplicationUrl(),
                project.getEnvironment(),
                testCaseCount,
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}
