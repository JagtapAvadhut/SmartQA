package com.smartqa.generation;

/**
 * Source of the helper methods inlined into generated Playwright tests.
 * Isolated runs cannot import SmartQA, so these methods are copied into the test class.
 */
public final class GeneratedRuntimeHelpers {

    private GeneratedRuntimeHelpers() {
    }

    public static String methods() {
        return """

                    private Locator firstVisible(Locator locator) {
                        if (locator == null) {
                            return null;
                        }
                        int count = Math.min(locator.count(), 8);
                        for (int i = 0; i < count; i++) {
                            Locator item = locator.nth(i);
                            try {
                                if (item.isVisible()) {
                                    return item;
                                }
                            } catch (RuntimeException ignored) {
                            }
                        }
                        return locator.count() > 0 ? locator.first() : locator;
                    }

                    private Page clickAndUseResultingPage(Page page, Locator locator) {
                        java.util.Set<Page> before = new java.util.HashSet<>(page.context().pages());
                        firstVisible(locator).click(new Locator.ClickOptions().setNoWaitAfter(true));
                        try {
                            page.waitForLoadState(LoadState.DOMCONTENTLOADED,
                                    new Page.WaitForLoadStateOptions().setTimeout(8000));
                        } catch (RuntimeException ignored) {
                        }
                        for (Page candidate : page.context().pages()) {
                            if (!before.contains(candidate) && !candidate.isClosed()) {
                                try {
                                    candidate.waitForLoadState(LoadState.DOMCONTENTLOADED,
                                            new Page.WaitForLoadStateOptions().setTimeout(8000));
                                } catch (RuntimeException ignored) {
                                }
                                return candidate;
                            }
                        }
                        return page;
                    }

                    private void ensureToggle(Locator locator, boolean checked) {
                        Locator target = firstVisible(locator);
                        try {
                            if (target.isChecked() != checked) {
                                target.click(new Locator.ClickOptions().setNoWaitAfter(true));
                            }
                        } catch (RuntimeException ex) {
                            target.click(new Locator.ClickOptions().setNoWaitAfter(true));
                        }
                    }

                    private void captureScreenshot(Page page, String name) {
                        String dir = System.getProperty("smartqa.screenshot.dir");
                        if (dir == null || dir.isBlank() || page == null) {
                            return;
                        }
                        try {
                            page.screenshot(new Page.ScreenshotOptions()
                                    .setPath(java.nio.file.Path.of(dir, name + ".png"))
                                    .setFullPage(false));
                        } catch (RuntimeException ignored) {
                        }
                    }
                """;
    }
}
