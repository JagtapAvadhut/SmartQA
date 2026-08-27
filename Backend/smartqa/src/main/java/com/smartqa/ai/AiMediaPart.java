package com.smartqa.ai;

import java.util.Objects;

/**
 * Optional multimodal attachment for an {@link AiPrompt}.
 * Used for fresh screenshots during ambiguity / failure diagnosis.
 */
public record AiMediaPart(String mimeType, byte[] data, String label) {

    public AiMediaPart {
        mimeType = mimeType == null || mimeType.isBlank() ? "image/png" : mimeType.trim();
        data = data == null ? new byte[0] : data;
        label = label == null ? "screenshot" : label;
    }

    public static AiMediaPart image(byte[] data, String mimeType) {
        return new AiMediaPart(mimeType, data, "screenshot");
    }

    public static AiMediaPart png(byte[] data) {
        return image(data, "image/png");
    }

    public boolean isEmpty() {
        return data.length == 0;
    }

    public int sizeBytes() {
        return data.length;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AiMediaPart that)) {
            return false;
        }
        return Objects.equals(mimeType, that.mimeType)
                && Objects.equals(label, that.label)
                && java.util.Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(mimeType, label);
        result = 31 * result + java.util.Arrays.hashCode(data);
        return result;
    }
}
