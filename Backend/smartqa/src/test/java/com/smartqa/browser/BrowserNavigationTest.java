package com.smartqa.browser;

import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserNavigationTest {

    @Test
    void detectsHttpErrorPageTitles() {
        Page page = org.mockito.Mockito.mock(Page.class);
        org.mockito.Mockito.when(page.title()).thenReturn("500 Internal Server Error");
        org.mockito.Mockito.when(page.locator("body")).thenReturn(org.mockito.Mockito.mock(com.microsoft.playwright.Locator.class));
        assertTrue(BrowserNavigation.isErrorPage(page));
    }

    @Test
    void acceptsNormalApplicationTitle() {
        Page page = org.mockito.Mockito.mock(Page.class);
        org.mockito.Mockito.when(page.title()).thenReturn("OrangeHRM");
        assertFalse(BrowserNavigation.isErrorPage(page));
    }
}
