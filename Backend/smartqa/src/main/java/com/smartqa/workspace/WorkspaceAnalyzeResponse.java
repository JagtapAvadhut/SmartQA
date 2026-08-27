package com.smartqa.workspace;

import com.smartqa.project.ProjectResponse;
import com.smartqa.testcase.TestCaseResponse;

public record WorkspaceAnalyzeResponse(
        ProjectResponse project,
        TestCaseResponse testCase
) {
}
