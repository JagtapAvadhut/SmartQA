package com.smartqa.common.json;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonSupportTest {

    @Test
    void extractsJsonFromMarkdownFence() {
        String extracted = JsonSupport.extractJson("""
                ```json
                {"status":"READY"}
                ```
                """);
        assertEquals("{\"status\":\"READY\"}", extracted);
    }

    @Test
    void extractsJavaFromFence() {
        String extracted = JsonSupport.extractJava("""
                ```java
                public class FooTest {}
                ```
                """);
        assertTrue(extracted.contains("public class FooTest"));
    }
}
