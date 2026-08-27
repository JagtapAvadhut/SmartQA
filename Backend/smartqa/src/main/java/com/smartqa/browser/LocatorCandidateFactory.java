package com.smartqa.browser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LocatorCandidateFactory {

    public record Candidate(String locatorType, String resolvedLocator, double confidence, String strategy) {
    }

    private LocatorCandidateFactory() {
    }

    public static List<Candidate> candidates(String action, String target) {
        List<Candidate> list = new ArrayList<>();
        if (target == null || target.isBlank()) {
            return list;
        }
        String trimmed = target.trim().replaceAll("(?i)\\s+(button|link|field|input|checkbox|heading)$", "").trim();
        if (trimmed.isBlank()) {
            trimmed = target.trim();
        }
        String lowerAction = action == null ? "" : action.toLowerCase(Locale.ROOT);
        if ("verify".equals(lowerAction) || "click".equals(lowerAction) || "hover".equals(lowerAction)) {
            list.add(new Candidate("role", "heading|" + trimmed, 0.87, "role-heading"));
            list.add(new Candidate("role", "link|" + trimmed, 0.92, "role-link"));
            list.add(new Candidate("role", "button|" + trimmed, 0.9, "role-button"));
        }
        if ("input".equals(lowerAction) || "select".equals(lowerAction) || "search".equals(lowerAction)) {
            if ("search".equals(lowerAction)) {
                list.add(new Candidate("role", "searchbox|" + trimmed, 0.94, "role-searchbox"));
                list.add(new Candidate("role", "combobox|" + trimmed, 0.92, "role-combobox"));
            }
            list.add(new Candidate("role", "textbox|" + trimmed, 0.9, "role-textbox"));
            list.add(new Candidate("label", trimmed, 0.88, "label"));
            list.add(new Candidate("placeholder", trimmed, 0.84, "placeholder"));
        }
        list.add(new Candidate("label", trimmed, 0.86, "label"));
        if (trimmed.toLowerCase(Locale.ROOT).contains("heading")) {
            list.add(new Candidate("css", "h1", 0.78, "heading-css"));
        }
        list.add(new Candidate("text", trimmed, 0.82, "text"));
        list.add(new Candidate("css", "text=" + trimmed, 0.8, "pw-text"));
        for (String synonym : textSynonyms(trimmed)) {
            if ("click".equals(lowerAction) || "hover".equals(lowerAction) || "verify".equals(lowerAction)) {
                list.add(new Candidate("role", "button|" + synonym, 0.88, "role-button-synonym"));
                list.add(new Candidate("role", "link|" + synonym, 0.88, "role-link-synonym"));
                list.add(new Candidate("text", synonym, 0.8, "text-synonym"));
            }
        }
        if (looksLikeIdentifier(trimmed)) {
            list.add(new Candidate("css", "#" + trimmed, 0.7, "id"));
            list.add(new Candidate("css", "[name='" + cssEscape(trimmed) + "']", 0.74, "name"));
            list.add(new Candidate("css", "[aria-label='" + cssEscape(trimmed) + "']", 0.8, "aria"));
            list.add(new Candidate("css", "[placeholder='" + cssEscape(trimmed) + "']", 0.72, "placeholder-attr"));
        }
        return list;
    }

    static boolean looksLikeIdentifier(String value) {
        return value.matches("[A-Za-z][A-Za-z0-9_-]*");
    }

    private static List<String> textSynonyms(String target) {
        String lower = target == null ? "" : target.toLowerCase(Locale.ROOT).trim();
        if (lower.equals("login") || lower.equals("log in") || lower.equals("signin") || lower.equals("sign in")) {
            return List.of("Login", "Log in", "Sign in", "Sign In");
        }
        if (lower.contains("profile") || lower.equals("account") || lower.equals("user")) {
            return List.of("Account", "My Account", "Profile", "User");
        }
        return List.of();
    }

    static String cssEscape(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }
}
