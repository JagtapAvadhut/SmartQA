package com.smartqa.ai;

import tools.jackson.databind.ObjectMapper;
import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.SmartQaException;
import com.smartqa.common.json.JsonSupport;
import com.smartqa.debug.TraceContext;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import reactor.core.publisher.Mono;

import java.util.List;

public abstract class AbstractAiProvider implements AiProvider {

    private final ObjectMapper objectMapper;

    protected AbstractAiProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public <T> Mono<T> generateStructuredOutput(AiPrompt prompt, Class<T> type) {
        return Mono.deferContextual(ctx -> {
            String traceId = TraceContext.from(ctx);
            TraceContext.set(traceId);
            AiPrompt effective = prompt.jsonOutput()
                    ? prompt
                    : prompt.withJsonOutput(true);
            return generateText(effective)
                    .doOnSubscribe(ignored -> TraceContext.set(traceId))
                    .handle((text, sink) -> {
                        TraceContext.set(traceId);
                        long started = System.nanoTime();
                        TraceLogger.info("INTENT", "INTENT_PARSE_STARTED", "Parsing AI structured output", TraceMeta.of(
                                "responseLength", text == null ? 0 : text.length()
                        ));
                        if (text == null || text.isBlank()) {
                            TraceLogger.error("AI", "AI_RESPONSE_INVALID", "AI returned an empty response",
                                    new SmartQaException(ErrorCode.AI_RESPONSE_INVALID, "AI returned an empty response"),
                                    (System.nanoTime() - started) / 1_000_000,
                                    TraceMeta.of("provider", id(), "responseLength", 0));
                            sink.error(new SmartQaException(
                                    ErrorCode.AI_RESPONSE_INVALID,
                                    "AI returned an empty response"));
                            return;
                        }
                        try {
                            String json = JsonSupport.extractJson(text);
                            T value = objectMapper.convertValue(
                                    coerceObjectShapedStrings(objectMapper.readTree(json)), type);
                            TraceLogger.info("INTENT", "INTENT_PARSE_COMPLETED", "AI structured output parsed",
                                    (System.nanoTime() - started) / 1_000_000,
                                    TraceMeta.of("type", type.getSimpleName()));
                            sink.next(value);
                        } catch (RuntimeException ex) {
                            TraceLogger.error("AI", "AI_RESPONSE_INVALID", "AI returned invalid structured output", ex,
                                    (System.nanoTime() - started) / 1_000_000,
                                    TraceMeta.of("provider", id(), "responseLength", text.length()));
                            sink.error(new SmartQaException(
                                    ErrorCode.AI_RESPONSE_INVALID,
                                    "AI returned invalid structured output",
                                    ex));
                        }
                    });
        });
    }

    protected ObjectMapper objectMapper() {
        return objectMapper;
    }

    /**
     * Gemini sometimes emits {"value":{"query":"x"}} for a String field.
     * Flatten one level so Jackson can bind declared String DTOs.
     */
    static tools.jackson.databind.JsonNode coerceObjectShapedStrings(tools.jackson.databind.JsonNode node) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (node instanceof tools.jackson.databind.node.ObjectNode object) {
            for (String field : List.copyOf(object.propertyNames())) {
                tools.jackson.databind.JsonNode child = object.get(field);
                if (child instanceof tools.jackson.databind.node.ObjectNode nested) {
                    String extracted = firstText(nested, "text", "value", "query", "label", "name", "target", "content");
                    if (extracted != null) {
                        object.put(field, extracted);
                    } else {
                        coerceObjectShapedStrings(child);
                    }
                } else if (child != null && child.isArray()) {
                    child.forEach(AbstractAiProvider::coerceObjectShapedStrings);
                }
            }
        } else if (node.isArray()) {
            node.forEach(AbstractAiProvider::coerceObjectShapedStrings);
        }
        return node;
    }

    private static String firstText(tools.jackson.databind.node.ObjectNode object, String... fields) {
        for (String field : fields) {
            tools.jackson.databind.JsonNode value = object.get(field);
            if (value != null && value.isTextual() && !value.asText("").isBlank()) {
                return value.asText().trim();
            }
        }
        return null;
    }
}
