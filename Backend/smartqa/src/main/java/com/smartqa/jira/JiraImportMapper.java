package com.smartqa.jira;

import com.smartqa.testcase.TestCaseRequest;

/**
 * Jira issue text becomes a draft test case. It is never executed directly.
 */
public final class JiraImportMapper {

    private JiraImportMapper() {
    }

    public static TestCaseRequest toDraft(JiraIssueDraft issue) {
        if (issue == null || issue.issueKey() == null || issue.issueKey().isBlank()) {
            throw new IllegalArgumentException("Jira issue key is required");
        }
        String name = firstNonBlank(issue.summary(), issue.issueKey());
        String description = "Imported from Jira " + issue.issueKey() + ". Pending intent validation.";
        String language = firstNonBlank(issue.description(), issue.summary(), "Open the application and follow the issue summary.");
        return new TestCaseRequest(name, description, language);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
