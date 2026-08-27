package com.smartqa.rag;

public enum KnowledgeContentType {
    GENERIC_BROWSER_PATTERN,
    SEARCH_PATTERN,
    FILTER_PATTERN,
    RECOVERY_PATTERN,
    TAB_PATTERN,
    ASSERTION_PATTERN,
    APPLICATION_PATTERN,
    LOCATOR_PATTERN;

    public static KnowledgeContentType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return GENERIC_BROWSER_PATTERN;
        }
        return KnowledgeContentType.valueOf(raw.trim().toUpperCase());
    }
}
