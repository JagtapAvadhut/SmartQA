package com.smartqa.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FailureTaxonomyTest {

    @Test
    void canonicalizesNewFailureFamilies() {
        assertEquals(FailureTaxonomy.INCOMPLETE_CAPTURE, FailureTaxonomy.canonicalize("INCOMPLETE_SNAPSHOT"));
        assertEquals(FailureTaxonomy.NOT_CONFIDENT, FailureTaxonomy.canonicalize("LOW_CONFIDENCE"));
        assertEquals(FailureTaxonomy.DEPENDENCY_FAILED, FailureTaxonomy.canonicalize("PREREQUISITE_FAILED"));
        assertEquals(FailureTaxonomy.PROMPT_INJECTION, FailureTaxonomy.canonicalize("INJECTION"));
        assertEquals(FailureTaxonomy.AUTHORIZATION_FAILURE, FailureTaxonomy.canonicalize("AUTHZ"));
    }
}
