package com.smartqa.browser.intelligence;

import java.util.Locale;
import java.util.Set;

public final class SensitiveDataMasker {

    private static final Set<String> SENSITIVE_QUERY = Set.of(
            "token", "access_token", "refresh_token", "password", "passwd", "secret",
            "api_key", "apikey", "authorization", "auth", "session", "cookie"
    );

    private SensitiveDataMasker() {
    }

    public static String maskUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        int q = url.indexOf('?');
        if (q < 0) {
            return trim(url, 180);
        }
        String base = url.substring(0, q);
        String query = url.substring(q + 1);
        StringBuilder out = new StringBuilder(base).append('?');
        boolean first = true;
        for (String part : query.split("&")) {
            int eq = part.indexOf('=');
            String name = (eq < 0 ? part : part.substring(0, eq)).toLowerCase(Locale.ROOT);
            if (!first) {
                out.append('&');
            }
            first = false;
            if (SENSITIVE_QUERY.contains(name) || name.contains("token") || name.contains("key")) {
                out.append(eq < 0 ? part : part.substring(0, eq)).append("=***");
            } else {
                out.append(part);
            }
        }
        return trim(out.toString(), 180);
    }

    public static boolean sensitiveHeader(String name) {
        if (name == null) {
            return false;
        }
        String n = name.toLowerCase(Locale.ROOT);
        return n.contains("authorization") || n.contains("cookie") || n.contains("token")
                || n.contains("api-key") || n.contains("api_key") || n.contains("secret");
    }

    private static String trim(String v, int max) {
        return v.length() <= max ? v : v.substring(0, max) + "…";
    }
}
