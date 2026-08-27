package com.smartqa.jira;

import java.util.UUID;

public record JiraIssueDraft(
        String issueKey,
        String summary,
        String description,
        UUID projectId
) {
}
