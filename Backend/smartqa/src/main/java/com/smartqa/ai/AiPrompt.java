package com.smartqa.ai;

import java.util.List;

/**
 * Text prompt plus optional multimodal media (e.g. fresh screenshot).
 * Credentials and secrets must never appear in system/user text.
 */
public record AiPrompt(String system, String user, boolean jsonOutput, List<AiMediaPart> media) {

    public AiPrompt {
        system = system == null ? "" : system;
        user = user == null ? "" : user;
        media = media == null ? List.of() : List.copyOf(media);
    }

    public AiPrompt(String system, String user) {
        this(system, user, false, List.of());
    }

    public AiPrompt(String system, String user, boolean jsonOutput) {
        this(system, user, jsonOutput, List.of());
    }

    public static AiPrompt json(String system, String user) {
        return new AiPrompt(system, user, true, List.of());
    }

    public static AiPrompt json(String system, String user, List<AiMediaPart> media) {
        return new AiPrompt(system, user, true, media);
    }

    public AiPrompt withMedia(List<AiMediaPart> mediaParts) {
        return new AiPrompt(system, user, jsonOutput, mediaParts);
    }

    public AiPrompt withJsonOutput(boolean json) {
        return new AiPrompt(system, user, json, media);
    }

    public boolean hasMedia() {
        return media.stream().anyMatch(part -> part != null && !part.isEmpty());
    }

    public int mediaBytes() {
        return media.stream().filter(p -> p != null).mapToInt(AiMediaPart::sizeBytes).sum();
    }

    public int textLength() {
        return system.length() + user.length();
    }
}
