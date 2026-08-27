package com.smartqa.execution;

import com.smartqa.common.config.SmartQaProperties;
import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.SmartQaException;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import com.smartqa.execution.cancel.CancellationToken;
import com.smartqa.generation.CompileClasspath;
import com.smartqa.generation.GeneratedCodeSanitizer;
import com.smartqa.generation.QualityGateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class IsolatedTestExecutor {

    private static final Logger log = LoggerFactory.getLogger(IsolatedTestExecutor.class);
    private static final Pattern CLASS_NAME = Pattern.compile("public\\s+class\\s+([A-Za-z_][A-Za-z0-9_]*)");

    private final SmartQaProperties properties;
    private final QualityGateService qualityGateService;
    private final ConcurrentHashMap<UUID, Process> activeProcesses = new ConcurrentHashMap<>();

    public IsolatedTestExecutor(SmartQaProperties properties, QualityGateService qualityGateService) {
        this.properties = properties;
        this.qualityGateService = qualityGateService;
    }

    public ExecutionResult run(String source, Path screenshotDir) {
        return run(source, screenshotDir, null, null);
    }

    public ExecutionResult run(String source, Path screenshotDir, CancellationToken token, UUID runId) {
        if (token != null) {
            token.throwIfStopped();
        }
        source = GeneratedCodeSanitizer.sanitize(source);
        qualityGateService.requirePass(source);
        Matcher matcher = CLASS_NAME.matcher(source);
        if (!matcher.find()) {
            throw new SmartQaException(ErrorCode.QUALITY_GATE_FAILED, "Generated class name is missing");
        }
        String className = matcher.group(1);
        Path workDir = null;
        Process process = null;
        long started = System.currentTimeMillis();
        try {
            if (token != null) {
                token.throwIfStopped();
            }
            workDir = Files.createTempDirectory("smartqa-run-");
            Path javaFile = workDir.resolve(className + ".java");
            Files.writeString(javaFile, source, StandardCharsets.UTF_8);
            compile(javaFile);
            if (token != null) {
                token.throwIfStopped();
            }
            List<String> command = new ArrayList<>();
            command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
            command.add("-cp");
            command.add(workDir + javaPathSeparator() + CompileClasspath.resolve());
            command.add("-Dsmartqa.screenshot.dir=" + screenshotDir.toAbsolutePath());
            command.add("-Dsmartqa.browser.headless=" + properties.getBrowser().isHeadless());
            command.add("-Dsmartqa.browser.maximize-headed=" + properties.getBrowser().isMaximizeHeaded());
            command.add("-Dsmartqa.browser.zoom-percent=" + properties.getBrowser().getZoomPercent());
            command.add("-Dsmartqa.browser.headless-viewport-width=" + properties.getBrowser().getHeadlessViewportWidth());
            command.add("-Dsmartqa.browser.headless-viewport-height=" + properties.getBrowser().getHeadlessViewportHeight());
            command.add("com.smartqa.execution.runtime.IsolatedJunitRunner");
            command.add(className);
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(workDir.toFile());
            process = builder.start();
            if (runId != null) {
                activeProcesses.put(runId, process);
            }
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            Thread outThread = copy(process.getInputStream(), stdout);
            Thread errThread = copy(process.getErrorStream(), stderr);
            int timeout = Math.max(30, properties.getExecution().getValidatorTimeoutSeconds());
            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                if (token != null && token.isStopRequested()) {
                    token.throwIfStopped();
                }
                throw new SmartQaException(ErrorCode.EXECUTION_TIMEOUT,
                        "VALIDATOR_TIMEOUT: isolated generated test timed out after " + timeout + "s");
            }
            if (token != null && token.isStopRequested()) {
                process.destroyForcibly();
                token.throwIfStopped();
            }
            outThread.join(2000);
            errThread.join(2000);
            int exit = process.exitValue();
            TraceLogger.info("EXECUTION", "ISOLATED_JVM_COMPLETED", "Isolated test process finished",
                    System.currentTimeMillis() - started,
                    TraceMeta.of("exitCode", exit, "className", className));
            return new ExecutionResult(
                    exit,
                    stdout.toString(StandardCharsets.UTF_8),
                    stderr.toString(StandardCharsets.UTF_8),
                    System.currentTimeMillis() - started,
                    exit == 0 ? null : firstLine(stderr.toString(StandardCharsets.UTF_8))
            );
        } catch (SmartQaException ex) {
            TraceLogger.error("EXECUTION", "ISOLATED_JVM_FAILED", ex.getMessage(), ex,
                    System.currentTimeMillis() - started, TraceMeta.of("className", className));
            throw ex;
        } catch (Exception ex) {
            TraceLogger.error("EXECUTION", "ISOLATED_JVM_FAILED", "Isolated execution failed", ex,
                    System.currentTimeMillis() - started, TraceMeta.of("className", className));
            throw new SmartQaException(ErrorCode.EXECUTION_FAILED, "Isolated execution failed", ex);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            if (runId != null) {
                activeProcesses.remove(runId);
            }
            TempArtifactCleanup.deleteQuietly(workDir);
        }
    }

    public void stop(Process process) {
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
    }

    public void stopByRunId(UUID runId) {
        Process process = activeProcesses.get(runId);
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
            log.info("stopped_child_process runId={}", runId);
        }
    }

    private void compile(Path javaFile) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            boolean success = compiler.getTask(
                    null,
                    fileManager,
                    null,
                    List.of("-classpath", CompileClasspath.resolve()),
                    null,
                    fileManager.getJavaFileObjects(javaFile.toFile())
            ).call();
            if (!success) {
                throw new SmartQaException(ErrorCode.QUALITY_GATE_FAILED, "Execution compile failed");
            }
        }
    }

    private static Thread copy(InputStream in, ByteArrayOutputStream out) {
        Thread thread = new Thread(() -> {
            try {
                in.transferTo(out);
            } catch (Exception ignored) {
            }
        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static String javaPathSeparator() {
        return System.getProperty("path.separator");
    }

    private static String firstLine(String text) {
        if (text == null || text.isBlank()) {
            return "Test failed";
        }
        return text.lines().findFirst().orElse("Test failed");
    }

    public record ExecutionResult(int exitCode, String stdout, String stderr, long durationMs, String errorMessage) {
    }
}
