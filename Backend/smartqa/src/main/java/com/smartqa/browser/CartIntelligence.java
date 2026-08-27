package com.smartqa.browser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.SmartQaException;
import com.smartqa.browser.intelligence.PageReadinessContract;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;

import java.util.Locale;

/**
 * Generic post add-to-cart wait: drawer, modal, SPA navigation, or confirmation text.
 */
public final class CartIntelligence {

    public enum Phase {
        NOT_STARTED,
        ADD_REQUESTED,
        CART_OPENED,
        ITEM_PRESENT,
        VERIFIED
    }

    private CartIntelligence() {
    }

    public static boolean looksLikeAddToCart(String target) {
        if (target == null || target.isBlank()) {
            return false;
        }
        String lower = target.toLowerCase(Locale.ROOT);
        return (lower.contains("add") && (lower.contains("cart") || lower.contains("bag") || lower.contains("basket")))
                || lower.contains("buy now");
    }

    public static void ensureAdded(Page page) {
        if (!afterAddToCart(page)) {
            throw new SmartQaException(ErrorCode.CART_STATE_MISMATCH,
                    "Click succeeded but cart state was not confirmed");
        }
    }

    public static boolean afterAddToCart(Page page) {
        if (page == null) {
            return false;
        }
        long deadline = System.currentTimeMillis() + 8_000;
        String beforeUrl = safeUrl(page);
        while (System.currentTimeMillis() < deadline) {
            if (looksLikeCartState(page, beforeUrl)) {
                TraceLogger.info("CART", "CART_STATE_DETECTED", "Cart state observed after add-to-cart",
                        TraceMeta.of("url", safeUrl(page)));
                return true;
            }
            PageReadinessContract.boundedMicroSettle(page, 200);
        }
        TraceLogger.warn("CART", "CART_STATE_UNCONFIRMED", "No cart drawer/page confirmation observed",
                TraceMeta.of("url", safeUrl(page)));
        return false;
    }

    private static boolean looksLikeCartState(Page page, String beforeUrl) {
        String url = safeUrl(page).toLowerCase(Locale.ROOT);
        if (!url.equals(beforeUrl.toLowerCase(Locale.ROOT))
                && (url.contains("cart") || url.contains("bag") || url.contains("basket") || url.contains("checkout"))) {
            return true;
        }
        String body = safeBody(page).toLowerCase(Locale.ROOT);
        if (body.contains("added to cart")
                || body.contains("added to bag")
                || body.contains("go to cart")
                || body.contains("go to bag")
                || body.contains("place order")
                || body.contains("proceed to checkout")
                || body.contains("price details")
                || (body.contains("remove") && (body.contains("qty") || body.contains("quantity")))) {
            return true;
        }
        try {
            Locator dialog = page.getByRole(AriaRole.DIALOG);
            if (dialog.count() > 0 && dialog.first().isVisible()) {
                String dialogText = dialog.first().innerText().toLowerCase(Locale.ROOT);
                if (dialogText.contains("cart") || dialogText.contains("bag") || dialogText.contains("added")) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return false;
    }

    private static String safeUrl(Page page) {
        try {
            return page.url() == null ? "" : page.url();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static String safeBody(Page page) {
        try {
            return page.locator("body").innerText();
        } catch (RuntimeException ex) {
            return "";
        }
    }
}
