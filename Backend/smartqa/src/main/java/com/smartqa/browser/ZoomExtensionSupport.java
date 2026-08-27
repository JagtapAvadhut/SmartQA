package com.smartqa.browser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Unpacks the SmartQA Chromium zoom extension from the classpath for {@code --load-extension}.
 */
public final class ZoomExtensionSupport {

    private static final AtomicReference<Path> CACHED = new AtomicReference<>();

    private ZoomExtensionSupport() {
    }

    public static Path ensureExtracted() {
        Path cached = CACHED.get();
        if (cached != null && Files.isDirectory(cached) && Files.isRegularFile(cached.resolve("manifest.json"))) {
            return cached;
        }
        try {
            Path dir = Files.createTempDirectory("smartqa-zoom-ext-");
            copyResource("smartqa-zoom-extension/manifest.json", dir.resolve("manifest.json"));
            copyResource("smartqa-zoom-extension/background.js", dir.resolve("background.js"));
            copyResource("smartqa-zoom-extension/content.js", dir.resolve("content.js"));
            CACHED.set(dir);
            return dir;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to extract SmartQA zoom extension", ex);
        }
    }

    public static List<String> chromiumExtensionArgs(Path extensionDir) {
        String path = extensionDir.toAbsolutePath().toString();
        return List.of(
                "--disable-extensions-except=" + path,
                "--load-extension=" + path,
                // Chrome 137+ may ignore --load-extension without this:
                "--disable-features=DisableLoadExtensionCommandLineSwitch"
        );
    }

    private static void copyResource(String resource, Path target) throws IOException {
        try (InputStream in = ZoomExtensionSupport.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Missing classpath resource: " + resource);
            }
            try (OutputStream out = Files.newOutputStream(target)) {
                in.transferTo(out);
            }
        }
    }
}
