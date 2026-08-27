package com.smartqa.event;



import com.smartqa.debug.TraceLogger;

import com.smartqa.debug.TraceMeta;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;

import reactor.core.publisher.Sinks;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;



import java.util.concurrent.ConcurrentHashMap;

import java.util.concurrent.atomic.AtomicLong;



@Component

public class ProgressEventHub {



    private static final Logger log = LoggerFactory.getLogger(ProgressEventHub.class);

    private static final int REPLAY_LIMIT = 100;



    private final ConcurrentHashMap<String, Sinks.Many<ProgressEvent>> channels = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, AtomicLong> sequences = new ConcurrentHashMap<>();



    public void emit(String channel, ProgressEvent event) {

        long eventId = sequences.computeIfAbsent(channel, ignored -> new AtomicLong(0)).incrementAndGet();

        event = correlate(event);
        ProgressEvent sequenced = event.eventId() == null ? event.withEventId(eventId) : event;

        Sinks.Many<ProgressEvent> sink = sink(channel);

        Sinks.EmitResult result = sink.tryEmitNext(sequenced);

        if (result.isFailure()) {

            log.warn("event_drop channel={} type={} eventId={} result={}", channel, sequenced.type(), eventId, result);

            TraceLogger.warn("SSE", "SSE_EVENT_DROPPED", "SSE event dropped", TraceMeta.of(

                    "channel", channel,

                    "eventType", sequenced.type(),

                    "eventId", eventId,

                    "result", String.valueOf(result)

            ));

        } else {

            log.info("event type={} channel={} eventId={} message={}", sequenced.type(), channel, eventId, sequenced.message());

            Map<String, Object> details = sequenced.details();
            TraceLogger.info("SSE", "SSE_EVENT_SENT", sequenced.type(), TraceMeta.of(

                    "channel", channel,

                    "eventType", sequenced.type(),

                    "eventId", eventId,

                    "message", sequenced.message(),

                    "generationRunId", details == null ? null : details.get("generationRunId"),

                    "pipelineRunId", details == null ? null : details.get("pipelineRunId"),

                    "traceId", details == null ? null : details.get("traceId")

            ));

            mirrorAiEventToPipeline(channel, sequenced);

        }

    }



    public Flux<ProgressEvent> stream(String channel) {

        return sink(channel).asFlux();

    }

    public Flux<ProgressEvent> stream(String channel, Long lastEventId) {
        Flux<ProgressEvent> source = stream(channel);
        if (lastEventId != null) {
            source = source.filter(event -> event.eventId() == null || event.eventId() > lastEventId);
        }
        return source;
    }



    public long latestEventId(String channel) {

        AtomicLong counter = sequences.get(channel);

        return counter == null ? 0L : counter.get();

    }



    public static String generationChannel(java.util.UUID testCaseId) {

        return "generation:" + testCaseId;

    }



    public static String executionChannel(java.util.UUID runId) {

        return "execution:" + runId;

    }



    public static String validationChannel(java.util.UUID validationRunId) {

        return "validation:" + validationRunId;

    }



    public static String pipelineChannel(java.util.UUID pipelineId) {

        return "pipeline:" + pipelineId;

    }



    private Sinks.Many<ProgressEvent> sink(String channel) {

        return channels.computeIfAbsent(channel, ignored -> Sinks.many().replay().limit(REPLAY_LIMIT));

    }

    private static ProgressEvent correlate(ProgressEvent event) {
        if (event == null) {
            return null;
        }
        Map<String, Object> details = new HashMap<>();
        if (event.details() != null) {
            details.putAll(event.details());
        }
        boolean changed = false;
        changed |= stampValidId(details, "generationRunId", RunCorrelation.generationRunId());
        changed |= stampValidId(details, "pipelineRunId", RunCorrelation.pipelineRunId());
        UUID testCaseId = event.testCaseId() != null ? event.testCaseId() : RunCorrelation.testCaseId();
        changed |= stampValidId(details, "testCaseId", testCaseId);
        return changed ? event.withDetails(Collections.unmodifiableMap(details)) : event;
    }

    private static boolean stampValidId(Map<String, Object> details, String key, UUID value) {
        Object existing = details.get(key);
        if (!RunCorrelation.isMissingId(existing)) {
            return false;
        }
        if (value == null) {
            if (existing == null && details.containsKey(key)) {
                return false;
            }
            details.put(key, null);
            return true;
        }
        details.put(key, value.toString());
        return true;
    }

    private void mirrorAiEventToPipeline(String channel, ProgressEvent event) {
        if (event == null || !isAiProgressType(event.type())) {
            return;
        }
        UUID pipelineId = RunCorrelation.pipelineRunId();
        if (pipelineId == null && event.details() != null) {
            Object raw = event.details().get("pipelineRunId");
            if (raw != null) {
                try {
                    pipelineId = UUID.fromString(String.valueOf(raw));
                } catch (IllegalArgumentException ignored) {
                    return;
                }
            }
        }
        if (pipelineId == null) {
            return;
        }
        String pipelineChannel = pipelineChannel(pipelineId);
        if (channel.equals(pipelineChannel)) {
            return;
        }
        emit(pipelineChannel, event.withoutEventId());
    }

    static boolean isAiProgressType(String type) {
        if (type == null || type.isBlank()) {
            return false;
        }
        return type.startsWith("AI_")
                || type.startsWith("GEMINI_")
                || type.startsWith("OLLAMA_")
                || type.startsWith("INTENT_");
    }

}


