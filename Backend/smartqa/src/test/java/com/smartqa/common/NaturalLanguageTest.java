package com.smartqa.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NaturalLanguageTest {

    @Test
    void collapseRepeatedOrangeHrmBlock() {
        String once = """
                Open the OrangeHRM application.
                
                Click Login.
                
                Verify text as "Passwords do not match".
                """.trim();
        String doubled = once + "\n\n" + once;
        assertEquals(once, NaturalLanguage.normalize(doubled));
    }

    @Test
    void leavesSingleCopyUnchanged() {
        String once = "Open https://example.com\nVerify Example Domain";
        assertEquals(once, NaturalLanguage.normalize(once));
    }
}
