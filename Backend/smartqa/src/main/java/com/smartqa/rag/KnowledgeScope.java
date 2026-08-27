package com.smartqa.rag;

public enum KnowledgeScope {
    GLOBAL_GENERIC,
    APPLICATION,
    EXECUTION;

    public static KnowledgeScope parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return GLOBAL_GENERIC;
        }
        return KnowledgeScope.valueOf(raw.trim().toUpperCase());
    }
}
