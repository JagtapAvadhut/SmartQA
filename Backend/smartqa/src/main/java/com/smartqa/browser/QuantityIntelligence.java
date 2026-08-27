package com.smartqa.browser;

import com.microsoft.playwright.Page;
import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.SmartQaException;
import com.smartqa.browser.intelligence.PageReadinessContract;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generic quantity increment: read nearby quantity and totals, wait for a real numeric change.
 * No website-specific selectors.
 */
public final class QuantityIntelligence {

    public enum Phase {
        DISCOVERED,
        READ,
        INCREMENT_REQUESTED,
        UPDATED,
        TOTAL_UPDATED,
        VERIFIED
    }

    public record Snapshot(Integer quantity, String totalsFingerprint) {
    }

    private static final Pattern QTY = Pattern.compile("\\b([1-9]\\d{0,3})\\b");
    private static final Pattern MONEY = Pattern.compile("(?:₹|rs\\.?|inr|\\$)\\s*([0-9][0-9,]*(?:\\.\\d+)?)");

    private QuantityIntelligence() {
    }

    public static boolean looksLikeIncrement(String target) {
        if (target == null || target.isBlank()) {
            return false;
        }
        String lower = target.toLowerCase(Locale.ROOT).trim();
        return lower.equals("+")
                || lower.equals("plus")
                || lower.contains("increase")
                || lower.contains("increment")
                || lower.contains("qty +")
                || lower.contains("quantity +");
    }

    public static Snapshot capture(Page page) {
        String body = safeBody(page);
        Integer qty = firstQuantity(body);
        return new Snapshot(qty, moneyFingerprint(body));
    }

    public static void ensureIncremented(Page page, Snapshot before) {
        if (!waitForIncrement(page, before)) {
            throw new SmartQaException(ErrorCode.QUANTITY_STATE_MISMATCH,
                    "Click succeeded but quantity/total did not change");
        }
    }

    public static boolean waitForIncrement(Page page, Snapshot before) {
        if (page == null || before == null) {
            return false;
        }
        long deadline = System.currentTimeMillis() + 8_000;
        while (System.currentTimeMillis() < deadline) {
            Snapshot now = capture(page);
            boolean qtyUp = before.quantity() != null && now.quantity() != null && now.quantity() > before.quantity();
            boolean totalsChanged = !nullToEmpty(before.totalsFingerprint()).equals(now.totalsFingerprint())
                    && !now.totalsFingerprint().isBlank();
            if (qtyUp || totalsChanged) {
                TraceLogger.info("CART", "QUANTITY_STATE_CHANGED", "Quantity or totals changed after increment",
                        TraceMeta.of(
                                "quantityBefore", before.quantity() == null ? "" : String.valueOf(before.quantity()),
                                "quantityAfter", now.quantity() == null ? "" : String.valueOf(now.quantity()),
                                "totalsChanged", totalsChanged
                        ));
                return true;
            }
            PageReadinessContract.boundedMicroSettle(page, 200);
        }
        TraceLogger.warn("CART", "QUANTITY_STATE_UNCHANGED", "No quantity/total change observed after increment",
                TraceMeta.of(
                        "quantityBefore", before.quantity() == null ? "" : String.valueOf(before.quantity())
                ));
        return false;
    }

    private static Integer firstQuantity(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        Matcher matcher = QTY.matcher(body.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            try {
                int value = Integer.parseInt(matcher.group(1));
                if (value <= 99) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private static String moneyFingerprint(String body) {
        if (body == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        Matcher matcher = MONEY.matcher(body.toLowerCase(Locale.ROOT));
        int count = 0;
        while (matcher.find() && count < 8) {
            if (!out.isEmpty()) {
                out.append('|');
            }
            out.append(matcher.group(1).replace(",", ""));
            count++;
        }
        return out.toString();
    }

    private static String safeBody(Page page) {
        try {
            return page.locator("body").innerText();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
