package com.smartqa.debug;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class TraceId {

    public static final String HEADER = "X-SmartQA-Trace-Id";
    public static final String UNKNOWN = "SMARTQA-UNKNOWN";

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final SecureRandom RANDOM = new SecureRandom();

    private TraceId() {
    }

    public static String newId() {
        String stamp = LocalDateTime.now().format(STAMP);
        String suffix = String.format(Locale.ROOT, "%06x", RANDOM.nextInt(0x1000000));
        return "SMARTQA-" + stamp + "-" + suffix;
    }

    public static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return newId();
        }
        String cleaned = raw.trim();
        if (cleaned.length() > 80) {
            cleaned = cleaned.substring(0, 80);
        }
        if (!cleaned.matches("SMARTQA-[A-Za-z0-9._-]+")) {
            return newId();
        }
        return cleaned;
    }
}
