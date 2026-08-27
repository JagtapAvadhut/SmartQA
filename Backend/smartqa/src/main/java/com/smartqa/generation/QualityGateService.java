package com.smartqa.generation;

import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.SmartQaException;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class QualityGateService {

    private static final Logger log = LoggerFactory.getLogger(QualityGateService.class);
    private static final Pattern CLASS_NAME = Pattern.compile("public\\s+class\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern ASSERT_VISIBLE = Pattern.compile("Assertions\\.assertTrue\\([^;]*isVisible\\(\\)");
    private static final List<String> FORBIDDEN = List.of(
            "Thread.sleep",
            "waitForTimeout",
            "Runtime.getRuntime",
            "ProcessBuilder",
            "System.exit",
            "javax.script",
            "URLClassLoader",
            "Files.delete",
            "ObjectInputStream",
            "native ",
            "setForce(true)",
            "setForce( true)",
            ".click(0,",
            "mouse().click",
            ".click(new Position",
            "org.openqa.selenium",
            "ChromeDriver",
            "FirefoxDriver",
            "Runtime.exec",
            "getRuntime().exec",
            "ProcessHandle",
            "java.net.Socket",
            "Files.write",
            "Files.writeString",
            "ScriptEngine",
            "javax.script.ScriptEngine"
    );

    public QualityGateResult validateAndCompile(String source) {
        long started = System.nanoTime();
        TraceLogger.info("QUALITY_GATE", "QUALITY_GATE_STARTED", "Quality gate started", TraceMeta.of(
                "codeLength", source == null ? 0 : source.length()
        ));
        TraceLogger.info("QUALITY_GATE", "PARSE_STARTED", "Parsing generated source");
        source = GeneratedCodeSanitizer.sanitize(source);
        if (source == null || source.isBlank()) {
            return fail("PARSE", "Generated source is empty", started);
        }
        String lower = source.toLowerCase(Locale.ROOT);
        if (!source.contains("com.microsoft.playwright")) {
            return fail("PARSE", "Missing Playwright imports", started);
        }
        if (!source.contains("org.junit.jupiter")) {
            return fail("PARSE", "Missing JUnit imports", started);
        }
        if (!source.contains("@Test")) {
            return fail("PARSE", "Missing JUnit @Test method", started);
        }
        if (emptyTestBody(source)) {
            return fail("PARSE", "Zero-test or empty @Test method is not allowed", started);
        }
        if (!lower.contains("playwright")) {
            return fail("PARSE", "Generated code does not use Playwright", started);
        }
        for (String forbidden : FORBIDDEN) {
            if (source.contains(forbidden)) {
                return fail("PARSE", "Generated code contains forbidden API: " + forbidden, started);
            }
        }
        Matcher matcher = CLASS_NAME.matcher(source);
        if (!matcher.find()) {
            return fail("PARSE", "Could not find a public class", started);
        }
        String className = matcher.group(1);
        TraceLogger.info("QUALITY_GATE", "PARSE_COMPLETED", "Parse checks passed",
                (System.nanoTime() - started) / 1_000_000, TraceMeta.of("className", className));
        TraceLogger.info("QUALITY_GATE", "COMPILE_STARTED", "Compiling generated source", TraceMeta.of("className", className));
        try {
            compile(source, className);
        } catch (Exception ex) {
            log.warn("quality_gate_compile_failed class={}", className, ex);
            return fail("COMPILE", "Generated code did not compile: " + ex.getMessage(), started, ex);
        }
        TraceLogger.info("QUALITY_GATE", "COMPILE_COMPLETED", "Compilation succeeded",
                (System.nanoTime() - started) / 1_000_000, TraceMeta.of("className", className));
        TraceLogger.info("QUALITY_GATE", "LOCATOR_VALIDATION_STARTED", "Checking locator usage");
        boolean hasLocator = source.contains("getBy") || source.contains("locator(");
        TraceLogger.info("QUALITY_GATE", "LOCATOR_VALIDATION_COMPLETED", "Locator validation finished",
                TraceMeta.of("hasLocatorApi", hasLocator));
        TraceLogger.info("QUALITY_GATE", "ASSERTION_VALIDATION_STARTED", "Checking assertions", null);
        boolean hasAssertion = source.contains("Assertions.") || source.contains("assert");
        if (ASSERT_VISIBLE.matcher(source).find() && !source.contains(".waitFor(")) {
            return fail("ASSERTION",
                    "Visibility assertions must waitFor the locator before isVisible()", started);
        }
        if (!hasAssertion) {
            return fail("ASSERTION", "Generated test is missing assertions", started);
        }
        TraceLogger.info("QUALITY_GATE", "ASSERTION_VALIDATION_COMPLETED", "Assertion validation finished",
                TraceMeta.of("hasAssertion", hasAssertion));
        log.info("quality_gate_passed class={}", className);
        TraceLogger.info("QUALITY_GATE", "QUALITY_GATE_PASSED", "Quality gate passed",
                (System.nanoTime() - started) / 1_000_000, TraceMeta.of("className", className));
        return QualityGateResult.passed(className);
    }

    private QualityGateResult fail(String stage, String message, long started) {
        return fail(stage, message, started, null);
    }

    private QualityGateResult fail(String stage, String message, long started, Exception error) {
        if (error != null) {
            TraceLogger.error("QUALITY_GATE", "QUALITY_GATE_FAILED", message, error,
                    (System.nanoTime() - started) / 1_000_000, TraceMeta.of("stage", stage));
        } else {
            TraceLogger.warn("QUALITY_GATE", "QUALITY_GATE_FAILED", message, TraceMeta.of(
                    "stage", stage,
                    "durationMs", (System.nanoTime() - started) / 1_000_000
            ));
        }
        return QualityGateResult.failed(message);
    }

    public QualityGateResult requirePass(String source) {
        QualityGateResult result = validateAndCompile(source);
        if (!result.passed()) {
            throw new SmartQaException(ErrorCode.QUALITY_GATE_FAILED, result.message());
        }
        return result;
    }

    private void compile(String source, String className) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler is not available. Run SmartQA with a JDK, not a JRE.");
        }
        Path dir = Files.createTempDirectory("smartqa-qg-");
        Path javaFile = dir.resolve(className + ".java");
        Files.writeString(javaFile, source, StandardCharsets.UTF_8);
        javax.tools.DiagnosticCollector<javax.tools.JavaFileObject> diagnostics = new javax.tools.DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            var units = fileManager.getJavaFileObjects(javaFile.toFile());
            boolean success = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    List.of("-classpath", CompileClasspath.resolve()),
                    null,
                    units
            ).call();
            if (!success) {
                String details = diagnostics.getDiagnostics().stream()
                        .map(d -> d.getLineNumber() + ":" + d.getMessage(null))
                        .reduce((a, b) -> a + "; " + b)
                        .orElse("unknown javac error");
                throw new IllegalStateException("javac reported errors: " + details);
            }
        } finally {
            Files.walk(dir)
                    .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                            // temp cleanup
                        }
                    });
        }
    }

    public record QualityGateResult(boolean passed, String message, String className) {
        public static QualityGateResult passed(String className) {
            return new QualityGateResult(true, "Quality gate passed", className);
        }

        public static QualityGateResult failed(String message) {
            return new QualityGateResult(false, message, null);
        }
    }

    private static boolean emptyTestBody(String source) {
        return Pattern.compile(
                "@Test\\s+(?:public\\s+)?void\\s+\\w+\\s*\\([^)]*\\)\\s*\\{\\s*}",
                Pattern.DOTALL
        ).matcher(source).find();
    }
}
