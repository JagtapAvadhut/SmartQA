package com.smartqa.jira;

import com.smartqa.testcase.TestCaseRequest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JiraImportMapperTest {

    @Test
    void mapsIssueToDraftWithoutExecutingIt() {
        JiraIssueDraft issue = new JiraIssueDraft("QA-12", "Login smoke", "Open login and sign in", UUID.randomUUID());
        TestCaseRequest draft = JiraImportMapper.toDraft(issue);
        assertEquals("Login smoke", draft.name());
        assertTrue(draft.description().contains("QA-12"));
        assertTrue(draft.naturalLanguage().contains("Open login"));
    }
}
