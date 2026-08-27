package com.smartqa.execution;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TempArtifactCleanupTest {

    @Test
    void deletesDirectoryTree() throws Exception {
        Path dir = Files.createTempDirectory("smartqa-cleanup-");
        Files.writeString(dir.resolve("a.txt"), "x");
        Path nested = Files.createDirectory(dir.resolve("nested"));
        Files.writeString(nested.resolve("b.txt"), "y");
        assertTrue(Files.exists(dir));
        TempArtifactCleanup.deleteQuietly(dir);
        assertFalse(Files.exists(dir));
    }
}
