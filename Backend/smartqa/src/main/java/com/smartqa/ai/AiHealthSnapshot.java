package com.smartqa.ai;

import java.util.List;

public record AiHealthSnapshot(
        String primaryProvider,
        String fallbackProvider,
        List<AiHealthStatus> providers
) {
}
