package com.smartqa.debug;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public record TraceEvent(
        String traceId,
        String timestamp,
        String level,
        String component,
        String operation,
        String message,
        Long durationMs,
        Object payload,
        Object result,
        String error,
        String exceptionType,
        String stackTrace,
        Map<String, Object> metadata
) {
    public static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    public static String now() {
        return OffsetDateTime.now(ZoneId.systemDefault()).format(TIMESTAMP_FORMAT);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("traceId", traceId);
        map.put("timestamp", timestamp);
        map.put("level", level);
        map.put("component", component);
        map.put("operation", operation);
        map.put("message", message);
        if (durationMs != null) {
            map.put("durationMs", durationMs);
        }
        if (payload != null) {
            map.put("payload", payload);
        }
        if (result != null) {
            map.put("result", result);
        }
        if (error != null) {
            map.put("error", error);
        }
        if (exceptionType != null) {
            map.put("exceptionType", exceptionType);
        }
        if (stackTrace != null) {
            map.put("stackTrace", stackTrace);
        }
        if (metadata != null && !metadata.isEmpty()) {
            map.put("metadata", metadata);
        }
        return map;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String traceId;
        private String timestamp = now();
        private String level = "INFO";
        private String component;
        private String operation;
        private String message;
        private Long durationMs;
        private Object payload;
        private Object result;
        private String error;
        private String exceptionType;
        private String stackTrace;
        private Map<String, Object> metadata = Map.of();

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder timestamp(String timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder level(String level) {
            this.level = level;
            return this;
        }

        public Builder component(String component) {
            this.component = component;
            return this;
        }

        public Builder operation(String operation) {
            this.operation = operation;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder durationMs(Long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public Builder payload(Object payload) {
            this.payload = payload;
            return this;
        }

        public Builder result(Object result) {
            this.result = result;
            return this;
        }

        public Builder error(String error) {
            this.error = error;
            return this;
        }

        public Builder exceptionType(String exceptionType) {
            this.exceptionType = exceptionType;
            return this;
        }

        public Builder stackTrace(String stackTrace) {
            this.stackTrace = stackTrace;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata == null ? Map.of() : metadata;
            return this;
        }

        public TraceEvent build() {
            return new TraceEvent(
                    traceId,
                    timestamp,
                    level,
                    component,
                    operation,
                    message,
                    durationMs,
                    payload,
                    result,
                    error,
                    exceptionType,
                    stackTrace,
                    metadata
            );
        }
    }
}
