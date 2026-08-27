package com.smartqa.debug;

import org.slf4j.MDC;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

public final class TraceContext {

    public static final String KEY = "smartqa.traceId";

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TraceContext() {
    }

    public static String current() {
        String local = CURRENT.get();
        if (local != null && !local.isBlank()) {
            return local;
        }
        return TraceId.UNKNOWN;
    }

    public static String currentOrNull() {
        return CURRENT.get();
    }

    public static void set(String traceId) {
        if (traceId == null || traceId.isBlank() || TraceId.UNKNOWN.equals(traceId)) {
            CURRENT.remove();
            MDC.remove("traceId");
        } else {
            CURRENT.set(traceId);
            MDC.put("traceId", traceId);
        }
    }

    public static void clear() {
        CURRENT.remove();
        MDC.remove("traceId");
    }

    public static String resolveOrCreate() {
        String current = currentOrNull();
        if (current == null || current.isBlank() || TraceId.UNKNOWN.equals(current)) {
            String created = TraceId.newId();
            set(created);
            return created;
        }
        return current;
    }

    public static Context writeContext(ContextView current, String traceId) {
        Context ctx = Context.empty();
        if (current != null) {
            ctx = ctx.putAll(current);
        }
        if (traceId != null && !traceId.isBlank() && !TraceId.UNKNOWN.equals(traceId)) {
            ctx = ctx.put(KEY, traceId);
        }
        return ctx;
    }

    public static String from(ContextView context) {
        if (context == null || !context.hasKey(KEY)) {
            return current();
        }
        Object value = context.get(KEY);
        return value == null ? current() : String.valueOf(value);
    }

    public static <T> T call(String traceId, Supplier<T> supplier) {
        try {
            return callChecked(traceId, supplier::get);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static <T> T callChecked(String traceId, Callable<T> callable) throws Exception {
        String previous = CURRENT.get();
        set(traceId);
        try {
            return callable.call();
        } finally {
            if (previous == null) {
                clear();
            } else {
                set(previous);
            }
        }
    }

    public static void run(String traceId, Runnable runnable) {
        call(traceId, () -> {
            runnable.run();
            return null;
        });
    }
}
