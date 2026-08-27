package com.smartqa.browser.intelligence;

import com.microsoft.playwright.Page;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/**
 * Generic before/after page state for meaningful change detection.
 */
public record StateSnapshot(
        String url,
        String title,
        String visibleTextFingerprint,
        String domFingerprint,
        int interactiveCount,
        int dialogCount,
        boolean loading
) {
    public record Diff(
            boolean urlChanged,
            boolean titleChanged,
            boolean visibleTextChanged,
            boolean domChanged,
            int interactiveDelta,
            int dialogDelta,
            boolean loadingCleared,
            boolean meaningfullyChanged
    ) {
    }

    public static StateSnapshot capture(Page page, int interactiveCount) {
        String url = "";
        String title = "";
        try {
            url = page.url();
        } catch (RuntimeException ignored) {
        }
        try {
            title = page.title();
        } catch (RuntimeException ignored) {
        }
        String visibleText = "";
        int dialogCount = 0;
        boolean loading = false;
        String domFp = "";
        int count = interactiveCount;
        try {
            Object raw = page.evaluate("""
                    () => {
                      const text = (document.body && (document.body.innerText || ''))
                        .replace(/\\s+/g, ' ').trim().slice(0, 4000);
                      const dialogs = document.querySelectorAll(
                        '[role="dialog"], [aria-modal="true"], dialog[open]'
                      ).length;
                      const loading = !!(document.querySelector(
                        '[aria-busy="true"], .loading, .spinner, [class*="skeleton"]'
                      ));
                      const nodes = document.querySelectorAll('a,button,input,select,textarea,[role]').length;
                      return { text, dialogs, loading, nodes };
                    }
                    """);
            if (raw instanceof Map<?, ?> map) {
                Object textObj = map.get("text");
                visibleText = textObj == null ? "" : String.valueOf(textObj);
                Object d = map.get("dialogs");
                dialogCount = d instanceof Number n ? n.intValue() : 0;
                Object l = map.get("loading");
                loading = l instanceof Boolean b && b;
                Object nodes = map.get("nodes");
                if (count <= 0 && nodes instanceof Number n) {
                    count = n.intValue();
                }
                domFp = hash(String.valueOf(nodes) + "|" + dialogCount + "|" + visibleText.length());
            }
        } catch (RuntimeException ignored) {
        }
        return new StateSnapshot(
                url,
                title,
                hash(visibleText),
                domFp.isBlank() ? hash(url + "|" + title + "|" + count) : domFp,
                count,
                dialogCount,
                loading
        );
    }

    public boolean meaningfullyDifferent(StateSnapshot after) {
        return diff(this, after).meaningfullyChanged();
    }

    public static Diff diff(StateSnapshot before, StateSnapshot after) {
        if (before == null && after == null) {
            return new Diff(false, false, false, false, 0, 0, false, false);
        }
        if (before == null || after == null) {
            return new Diff(true, true, true, true, 0, 0, false, true);
        }
        boolean urlChanged = !nullSafe(before.url).equals(nullSafe(after.url));
        boolean titleChanged = !nullSafe(before.title).equals(nullSafe(after.title));
        boolean textChanged = !nullSafe(before.visibleTextFingerprint).equals(nullSafe(after.visibleTextFingerprint));
        boolean domChanged = !nullSafe(before.domFingerprint).equals(nullSafe(after.domFingerprint));
        int interactiveDelta = after.interactiveCount - before.interactiveCount;
        int dialogDelta = after.dialogCount - before.dialogCount;
        boolean loadingCleared = before.loading && !after.loading;
        boolean changed = urlChanged || titleChanged || textChanged || domChanged
                || interactiveDelta != 0 || dialogDelta != 0 || loadingCleared;
        return new Diff(urlChanged, titleChanged, textChanged, domChanged,
                interactiveDelta, dialogDelta, loadingCleared, changed);
    }

    public static void emitBefore(StateSnapshot snap) {
        TraceLogger.info("BROWSER", "STATE_BEFORE", "Captured state before action", meta(snap));
    }

    public static void emitAfter(StateSnapshot snap, boolean changed) {
        TraceLogger.info("BROWSER", "STATE_AFTER", "Captured state after action", meta(snap));
        TraceLogger.info("BROWSER", changed ? "STATE_CHANGED" : "STATE_NOT_CHANGED",
                "State change evaluation", TraceMeta.of("changed", changed));
        // Keep legacy event name for existing consumers.
        TraceLogger.info("BROWSER", "STATE_CHANGE_DETECTED", "State change evaluation", TraceMeta.of(
                "changed", changed
        ));
    }

    private static Map<String, Object> meta(StateSnapshot snap) {
        return TraceMeta.of(
                "url", snap.url(),
                "title", snap.title(),
                "visibleTextFingerprint", snap.visibleTextFingerprint(),
                "domFingerprint", snap.domFingerprint(),
                "interactiveCount", snap.interactiveCount(),
                "dialogCount", snap.dialogCount(),
                "loading", snap.loading()
        );
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(nullSafe(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes).substring(0, 16);
        } catch (Exception ex) {
            return Integer.toHexString(nullSafe(value).hashCode());
        }
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
