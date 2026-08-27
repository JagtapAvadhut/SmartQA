package com.smartqa.jira;

import com.smartqa.audit.AuditService;
import com.smartqa.testcase.TestCaseResponse;
import com.smartqa.testcase.TestCaseService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
public class JiraImportService {

    private final TestCaseService testCaseService;
    private final AuditService auditService;

    public JiraImportService(TestCaseService testCaseService, AuditService auditService) {
        this.testCaseService = testCaseService;
        this.auditService = auditService;
    }

    public Mono<TestCaseResponse> importIssue(JiraIssueDraft issue) {
        return testCaseService.create(issue.projectId(), JiraImportMapper.toDraft(issue))
                .flatMap(created -> auditService.record(
                        issue.projectId(),
                        created.id(),
                        "jira-import",
                        "JIRA_IMPORTED",
                        issue.issueKey()
                ).thenReturn(created));
    }
}
