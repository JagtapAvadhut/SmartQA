package com.smartqa.debug;

import com.smartqa.common.api.ApiResponse;
import com.smartqa.common.error.ResourceNotFoundException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class TraceController {

    private final TraceStore traceStore;

    public TraceController(TraceStore traceStore) {
        this.traceStore = traceStore;
    }

    @GetMapping("/api/debug/traces/{traceId}")
    public Mono<ApiResponse<Map<String, Object>>> get(@PathVariable String traceId) {
        String id = TraceId.sanitize(traceId);
        List<TraceEvent> events = traceStore.read(id);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("traceId", id);
        body.put("status", status(events));
        body.put("events", events.stream().map(TraceEvent::toMap).toList());
        body.put("eventCount", events.size());
        body.put("errors", events.stream().filter(event -> "ERROR".equalsIgnoreCase(event.level())).count());
        return Mono.just(ApiResponse.ok("Trace fetched", body));
    }

    @GetMapping("/api/debug/traces/{traceId}/download")
    public Mono<Void> download(
            @PathVariable String traceId,
            @RequestParam(name = "format", required = false, defaultValue = "log") String format,
            ServerWebExchange exchange) {
        String id = TraceId.sanitize(traceId);
        List<TraceEvent> events = traceStore.read(id);
        if (events.isEmpty() && !traceStore.exists(id)) {
            return Mono.error(new ResourceNotFoundException("Trace not found: " + id));
        }
        boolean jsonl = "jsonl".equalsIgnoreCase(format);
        String content = jsonl ? traceStore.downloadJsonl(id) : traceStore.downloadLog(id);
        String filename = "smartqa-trace-" + id + (jsonl ? ".jsonl" : ".log");
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().setContentType(jsonl
                ? MediaType.parseMediaType("application/x-ndjson")
                : MediaType.TEXT_PLAIN);
        response.getHeaders().set(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(filename).build().toString());
        DataBuffer buffer = response.bufferFactory().wrap(content.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @PostMapping("/api/debug/traces/{traceId}/events")
    public Mono<ApiResponse<Map<String, Object>>> ingest(
            @PathVariable String traceId,
            @RequestBody List<Map<String, Object>> events) {
        String id = TraceId.sanitize(traceId);
        TraceContext.set(id);
        int accepted = 0;
        List<Map<String, Object>> incoming = events == null ? List.of() : events;
        int limit = Math.min(incoming.size(), 400);
        for (int i = 0; i < limit; i++) {
            TraceEvent event = fromClient(id, incoming.get(i));
            if (event != null) {
                TraceLogger.write(event);
                accepted++;
            }
        }
        return Mono.just(ApiResponse.ok("Trace events accepted", Map.of(
                "traceId", id,
                "accepted", accepted
        )));
    }

    @SuppressWarnings("unchecked")
    private static TraceEvent fromClient(String traceId, Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        Object metadataRaw = raw.get("metadata");
        Map<String, Object> metadata = metadataRaw instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
        Number duration = raw.get("durationMs") instanceof Number number ? number : null;
        String level = string(raw.get("level"));
        if (level == null) {
            level = "INFO";
        }
        return TraceEvent.builder()
                .traceId(traceId)
                .timestamp(string(raw.get("timestamp")) == null ? TraceEvent.now() : string(raw.get("timestamp")))
                .level(level)
                .component(firstNonBlank(string(raw.get("component")), "UI"))
                .operation(firstNonBlank(string(raw.get("operation")), "UI_EVENT"))
                .message(SecretMasker.maskText(string(raw.get("message"))))
                .durationMs(duration == null ? null : duration.longValue())
                .payload(SecretMasker.mask(raw.get("payload")))
                .result(SecretMasker.mask(raw.get("result")))
                .error(SecretMasker.maskText(string(raw.get("error"))))
                .exceptionType(string(raw.get("exceptionType")))
                .metadata(metadata)
                .build();
    }

    private static String status(List<TraceEvent> events) {
        for (int i = events.size() - 1; i >= 0; i--) {
            TraceEvent event = events.get(i);
            if ("CONTROLLER_EXIT".equals(event.operation())
                    || "SERVICE_EXIT".equals(event.operation())
                    || "TRACE_END".equals(event.operation())) {
                Object status = event.metadata() == null ? null : event.metadata().get("status");
                if (status != null) {
                    String value = String.valueOf(status);
                    if ("SUCCESS".equalsIgnoreCase(value) || "COMPLETED".equalsIgnoreCase(value)) {
                        return "SUCCESS";
                    }
                    if ("FAILED".equalsIgnoreCase(value)) {
                        return "FAILED";
                    }
                    return value;
                }
            }
        }
        for (int i = events.size() - 1; i >= 0; i--) {
            if ("ERROR".equalsIgnoreCase(events.get(i).level())) {
                return "FAILED";
            }
        }
        return events.isEmpty() ? "EMPTY" : "RUNNING";
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
