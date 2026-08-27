package com.smartqa.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TraceFormatter {

    private TraceFormatter() {
    }

    public static String toHumanReadable(String traceId, List<TraceEvent> events) {
        List<TraceEvent> safe = events == null ? List.of() : events;
        StringBuilder out = new StringBuilder();
        out.append("SMARTQA TRACE\n");
        out.append("================================================\n\n");
        out.append("Trace ID:\n").append(traceId == null ? "" : traceId).append("\n\n");

        TraceEvent start = first(safe, "TRACE_STARTED");
        TraceEvent end = last(safe, "TRACE_END");
        out.append("Started:\n").append(start == null ? firstTs(safe) : start.timestamp()).append("\n\n");
        out.append("Finished:\n").append(end == null ? lastTs(safe) : end.timestamp()).append("\n\n");
        out.append("Operation:\n").append(operation(safe)).append("\n\n");
        out.append("Application URL:\n").append(meta(safe, "applicationUrl")).append("\n\n");
        out.append("STATUS: ").append(status(safe)).append("\n");
        out.append("EVENTS: ").append(safe.size()).append("\n");
        out.append("ERRORS: ").append(countLevel(safe, "ERROR")).append("\n");
        out.append("WARNINGS: ").append(countLevel(safe, "WARN")).append("\n");
        out.append("DURATION: ").append(totalDuration(safe)).append("\n\n");

        int step = 1;
        String lastComponent = "";
        for (TraceEvent event : safe) {
            String component = event.component() == null ? "UNKNOWN" : event.component();
            if (!component.equals(lastComponent)) {
                out.append("------------------------------------------------\n");
                out.append("STEP ").append(step++).append(" — ").append(component).append("\n");
                out.append("------------------------------------------------\n\n");
                lastComponent = component;
            }
            appendEvent(out, event);
        }

        List<TraceEvent> errors = new ArrayList<>();
        for (TraceEvent event : safe) {
            if ("ERROR".equalsIgnoreCase(event.level())) {
                errors.add(event);
            }
        }
        if (!errors.isEmpty()) {
            out.append("------------------------------------------------\n");
            out.append("ERROR\n");
            out.append("------------------------------------------------\n\n");
            for (TraceEvent event : errors) {
                appendEvent(out, event);
            }
        }

        out.append("================================================\n\n");
        out.append("TRACE END\n");
        return out.toString();
    }

    private static void appendEvent(StringBuilder out, TraceEvent event) {
        out.append("EVENT:\n").append(nullToEmpty(event.operation())).append("\n\n");
        out.append("timestamp: ").append(nullToEmpty(event.timestamp())).append("\n");
        out.append("level: ").append(nullToEmpty(event.level())).append("\n");
        out.append("component: ").append(nullToEmpty(event.component())).append("\n");
        out.append("message: ").append(nullToEmpty(event.message())).append("\n");
        if (event.durationMs() != null) {
            out.append("durationMs: ").append(event.durationMs()).append("\n");
        }
        if (event.exceptionType() != null) {
            out.append("exceptionType: ").append(event.exceptionType()).append("\n");
        }
        if (event.error() != null) {
            out.append("error: ").append(event.error()).append("\n");
        }
        if (event.payload() != null) {
            out.append("payload:\n").append(stringify(event.payload())).append("\n");
        }
        if (event.result() != null) {
            out.append("result:\n").append(stringify(event.result())).append("\n");
        }
        if (event.metadata() != null && !event.metadata().isEmpty()) {
            out.append("metadata:\n");
            for (Map.Entry<String, Object> entry : event.metadata().entrySet()) {
                out.append("  ").append(entry.getKey()).append("=").append(stringify(entry.getValue())).append("\n");
            }
        }
        if (event.stackTrace() != null && !event.stackTrace().isBlank()) {
            out.append("stackTrace:\n").append(event.stackTrace()).append("\n");
        }
        out.append("\n");
    }

    private static TraceEvent first(List<TraceEvent> events, String operation) {
        for (TraceEvent event : events) {
            if (operation.equals(event.operation())) {
                return event;
            }
        }
        return null;
    }

    private static TraceEvent last(List<TraceEvent> events, String operation) {
        for (int i = events.size() - 1; i >= 0; i--) {
            if (operation.equals(events.get(i).operation())) {
                return events.get(i);
            }
        }
        return null;
    }

    private static String firstTs(List<TraceEvent> events) {
        return events.isEmpty() ? "" : nullToEmpty(events.getFirst().timestamp());
    }

    private static String lastTs(List<TraceEvent> events) {
        return events.isEmpty() ? "" : nullToEmpty(events.getLast().timestamp());
    }

    private static String operation(List<TraceEvent> events) {
        String value = meta(events, "operation");
        if (!value.isBlank()) {
            return value;
        }
        TraceEvent started = first(events, "TRACE_STARTED");
        return started == null ? "" : nullToEmpty(started.message());
    }

    private static String meta(List<TraceEvent> events, String key) {
        for (TraceEvent event : events) {
            if (event.metadata() != null && event.metadata().get(key) != null) {
                return String.valueOf(event.metadata().get(key));
            }
        }
        return "";
    }

    private static String status(List<TraceEvent> events) {
        if (countLevel(events, "ERROR") > 0) {
            return "FAILED";
        }
        TraceEvent end = last(events, "TRACE_END");
        if (end != null && end.metadata() != null && end.metadata().get("status") != null) {
            return String.valueOf(end.metadata().get("status"));
        }
        return events.isEmpty() ? "EMPTY" : "RUNNING";
    }

    private static long countLevel(List<TraceEvent> events, String level) {
        long count = 0;
        for (TraceEvent event : events) {
            if (level.equalsIgnoreCase(event.level())) {
                count++;
            }
        }
        return count;
    }

    private static String totalDuration(List<TraceEvent> events) {
        long total = 0;
        boolean any = false;
        for (TraceEvent event : events) {
            if (event.durationMs() != null) {
                total += event.durationMs();
                any = true;
            }
        }
        if (!any) {
            return "";
        }
        return String.format(java.util.Locale.ROOT, "%.2fs", total / 1000.0);
    }

    private static String stringify(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
