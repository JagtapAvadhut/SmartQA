package com.smartqa.debug;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.Locale;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class TraceWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        if (!path.startsWith("/api/")) {
            return chain.filter(exchange);
        }
        String incoming = request.getHeaders().getFirst(TraceId.HEADER);
        String traceId = incoming == null || incoming.isBlank() ? TraceId.newId() : TraceId.sanitize(incoming);
        exchange.getResponse().getHeaders().add(TraceId.HEADER, traceId);
        boolean debugApi = path.startsWith("/api/debug/");
        boolean sse = path.contains("/stream");
        boolean health = path.equals("/api/health") || path.equals("/api/health/");
        String operation = operationFor(request.getMethod(), path);
        long started = System.nanoTime();

        TraceContext.set(traceId);
        if (!debugApi) {
            if (health) {
                TraceLogger.info("HTTP", "HEALTH_CHECK_STARTED", "Health check received", TraceMeta.of(
                        "url", request.getURI().toString(),
                        "method", method(request)
                ));
            } else {
                TraceLogger.info("HTTP", "HTTP_REQUEST", method(request) + " " + path, TraceMeta.of(
                        "method", method(request),
                        "path", path,
                        "operation", operation
                ));
                if (isFlowStart(path)) {
                    TraceLogger.info("HTTP", "TRACE_STARTED", operation, TraceMeta.of(
                            "operation", operation,
                            "path", path,
                            "backendVersion", "0.0.1-SNAPSHOT"
                    ));
                }
            }
        }

        ServerHttpResponse response = exchange.getResponse();
        response.beforeCommit(() -> {
            long duration = (System.nanoTime() - started) / 1_000_000;
            Integer status = response.getStatusCode() == null ? null : response.getStatusCode().value();
            TraceContext.set(traceId);
            if (!debugApi) {
                if (health) {
                    TraceLogger.info("HTTP", "HEALTH_CHECK_RESPONSE", "Health check finished", duration, TraceMeta.of(
                            "status", status,
                            "url", request.getURI().toString()
                    ));
                    TraceLogger.info("HTTP", "HEALTH_CHECK_COMPLETED", "Health check completed", duration, TraceMeta.of(
                            "status", status
                    ));
                } else if (sse) {
                    TraceLogger.info("SSE", "SSE_CONNECTED", "SSE stream opened", TraceMeta.of(
                            "path", path,
                            "status", status
                    ));
                } else {
                    TraceLogger.info("HTTP", "HTTP_RESPONSE", method(request) + " " + path, duration, TraceMeta.of(
                            "method", method(request),
                            "path", path,
                            "status", status
                    ));
                }
            }
            return Mono.empty();
        });

        return chain.filter(exchange)
                .contextWrite(Context.of(TraceContext.KEY, traceId))
                .doOnEach(signal -> TraceContext.set(traceId))
                .doOnError(error -> {
                    TraceContext.set(traceId);
                    TraceLogger.error("HTTP", health ? "HEALTH_CHECK_FAILED" : "HTTP_REQUEST_FAILED",
                            error.getMessage(), error, (System.nanoTime() - started) / 1_000_000,
                            TraceMeta.of("path", path, "method", method(request)));
                })
                .doFinally(signal -> {
                    if (!sse) {
                        TraceContext.clear();
                    }
                });
    }

    private static boolean isFlowStart(String path) {
        return path.endsWith("/understand")
                || path.endsWith("/analyze")
                || path.endsWith("/generate")
                || path.endsWith("/generate-and-validate")
                || path.endsWith("/execute")
                || path.endsWith("/clarify");
    }

    private static String operationFor(HttpMethod method, String path) {
        if (path.endsWith("/understand") || path.endsWith("/analyze")) {
            return "ANALYZE";
        }
        if (path.endsWith("/generate-and-validate")) {
            return "GENERATE_AND_VALIDATE";
        }
        if (path.endsWith("/generate")) {
            return "GENERATE_TEST";
        }
        if (path.endsWith("/execute")) {
            return "EXECUTE_TEST";
        }
        if (path.contains("/stream")) {
            return "SSE";
        }
        if (path.equals("/api/health") || path.equals("/api/health/")) {
            return "HEALTH_CHECK";
        }
        return method(method == null ? HttpMethod.GET : method) + " " + path;
    }

    private static String method(ServerHttpRequest request) {
        return method(request.getMethod());
    }

    private static String method(HttpMethod method) {
        return method == null ? "GET" : method.name().toUpperCase(Locale.ROOT);
    }
}
