package com.smartqa.browser.intelligence;

import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.SmartQaException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocatorContractTest {

    @Test
    void rejectsEqualsOnlyAndEmptySelectors() {
        assertFalse(LocatorContract.validate("css", "=", "test").valid());
        assertFalse(LocatorContract.validate("css", "==", "test").valid());
        assertFalse(LocatorContract.validate("css", "  ", "test").valid());
        assertFalse(LocatorContract.validate("css", null, "test").valid());
        assertFalse(LocatorContract.validate("", "=", "test").valid());
        assertFalse(LocatorContract.validate("role", "button", "test").valid());
    }

    @Test
    void requireValidEmitsLocatorInvalid() {
        SmartQaException ex = assertThrows(SmartQaException.class,
                () -> LocatorContract.requireValid("css", "=", "unit-test"));
        assertEquals(ErrorCode.LOCATOR_INVALID, ex.errorCode());
        assertTrue(ex.getMessage().contains("LOCATOR_INVALID"));
        assertTrue(ex.getMessage().contains("value="));
        assertTrue(ex.getMessage().contains("source=unit-test"));
    }

    @Test
    void builderNeverEmitsEqualsOnlyCssFromMalformedActionableSelector() {
        ElementCandidate element = ElementCandidate.fromMap(Map.of(
                "tag", "button",
                "text", "Login",
                "visible", true,
                "enabled", true,
                "clickable", true,
                "actionableSelector", "=",
                "targetPath", "="
        ), 0);
        List<RankedLocator> locators = LocatorSelectorBuilder.fromElement(element, "click");
        assertTrue(locators.stream().noneMatch(item -> "=".equals(item.resolvedLocator())));
        assertTrue(locators.stream().anyMatch(item -> "text".equals(item.locatorType())
                && "Login".equals(item.resolvedLocator())));
    }

    @Test
    void acceptsStableRoleAndCss() {
        assertTrue(LocatorContract.isUsable("role", "button|Login"));
        assertTrue(LocatorContract.isUsableCss("#header-actions > button:nth-of-type(3)"));
    }
}
