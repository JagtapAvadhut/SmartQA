package com.smartqa.browser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerifyExpectationTest {

    @Test
    void usesValueWhenAssertionIsContainsOperator() {
        assertEquals("Example Domain", VerifyExpectation.expectedText("contains", "Example Domain"));
        assertTrue(VerifyExpectation.isOperator("contains"));
        assertTrue(VerifyExpectation.isPageLevelTarget("page content"));
        assertTrue(VerifyExpectation.isSpecificExpectedText("Example Domain"));
        assertFalse(VerifyExpectation.isSpecificExpectedText("contains"));
        assertTrue(VerifyExpectation.isOperator("text"));
        assertEquals(null, VerifyExpectation.expectedText("text", null));
        assertFalse(VerifyExpectation.isSpecificExpectedText("text"));
    }

    @Test
    void classifiesRecordOutcomesWithoutRewritingLiteralAssertions() {
        assertEquals(VerifyExpectation.RecordOutcome.PRESENT,
                VerifyExpectation.recordOutcome("matching employee record"));
        assertEquals(VerifyExpectation.RecordOutcome.LITERAL,
                VerifyExpectation.recordOutcome("No Records Found"));
        assertEquals(VerifyExpectation.RecordOutcome.LITERAL,
                VerifyExpectation.recordOutcome("Record Found"));
        assertEquals(VerifyExpectation.RecordOutcome.ABSENT,
                VerifyExpectation.recordOutcome("no matching records"));
        assertEquals(VerifyExpectation.RecordOutcome.LITERAL,
                VerifyExpectation.recordOutcome("Dashboard"));
        assertEquals(VerifyExpectation.RecordOutcome.LITERAL,
                VerifyExpectation.recordOutcome("Invalid credentials"));
        assertTrue(VerifyExpectation.textVariants("Record Found").contains("Records Found"));
    }

    @Test
    void usesAssertionWhenItIsLiteralText() {
        assertEquals("Login", VerifyExpectation.expectedText("Login", null));
        assertFalse(VerifyExpectation.isPageLevelTarget("Login button"));
    }

    @Test
    void stripsTextAsPrefixAndWrappingQuotes() {
        assertEquals("5 cars matching your search",
                VerifyExpectation.expectedText("text as '5 cars matching your search'", null));
        assertEquals("5 cars matching your search",
                VerifyExpectation.expectedText("contains", "text as '5 cars matching your search'"));
    }
}
