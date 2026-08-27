package com.smartqa.debug;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;

public final class TraceLogger {

    private static final Logger log = LoggerFactory.getLogger("smartqa.trace");
    private static final int INFO_PAYLOAD_LIMIT = 1_500;
    private static volatile TraceStore store;

    private TraceLogger() {
    }

    static void bind(TraceStore traceStore) {
        store = traceStore;
    }

    public static void info(String component, String operation, String message) {
        info(component, operation, message, null);
    }

    public static void info(String component, String operation, String message, Map<String, Object> metadata) {
        emit("INFO", component, operation, message, null, null, null, metadata, null);
    }

    public static void info(
            String component,
            String operation,
            String message,
            Long durationMs,
            Map<String, Object> metadata) {
        emit("INFO", component, operation, message, durationMs, null, null, metadata, null);
    }

    public static void debug(String component, String operation, String message, Map<String, Object> metadata) {
        emit("DEBUG", component, operation, message, null, null, null, metadata, null);
    }

    public static void warn(String component, String operation, String message, Map<String, Object> metadata) {
        emit("WARN", component, operation, message, null, null, null, metadata, null);
    }

    public static void error(String component, String operation, String message, Throwable error) {
        error(component, operation, message, error, null, null);
    }

    public static void error(
            String component,
            String operation,
            String message,
            Throwable error,
            Long durationMs,
            Map<String, Object> metadata) {
        emit("ERROR", component, operation, message, durationMs, null, null, metadata, error);
    }

    public static void emit(
            String level,
            String component,
            String operation,
            String message,
            Long durationMs,
            Object payload,
            Object result,
            Map<String, Object> metadata,
            Throwable error) {
        String traceId = TraceContext.current();
        TraceEvent event = TraceEvent.builder()
                .traceId(traceId)
                .level(level == null ? "INFO" : level.toUpperCase(Locale.ROOT))
                .component(component)
                .operation(operation)
                .message(SecretMasker.maskText(message))
                .durationMs(durationMs)
                .payload(shrink(SecretMasker.mask(payload), !"DEBUG".equalsIgnoreCase(level)))
                .result(shrink(SecretMasker.mask(result), !"DEBUG".equalsIgnoreCase(level)))
                .metadata(cast(SecretMasker.mask(metadata)))
                .error(error == null ? null : SecretMasker.maskText(error.getMessage()))
                .exceptionType(error == null ? null : error.getClass().getName())
                .stackTrace(error == null ? null : stackTrace(error))
                .build();
        write(event);
    }

    public static void write(TraceEvent event) {
        if (event == null) {
            return;
        }
        MDC.put("traceId", event.traceId());
        try {
            String line = "traceId=" + event.traceId()
                    + " component=" + nullToEmpty(event.component())
                    + " operation=" + nullToEmpty(event.operation())
                    + " message=\"" + nullToEmpty(event.message()) + "\"";
            if (event.durationMs() != null) {
                line += " durationMs=" + event.durationMs();
            }
            switch (event.level() == null ? "INFO" : event.level()) {
                case "ERROR" -> log.error(line);
                case "WARN" -> log.warn(line);
                case "DEBUG" -> log.debug(line);
                default -> log.info(line);
            }
            TraceStore current = store;
            if (current != null) {
                current.append(event);
            }
        } finally {
            MDC.remove("traceId");
        }
    }

    public static Object summarizeLarge(String content, String label) {
        if (content == null) {
            return null;
        }
        return TraceMeta.of(
                "label", label,
                "payloadSize", content.length(),
                "payloadHash", sha256(content),
                "preview", content.length() <= INFO_PAYLOAD_LIMIT
                        ? SecretMasker.maskText(content)
                        : SecretMasker.maskText(content.substring(0, INFO_PAYLOAD_LIMIT)) + "...[truncated]"
        );
    }

    private static Object shrink(Object value, boolean infoLevel) {
        if (value == null || !infoLevel) {
            return value;
        }
        String text = String.valueOf(value);
        if (text.length() <= INFO_PAYLOAD_LIMIT) {
            return value;
        }
        return TraceMeta.of(
                "payloadSize", text.length(),
                "payloadHash", sha256(text),
                "preview", text.substring(0, INFO_PAYLOAD_LIMIT) + "...[truncated]"
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private static String stackTrace(Throwable error) {
        StringWriter writer = new StringWriter();
        error.printStackTrace(new PrintWriter(writer));
        String text = writer.toString();
        if (text.length() > 12_000) {
            return text.substring(0, 12_000) + "\n...[truncated]";
        }
        return text;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < Math.min(8, hash.length); i++) {
                hex.append(String.format(Locale.ROOT, "%02x", hash[i]));
            }
            return hex.toString();
        } catch (Exception ex) {
            return "na";
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
