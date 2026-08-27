package com.smartqa.common.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads a local {@code .env} into JVM system properties (does not override existing env/props).
 * Keeps secrets out of committed YAML while allowing local multi-key Gemini failover.
 */
public final class DotEnvLoader {

    private DotEnvLoader() {
    }

    public static int loadIfPresent() {
        Path cwd = Path.of("").toAbsolutePath();
        Path[] candidates = {
                cwd.resolve(".env"),
                cwd.resolve(".env.local"),
                cwd.resolve("Backend").resolve("smartqa").resolve(".env"),
                cwd.resolve("smartqa").resolve(".env")
        };
        for (Path path : candidates) {
            if (Files.isRegularFile(path)) {
                return apply(path, parse(path));
            }
        }
        return 0;
    }

    static Map<String, String> parse(Path path) {
        Map<String, String> values = new LinkedHashMap<>();
        try {
            for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = line.substring(0, eq).trim();
                String value = stripQuotes(line.substring(eq + 1).trim());
                if (!key.isEmpty()) {
                    values.put(key, value);
                }
            }
        } catch (IOException ignored) {
            return Map.of();
        }
        return values;
    }

    static int apply(Path path, Map<String, String> values) {
        int applied = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey();
            if (System.getenv(key) != null && !System.getenv(key).isBlank()) {
                continue;
            }
            if (System.getProperty(key) != null && !System.getProperty(key).isBlank()) {
                continue;
            }
            System.setProperty(key, entry.getValue());
            applied++;
        }
        if (applied > 0) {
            System.out.println("Loaded " + applied + " values from " + path.toAbsolutePath());
        }
        return applied;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
