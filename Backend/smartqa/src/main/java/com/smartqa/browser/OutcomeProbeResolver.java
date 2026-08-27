package com.smartqa.browser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;

import java.util.List;

/**
 * Last-resort resolution: click a candidate, keep it only if expected outcome text appears,
 * otherwise restore URL/scroll. Never used as the first locator strategy.
 */
public final class OutcomeProbeResolver {

    private OutcomeProbeResolver() {
    }

    public record Probe(Locator locator, String description) {
    }

    public static Locator resolve(Page page, List<Probe> candidates, String expectedOutcome) {
        if (page == null || candidates == null || candidates.isEmpty()
                || expectedOutcome == null || expectedOutcome.isBlank()) {
            return null;
        }
        String needle = expectedOutcome.trim();
        Snapshot before = Snapshot.capture(page);
        for (Probe candidate : candidates) {
            if (candidate == null || candidate.locator() == null) {
                continue;
            }
            Locator target = VisibleLocatorPicker.firstVisibleOrControl(candidate.locator());
            if (target == null) {
                continue;
            }
            try {
                target.click(new Locator.ClickOptions().setNoWaitAfter(true).setTimeout(4_000));
                SafeClick.settle(page);
                if (outcomeVisible(page, needle)) {
                    TraceLogger.info("LOCATOR", "OUTCOME_PROBE_HIT", "Candidate produced expected outcome", TraceMeta.of(
                            "candidate", candidate.description() == null ? "" : candidate.description(),
                            "outcome", needle
                    ));
                    return target;
                }
            } catch (RuntimeException ignored) {
            }
            before.restore(page);
        }
        return null;
    }

    static boolean outcomeVisible(Page page, String expected) {
        if (page == null || expected == null || expected.isBlank()) {
            return false;
        }
        try {
            Locator byText = page.getByText(expected);
            return VisibleLocatorPicker.firstVisible(byText) != null;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private record Snapshot(String url, Object scrollY) {
        static Snapshot capture(Page page) {
            String url = "";
            Object scroll = 0;
            try {
                url = page.url();
            } catch (RuntimeException ignored) {
            }
            try {
                scroll = page.evaluate("() => window.scrollY || 0");
            } catch (RuntimeException ignored) {
            }
            return new Snapshot(url, scroll);
        }

        void restore(Page page) {
            try {
                if (url != null && !url.isBlank() && !url.equals(page.url())) {
                    page.navigate(url);
                    SafeClick.settle(page);
                }
            } catch (RuntimeException ignored) {
            }
            try {
                page.evaluate("y => window.scrollTo(0, y)", scrollY);
            } catch (RuntimeException ignored) {
            }
        }
    }
}
