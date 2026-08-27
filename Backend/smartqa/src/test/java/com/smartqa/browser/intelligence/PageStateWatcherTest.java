package com.smartqa.browser.intelligence;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PageStateWatcherTest {

    @Test
    void waitForChangeDetectsTitleChangeWithoutFixedSleep() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("<html><head><title>Before</title></head><body><button>Run</button></body></html>");
            PageStateWatcher.Observation before = PageStateWatcher.capture(page, 1);

            page.evaluate("() => setTimeout(() => { document.title = 'After'; }, 150)");

            boolean changed = PageStateWatcher.waitForChange(
                    page,
                    before,
                    () -> 1,
                    UUID.randomUUID(),
                    null
            );
            assertTrue(changed, "Expected watcher to detect title change");
            browser.close();
        }
    }

    @Test
    void waitUntilInteractiveUsesStateDrivenCheck() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("<html><body><input name='q' /></body></html>");

            boolean interactive = PageStateWatcher.waitUntilInteractive(
                    page,
                    () -> 1,
                    UUID.randomUUID(),
                    null
            );
            assertTrue(interactive, "Expected interactive state to be detected");
            browser.close();
        }
    }

    @Test
    void waitUntilInteractivePollsUntilElementsAppear() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("<html><body><div id='root'></div></body></html>");
            AtomicInteger count = new AtomicInteger(0);
            page.evaluate("() => setTimeout(() => {"
                    + "const root = document.getElementById('root');"
                    + "const btn = document.createElement('button');"
                    + "btn.textContent = 'Login';"
                    + "root.appendChild(btn);"
                    + "}, 300)");

            boolean interactive = PageStateWatcher.waitUntilInteractive(
                    page,
                    () -> {
                        Object value = page.evaluate("() => document.querySelectorAll('button,input,a,select,textarea,[role]').length");
                        int current = value == null ? 0 : ((Number) value).intValue();
                        count.set(current);
                        return current;
                    },
                    UUID.randomUUID(),
                    null,
                    5000
            );
            assertTrue(interactive, "Expected delayed interactive controls to be detected");
            assertTrue(count.get() > 0, "Expected non-zero interactive count after polling");
            browser.close();
        }
    }

    @Test
    void waitForSubtreeSettleReturnsAfterMutationsGoQuiet() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setContent("<html><body><div id='root'>a</div></body></html>");
            page.evaluate("() => { const root = document.getElementById('root');"
                    + "let n = 0; const id = setInterval(() => {"
                    + "root.textContent = 'tick-' + (++n); if (n >= 3) clearInterval(id);"
                    + "}, 40); }");
            boolean settled = PageStateWatcher.waitForSubtreeSettle(page, "#root", 120, 2000);
            assertTrue(settled, "Expected subtree mutations to settle");
            browser.close();
        }
    }
}
