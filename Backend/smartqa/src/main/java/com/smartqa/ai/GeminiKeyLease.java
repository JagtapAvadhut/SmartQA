package com.smartqa.ai;

/**
 * Selected Gemini key for one HTTP attempt. {@link #toString()} never includes the secret.
 */
public final class GeminiKeyLease {

    private final int index;
    private final String apiKey;
    private final int keyCount;
    private final int attempt;

    public GeminiKeyLease(int index, String apiKey, int keyCount, int attempt) {
        this.index = index;
        this.apiKey = apiKey;
        this.keyCount = keyCount;
        this.attempt = attempt;
    }

    public int index() {
        return index;
    }

    public int displayIndex() {
        return index + 1;
    }

    public String apiKey() {
        return apiKey;
    }

    public int keyCount() {
        return keyCount;
    }

    public int attempt() {
        return attempt;
    }

    @Override
    public String toString() {
        return "GeminiKeyLease{keyIndex=" + displayIndex() + ", keyCount=" + keyCount + ", attempt=" + attempt + "}";
    }
}
