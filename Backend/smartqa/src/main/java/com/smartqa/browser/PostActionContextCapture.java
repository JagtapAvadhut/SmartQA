package com.smartqa.browser;

import com.microsoft.playwright.Page;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;

import java.util.Map;

/**
 * Captures focused element / overlay / dialog after an action so the next resolve
 * can use live context instead of stale pre-click memory.
 */
public final class PostActionContextCapture {

    private PostActionContextCapture() {
    }

    public record Context(
            String focusedText,
            String focusedRole,
            boolean dialogOpen,
            boolean overlayPresent,
            String url
    ) {
        public static Context empty() {
            return new Context("", "", false, false, "");
        }
    }

    public static Context capture(Page page) {
        if (page == null) {
            return Context.empty();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = (Map<String, Object>) page.evaluate("""
                    () => {
                      const active = document.activeElement;
                      const dialog = document.querySelector('[role=dialog], dialog[open], [aria-modal=true]');
                      const overlay = document.querySelector('[aria-modal=true], .modal.show, [class*=overlay]:not([hidden])');
                      return {
                        focusedText: active ? (active.innerText || active.getAttribute('aria-label') || '').slice(0, 80) : '',
                        focusedRole: active ? (active.getAttribute('role') || active.tagName || '') : '',
                        dialogOpen: !!dialog,
                        overlayPresent: !!overlay,
                        url: location.href
                      };
                    }
                    """);
            if (raw == null) {
                return Context.empty();
            }
            Context context = new Context(
                    string(raw.get("focusedText")),
                    string(raw.get("focusedRole")),
                    bool(raw.get("dialogOpen")),
                    bool(raw.get("overlayPresent")),
                    string(raw.get("url"))
            );
            TraceLogger.info("BROWSER", "POST_ACTION_CONTEXT", "Captured post-action UI context", TraceMeta.of(
                    "dialogOpen", context.dialogOpen(),
                    "overlayPresent", context.overlayPresent(),
                    "focusedRole", context.focusedRole()
            ));
            return context;
        } catch (RuntimeException ex) {
            return new Context("", "", false, false, safeUrl(page));
        }
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean bool(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private static String safeUrl(Page page) {
        try {
            return page.url();
        } catch (RuntimeException ex) {
            return "";
        }
    }
}
