package com.smartqa.rag;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Strips secrets and sensitive values before embedding or storage.
 * Never stores passwords, tokens, OTPs, API keys, or PII as reusable memory.
 */
public final class KnowledgeSanitizer {

    private static final Pattern JWT = Pattern.compile(
            "\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b");
    private static final Pattern API_KEYISH = Pattern.compile(
            "(?i)\\b(api[_-]?key|access[_-]?token|authorization|bearer|secret|password|passwd|otp|jwt)\\s*[:=]\\s*\\S+");
    private static final Pattern LONG_TOKEN = Pattern.compile("\\b[A-Za-z0-9_\\-]{40,}\\b");
    private static final Pattern EMAIL = Pattern.compile(
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");
    private static final Pattern PHONE = Pattern.compile("\\b\\+?\\d[\\d\\s\\-]{8,}\\d\\b");

    private KnowledgeSanitizer() {
    }

    public static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String out = com.smartqa.debug.SecretMasker.maskText(raw);
        out = JWT.matcher(out).replaceAll("[REDACTED_JWT]");
        out = API_KEYISH.matcher(out).replaceAll("$1=[REDACTED]");
        out = EMAIL.matcher(out).replaceAll("[REDACTED_EMAIL]");
        out = PHONE.matcher(out).replaceAll("[REDACTED_PHONE]");
        out = LONG_TOKEN.matcher(out).replaceAll("[REDACTED_TOKEN]");
        out = redactKnownCredentialLiterals(out);
        out = out.replaceAll("\\s+", " ").trim();
        if (out.length() > 1200) {
            out = out.substring(0, 1200);
        }
        return out;
    }

    public static boolean looksSecretHeavy(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("password=") || lower.contains("api_key") || lower.contains("apikey")
                || lower.contains("bearer ") || lower.contains("authorization:")
                || lower.contains("access_token") || lower.contains("otp=")) {
            return true;
        }
        String sanitized = sanitize(text);
        // If sanitization removed most of the content, reject.
        return sanitized.length() < Math.min(20, text.length() / 3);
    }

    private static String redactKnownCredentialLiterals(String text) {
        // Common demo credentials must never become reusable generic memory.
        String out = text;
        out = out.replaceAll("(?i)\\badmin123\\b", "[REDACTED_PASSWORD]");
        out = out.replaceAll("(?i)\\bDemo@12345\\b", "[REDACTED_PASSWORD]");
        out = out.replaceAll("(?i)\\bMismatch@12345\\b", "[REDACTED_PASSWORD]");
        out = out.replaceAll("(?i)\\bRadha\\s+Gupta\\b", "[REDACTED_NAME]");
        return out;
    }
}
