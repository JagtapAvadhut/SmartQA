package com.smartqa.browser.intelligence;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhysicalControlTest {

    @Test
    void projectsCheckboxCapabilitiesFromCandidate() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("candidateId", "ctrl-1");
        raw.put("tag", "input");
        raw.put("role", "checkbox");
        raw.put("inputType", "checkbox");
        raw.put("accessibleName", "AK");
        raw.put("text", "AK");
        raw.put("parentId", "brand-container");
        raw.put("containerId", "brand");
        raw.put("visible", true);
        raw.put("enabled", true);
        raw.put("clickable", true);
        ElementCandidate candidate = ElementCandidate.fromMap(raw, 0);
        PhysicalControl control = PhysicalControl.from(candidate);
        assertEquals("ctrl-1", control.controlId());
        assertEquals(ControlType.CHECKBOX, control.controlType());
        assertTrue(control.capabilities().contains(ControlCapability.CHECK));
        assertEquals("brand-container", control.parentId());
        assertEquals("brand", control.containerId());
    }
}
