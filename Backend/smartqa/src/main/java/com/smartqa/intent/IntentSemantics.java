package com.smartqa.intent;

import java.util.List;

/**
 * Extra semantic slots that must not be collapsed into target or assertion.
 */
public record IntentSemantics(
        String entity,
        String scope,
        String desiredState,
        String expectedOutcome,
        String risk,
        List<String> qualifiers
) {
    public static IntentSemantics empty() {
        return new IntentSemantics(null, null, null, null, null, List.of());
    }

    public IntentSemantics {
        qualifiers = qualifiers == null ? List.of() : List.copyOf(qualifiers);
    }
}
