package com.smartqa.generation;

import com.smartqa.browser.LocatorMemoryDocument;
import com.smartqa.browser.LocatorMemoryEntry;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generated tests may only navigate to URLs that were recorded in locator memory.
 * Removes unrecorded page.navigate calls from @Test methods (not helper methods).
 */
public final class GeneratedCodeNavigationContract {

    private static final Pattern NAVIGATE = Pattern.compile(
            "\\b([A-Za-z_][A-Za-z0-9_]*)\\.navigate\\s*\\(\\s*\"([^\"]+)\"\\s*\\)\\s*;");

    private GeneratedCodeNavigationContract() {
    }

    public static String stripUnrecordedNavigations(String source, LocatorMemoryDocument memory) {
        if (source == null || source.isBlank() || memory == null) {
            return source;
        }
        Set<String> allowed = allowedUrls(memory);
        if (allowed.isEmpty()) {
            return source;
        }
        Matcher matcher = NAVIGATE.matcher(source);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String url = matcher.group(2);
            if (allowed(url, allowed)) {
                matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group()));
            } else {
                matcher.appendReplacement(out, "/* unrecorded navigation removed */");
            }
        }
        matcher.appendTail(out);
        return out.toString();
    }

    static Set<String> allowedUrls(LocatorMemoryDocument memory) {
        Set<String> allowed = new LinkedHashSet<>();
        if (memory.entries() == null) {
            return allowed;
        }
        for (LocatorMemoryEntry entry : memory.entries()) {
            if (entry == null) {
                continue;
            }
            if (isNavigate(entry.action()) && notBlank(entry.resolvedLocator())) {
                allowed.add(normalize(entry.resolvedLocator()));
            }
            if (notBlank(entry.pageUrl())) {
                allowed.add(normalize(entry.pageUrl()));
            }
        }
        return allowed;
    }

    private static boolean isNavigate(String action) {
        if (action == null) {
            return false;
        }
        String lower = action.toLowerCase(Locale.ROOT);
        return "navigate".equals(lower) || "open".equals(lower) || "goto".equals(lower);
    }

    private static boolean allowed(String url, Set<String> allowed) {
        String normalized = normalize(url);
        if (allowed.contains(normalized)) {
            return true;
        }
        for (String candidate : allowed) {
            if (sameOriginPrefix(normalized, candidate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameOriginPrefix(String left, String right) {
        int leftHost = hostEnd(left);
        int rightHost = hostEnd(right);
        if (leftHost <= 0 || rightHost <= 0) {
            return false;
        }
        if (!left.substring(0, leftHost).equals(right.substring(0, rightHost))) {
            return false;
        }
        return left.startsWith(right) || right.startsWith(left);
    }

    private static int hostEnd(String url) {
        int scheme = url.indexOf("://");
        if (scheme < 0) {
            return -1;
        }
        int path = url.indexOf('/', scheme + 3);
        return path < 0 ? url.length() : path;
    }

    private static String normalize(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
