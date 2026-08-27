package com.smartqa.browser.intelligence.memory;

import com.smartqa.common.config.SmartQaProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionMemoryServiceTest {

    @Test
    void remembersSanitizedHintsAndRejectsSecrets() {
        ExecutionMemoryService memory = new ExecutionMemoryService(new SmartQaProperties());
        memory.rememberSuccess("https://shop.example/list", "tc-1", "run-1", "click", "Brand AK",
                "checkbox", "Brand", "main", "", "role", "checkbox|AK", 0.9);
        memory.rememberSuccess("https://shop.example/list", "tc-1", "run-1", "fill", "password",
                "textbox", "form", "main", "", "css", "#password", 0.9);
        assertEquals(1, memory.size());
        assertEquals("Brand AK", memory.hints("https://shop.example/list", "click", "AK").getFirst().semanticTarget());
    }

    @Test
    void liveHostScopeDoesNotLeakAcrossSites() {
        ExecutionMemoryService memory = new ExecutionMemoryService(new SmartQaProperties());
        memory.rememberSuccess("https://a.example/", "t", "e", "click", "AK", "checkbox", "Brand", "main", "", "role", "x", 0.8);
        assertTrue(memory.hints("https://b.example/", "click", "AK").isEmpty());
    }

    @Test
    void recordsControlAndEvidenceMomentOnVerifiedSuccess() {
        ExecutionMemoryService memory = new ExecutionMemoryService(new SmartQaProperties());
        memory.rememberSuccess("https://shop.example/list", "tc-1", "run-1", "click", "Brand AK",
                "checkbox", "Brand", "main", "", "role", "checkbox|AK", 0.9,
                "CHECKBOX", "brand-container", "role", "moment-1");
        ExecutionMemoryRecord rec = memory.hints("https://shop.example/list", "click", "AK").getFirst();
        assertEquals("CHECKBOX", rec.controlType());
        assertEquals("brand-container", rec.container());
        assertEquals("moment-1", rec.evidenceMomentId());
    }
}
