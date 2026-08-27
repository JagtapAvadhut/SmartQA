package com.smartqa.debug;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SecretMasker {

    public static final String MASK = "***MASKED***";

    private static final Pattern SECRET_KEY = Pattern.compile(
            "(?i)(password|passwd|pwd|secret|token|api[_-]?key|authorization|cookie|session|credential|private[_-]?key)");
    private static final Pattern INLINE_SECRET = Pattern.compile(
            "(?i)(password|passwd|pwd|secret|token|api[_-]?key|authorization|cookie|session)=([^\\s&\"']+)");
    private static final Pattern BEARER = Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9._\\-+=/]+");
    private static final Pattern JWT = Pattern.compile(
            "\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b");
    private static final Pattern EMAIL = Pattern.compile(
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");
    private static final Pattern PHONE = Pattern.compile("\\b\\+?\\d[\\d\\s\\-]{8,}\\d\\b");
    private static final Pattern CARD = Pattern.compile("\\b(?:\\d[ -]*?){13,19}\\b");
    private static final int MAX_STRING = 8_000;

    private SecretMasker() {
    }

    public static Object mask(Object value) {
        return mask(value, 0);
    }

    public static String maskText(String value) {
        if (value == null) {
            return null;
        }
        String masked = INLINE_SECRET.matcher(value).replaceAll("$1=" + MASK);
        masked = BEARER.matcher(masked).replaceAll("$1" + MASK);
        masked = JWT.matcher(masked).replaceAll(MASK);
        masked = EMAIL.matcher(masked).replaceAll(MASK);
        masked = PHONE.matcher(masked).replaceAll(MASK);
        masked = CARD.matcher(masked).replaceAll(MASK);
        if (masked.length() > MAX_STRING) {
            return masked.substring(0, MAX_STRING) + "...[truncated, length=" + value.length() + "]";
        }
        return masked;
    }

    public static boolean looksSecret(String key, String value) {
        if (key != null && SECRET_KEY.matcher(key).find()) {
            return true;
        }
        if (key != null && key.toLowerCase(Locale.ROOT).contains("password")) {
            return true;
        }
        if (value != null && INLINE_SECRET.matcher(value).find()) {
            return true;
        }
        return false;
    }

    public static String maskValue(String key, String value) {
        if (value == null) {
            return null;
        }
        if (looksSecret(key, value)) {
            return MASK;
        }
        return maskText(value);
    }

    @SuppressWarnings("unchecked")
    private static Object mask(Object value, int depth) {
        if (value == null || depth > 8) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object nested = entry.getValue();
                if (looksSecret(key, nested == null ? null : String.valueOf(nested))) {
                    out.put(key, MASK);
                } else {
                    out.put(key, mask(nested, depth + 1));
                }
            }
            return out;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(mask(item, depth + 1));
            }
            return out;
        }
        if (value instanceof String text) {
            return maskText(text);
        }
        return value;
    }
}
