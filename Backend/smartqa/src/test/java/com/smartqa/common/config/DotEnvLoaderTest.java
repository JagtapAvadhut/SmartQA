package com.smartqa.common.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DotEnvLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void parseIgnoresCommentsAndSupportsQuotes() throws Exception {
        Path env = tempDir.resolve(".env");
        Files.writeString(env, """
                # comment
                GEMINI_API_KEY=key-one
                GEMINI_API_KEYS="key-two,key-three"
                EMPTY=
                """);
        Map<String, String> parsed = DotEnvLoader.parse(env);
        assertEquals("key-one", parsed.get("GEMINI_API_KEY"));
        assertEquals("key-two,key-three", parsed.get("GEMINI_API_KEYS"));
        assertTrue(parsed.containsKey("EMPTY"));
    }
}
