package com.smartqa.debug;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Persists SmartQA traces to disk.
 * Appends are queued to a dedicated writer thread so Netty event-loop threads never block on disk I/O.
 */
@Component
public class TraceStore {

    private static final Logger log = LoggerFactory.getLogger(TraceStore.class);

    private final JsonMapper jsonMapper;
    private final Path directory;
    private final int maxFiles;
    private final int maxAgeDays;
    private final ReentrantLock lock = new ReentrantLock();
    private final Set<String> active = ConcurrentHashMap.newKeySet();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "smartqa-trace-writer");
        thread.setDaemon(true);
        return thread;
    });

    public TraceStore(JsonMapper jsonMapper, com.smartqa.common.config.SmartQaProperties properties) {
        this.jsonMapper = jsonMapper;
        var debug = properties.getDebug();
        this.directory = Path.of(debug.getLogDir()).toAbsolutePath().normalize();
        this.maxFiles = Math.max(10, debug.getMaxFiles());
        this.maxAgeDays = Math.max(1, debug.getMaxAgeDays());
        try {
            Files.createDirectories(this.directory);
        } catch (IOException ex) {
            log.warn("Unable to create SmartQA trace directory {}", this.directory, ex);
        }
        TraceLogger.bind(this);
        cleanupQuietly(null);
    }

    public Path jsonlPath(String traceId) {
        return directory.resolve(traceId + ".jsonl");
    }

    public void markActive(String traceId) {
        if (traceId != null && !traceId.isBlank()) {
            active.add(traceId);
        }
    }

    public void markInactive(String traceId) {
        if (traceId != null) {
            active.remove(traceId);
        }
    }

    public void append(TraceEvent event) {
        if (event == null || event.traceId() == null || event.traceId().isBlank()) {
            return;
        }
        markActive(event.traceId());
        try {
            writer.execute(() -> appendSync(event));
        } catch (RejectedExecutionException ex) {
            log.warn("Trace writer rejected append for {}", event.traceId(), ex);
        }
    }

    private void appendSync(TraceEvent event) {
        Path file = jsonlPath(event.traceId());
        lock.lock();
        try {
            Files.createDirectories(directory);
            String line = jsonMapper.writeValueAsString(SecretMasker.mask(event.toMap())) + System.lineSeparator();
            Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ex) {
            log.warn("Failed to append SmartQA trace {}", event.traceId(), ex);
        } finally {
            lock.unlock();
        }
    }

    public List<TraceEvent> read(String traceId) {
        Path file = jsonlPath(traceId);
        if (!Files.exists(file)) {
            return List.of();
        }
        lock.lock();
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<TraceEvent> events = new ArrayList<>();
            for (String line : lines) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                events.add(fromJson(line));
            }
            return events;
        } catch (Exception ex) {
            log.warn("Failed to read SmartQA trace {}", traceId, ex);
            return List.of();
        } finally {
            lock.unlock();
        }
    }

    public boolean exists(String traceId) {
        return Files.exists(jsonlPath(traceId));
    }

    public String downloadLog(String traceId) {
        return TraceFormatter.toHumanReadable(traceId, read(traceId));
    }

    public String downloadJsonl(String traceId) {
        Path file = jsonlPath(traceId);
        if (!Files.exists(file)) {
            return "";
        }
        lock.lock();
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            log.warn("Failed to read SmartQA jsonl {}", traceId, ex);
            return "";
        } finally {
            lock.unlock();
        }
    }

    public void cleanupQuietly(String keepTraceId) {
        try {
            cleanup(keepTraceId);
        } catch (Exception ex) {
            log.warn("Trace retention cleanup failed", ex);
        }
    }

    private void cleanup(String keepTraceId) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "SMARTQA-*.jsonl")) {
            for (Path path : stream) {
                files.add(path);
            }
        }
        Instant cutoff = Instant.now().minus(maxAgeDays, ChronoUnit.DAYS);
        files.sort(Comparator.comparingLong(this::lastModified).reversed());
        int index = 0;
        for (Path file : files) {
            String name = file.getFileName().toString();
            String id = name.substring(0, name.length() - ".jsonl".length());
            boolean keep = id.equals(keepTraceId) || active.contains(id);
            boolean tooOld = Instant.ofEpochMilli(lastModified(file)).isBefore(cutoff);
            boolean tooMany = index >= maxFiles;
            index++;
            if (keep) {
                continue;
            }
            if (tooOld || tooMany) {
                Files.deleteIfExists(file);
            }
        }
    }

    private long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ex) {
            return 0L;
        }
    }

    @SuppressWarnings("unchecked")
    private TraceEvent fromJson(String line) {
        JsonNode node = jsonMapper.readTree(line);
        Map<String, Object> map = jsonMapper.convertValue(node, Map.class);
        Object metadataRaw = map.get("metadata");
        Map<String, Object> metadata = metadataRaw instanceof Map<?, ?> nested
                ? (Map<String, Object>) nested
                : Map.of();
        Number duration = map.get("durationMs") instanceof Number number ? number : null;
        return new TraceEvent(
                string(map.get("traceId")),
                string(map.get("timestamp")),
                string(map.get("level")),
                string(map.get("component")),
                string(map.get("operation")),
                string(map.get("message")),
                duration == null ? null : duration.longValue(),
                map.get("payload"),
                map.get("result"),
                string(map.get("error")),
                string(map.get("exceptionType")),
                string(map.get("stackTrace")),
                metadata
        );
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @jakarta.annotation.PreDestroy
    void shutdown() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(2, TimeUnit.SECONDS)) {
                writer.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
        }
    }
}
