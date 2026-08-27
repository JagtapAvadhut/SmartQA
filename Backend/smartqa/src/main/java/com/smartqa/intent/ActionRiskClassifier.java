package com.smartqa.intent;

import java.util.Locale;
import java.util.Set;

/**
 * Generic per-action risk. High-risk actions require a unique confident winner
 * and cannot be executed from an AI guess alone.
 */
public final class ActionRiskClassifier {

    public enum Level {
        LOW,
        MEDIUM,
        HIGH
    }

    private static final Set<String> HIGH_HINTS = Set.of(
            "delete", "remove", "pay", "payment", "purchase", "buy", "checkout", "place order",
            "confirm order", "transfer", "unsubscribe", "deactivate", "reset password",
            "change password", "grant", "revoke", "admin"
    );

    private ActionRiskClassifier() {
    }

    public static Level classify(String action, String target, String value) {
        String blob = join(action, target, value).toLowerCase(Locale.ROOT);
        for (String hint : HIGH_HINTS) {
            if (blob.contains(hint)) {
                return Level.HIGH;
            }
        }
        String act = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        return switch (act) {
            case SupportedActions.NAVIGATE, SupportedActions.SEARCH, SupportedActions.EXPAND,
                 SupportedActions.COLLAPSE, SupportedActions.HOVER, SupportedActions.SCROLL,
                 SupportedActions.WAIT -> Level.LOW;
            case SupportedActions.VERIFY -> Level.LOW;
            case SupportedActions.CLICK, SupportedActions.INPUT, SupportedActions.SELECT,
                 SupportedActions.CHECKBOX, SupportedActions.RADIO, SupportedActions.FILTER,
                 SupportedActions.SUBMIT, SupportedActions.CLEAR_FILTERS, SupportedActions.SET_VALUE -> Level.MEDIUM;
            case SupportedActions.ADD_TO_CART, SupportedActions.QUANTITY -> Level.MEDIUM;
            default -> Level.MEDIUM;
        };
    }

    public static boolean requiresUniqueWinner(Level level) {
        return level == Level.HIGH || level == Level.MEDIUM;
    }

    private static String join(String... parts) {
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                if (!out.isEmpty()) {
                    out.append(' ');
                }
                out.append(part);
            }
        }
        return out.toString();
    }
}
