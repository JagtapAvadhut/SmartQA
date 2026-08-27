package com.smartqa.ai;

import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;

/**
 * Thread-scoped token usage for a single generation/execution. Never logs prompt text.
 */
public final class TokenUsageTracker {

    private static final ThreadLocal<Usage> CURRENT = ThreadLocal.withInitial(Usage::new);

    private TokenUsageTracker() {
    }

    public static void reset() {
        CURRENT.set(new Usage());
    }

    public static void record(int promptTokens, int outputTokens) {
        Usage usage = CURRENT.get();
        usage.promptTokens += Math.max(0, promptTokens);
        usage.outputTokens += Math.max(0, outputTokens);
        usage.calls += 1;
        TraceLogger.info("AI", "TOKEN_USAGE", "AI token usage recorded", TraceMeta.of(
                "promptTokens", promptTokens,
                "outputTokens", outputTokens,
                "totalTokens", promptTokens + outputTokens,
                "call", usage.calls
        ));
    }

    public static Usage snapshot() {
        Usage usage = CURRENT.get();
        return new Usage(usage.promptTokens, usage.outputTokens, usage.calls);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static final class Usage {
        private int promptTokens;
        private int outputTokens;
        private int calls;

        public Usage() {
        }

        public Usage(int promptTokens, int outputTokens, int calls) {
            this.promptTokens = promptTokens;
            this.outputTokens = outputTokens;
            this.calls = calls;
        }

        public int promptTokens() {
            return promptTokens;
        }

        public int outputTokens() {
            return outputTokens;
        }

        public int totalTokens() {
            return promptTokens + outputTokens;
        }

        public int calls() {
            return calls;
        }
    }
}
