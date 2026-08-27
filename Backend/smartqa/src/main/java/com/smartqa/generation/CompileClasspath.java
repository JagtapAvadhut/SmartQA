package com.smartqa.generation;

import com.microsoft.playwright.Playwright;
import com.smartqa.SmartqaApplication;
import com.smartqa.execution.runtime.IsolatedJunitRunner;
import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.Launcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

public final class CompileClasspath {

    private static final Logger log = LoggerFactory.getLogger(CompileClasspath.class);
    private static volatile String cached;

    private CompileClasspath() {
    }

    public static String resolve() {
        String current = cached;
        if (current != null && current.toLowerCase(Locale.ROOT).contains("playwright")) {
            return current;
        }
        synchronized (CompileClasspath.class) {
            if (cached == null || !cached.toLowerCase(Locale.ROOT).contains("playwright")) {
                cached = build();
                log.info("compile_classpath playwrightPresent={} entries={}",
                        cached.toLowerCase(Locale.ROOT).contains("playwright"),
                        cached.split(File.pathSeparator).length);
            }
            return cached;
        }
    }

    private static String build() {
        Set<String> entries = new LinkedHashSet<>();
        addExplodedClasspath(entries);
        addClassJar(entries, Playwright.class);
        addClassJar(entries, Test.class);
        addClassJar(entries, Launcher.class);
        addClassJar(entries, IsolatedJunitRunner.class);
        addClassJar(entries, SmartqaApplication.class);
        Path fatJar = findFatJar();
        if (fatJar != null) {
            extractBootInf(fatJar, entries);
        }
        return String.join(File.pathSeparator, entries);
    }

    private static void addExplodedClasspath(Set<String> entries) {
        String existing = System.getProperty("java.class.path");
        if (existing == null || existing.isBlank()) {
            return;
        }
        for (String item : existing.split(File.pathSeparator)) {
            if (item.isBlank()) {
                continue;
            }
            Path path = Path.of(item);
            if (!path.isAbsolute()) {
                path = Path.of(System.getProperty("user.dir", ".")).resolve(path).toAbsolutePath().normalize();
            }
            if (Files.isDirectory(path) || (Files.isRegularFile(path) && !isFatJar(path))) {
                entries.add(path.toString());
            }
        }
    }

    private static void addClassJar(Set<String> entries, Class<?> type) {
        try {
            URL location = type.getProtectionDomain().getCodeSource().getLocation();
            Path path = toFilePath(location);
            if (path != null && Files.isRegularFile(path) && !isFatJar(path)) {
                entries.add(path.toAbsolutePath().toString());
            }
        } catch (Exception ignored) {
            // continue with other classpath sources
        }
    }

    private static Path findFatJar() {
        Path fromLocation = toFilePath(codeSource(SmartqaApplication.class));
        if (isFatJar(fromLocation)) {
            return fromLocation;
        }
        String existing = System.getProperty("java.class.path");
        if (existing != null && !existing.contains(File.pathSeparator)) {
            Path path = Path.of(existing);
            if (!path.isAbsolute()) {
                path = Path.of(System.getProperty("user.dir", ".")).resolve(path);
            }
            path = path.toAbsolutePath().normalize();
            if (isFatJar(path)) {
                return path;
            }
        }
        String command = System.getProperty("sun.java.command");
        if (command != null && command.contains(".jar")) {
            for (String token : command.split("\\s+")) {
                if (token.toLowerCase(Locale.ROOT).endsWith(".jar")) {
                    Path path = Path.of(token);
                    if (!path.isAbsolute()) {
                        path = Path.of(System.getProperty("user.dir", ".")).resolve(path);
                    }
                    path = path.toAbsolutePath().normalize();
                    if (isFatJar(path)) {
                        return path;
                    }
                }
            }
        }
        return fromLocation != null && Files.isRegularFile(fromLocation) ? fromLocation : null;
    }

    private static void extractBootInf(Path source, Set<String> entries) {
        try {
            if (!Files.isRegularFile(source) || !source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                return;
            }
            Path cache = Path.of(System.getProperty("java.io.tmpdir"), "smartqa-compile-libs");
            Files.createDirectories(cache);
            Path classes = cache.resolve("classes");
            Files.createDirectories(classes);
            int libs = 0;
            try (JarFile jar = new JarFile(source.toFile())) {
                var extracted = jar.stream()
                        .filter(entry -> !entry.isDirectory())
                        .toList();
                for (ZipEntry entry : extracted) {
                    if (entry.getName().startsWith("BOOT-INF/lib/") && entry.getName().endsWith(".jar") && needed(entry.getName())) {
                        extractLib(jar, entry, cache, entries);
                        libs++;
                    } else if (entry.getName().startsWith("BOOT-INF/classes/")) {
                        extractClass(jar, entry, classes);
                    }
                }
            }
            entries.add(classes.toAbsolutePath().toString());
            log.info("extracted_boot_inf jar={} libs={} classes={}", source, libs, classes);
        } catch (Exception ex) {
            log.warn("Unable to extract compile classpath from {}", source, ex);
        }
    }

    private static boolean needed(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("playwright")
                || lower.contains("/driver-")
                || lower.contains("driver-bundle")
                || lower.contains("gson")
                || lower.contains("junit")
                || lower.contains("opentest4j")
                || lower.contains("apiguardian")
                || lower.contains("engine-commons");
    }

    private static boolean isFatJar(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return false;
        }
        if (!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
            return false;
        }
        try (JarFile jar = new JarFile(path.toFile())) {
            return jar.getEntry("BOOT-INF/classes/") != null || jar.getEntry("BOOT-INF/lib/") != null;
        } catch (Exception ex) {
            return false;
        }
    }

    private static URL codeSource(Class<?> type) {
        try {
            return type.getProtectionDomain().getCodeSource().getLocation();
        } catch (Exception ex) {
            return null;
        }
    }

    private static Path toFilePath(URL location) {
        if (location == null) {
            return null;
        }
        try {
            String spec = location.toString();
            int fileIdx = spec.indexOf("file:");
            if (fileIdx < 0) {
                return null;
            }
            int bang = spec.indexOf('!', fileIdx);
            String fileUrl = bang >= 0 ? spec.substring(fileIdx, bang) : spec.substring(fileIdx);
            return Path.of(URI.create(fileUrl)).toAbsolutePath().normalize();
        } catch (Exception ex) {
            return null;
        }
    }

    private static void extractLib(JarFile jar, ZipEntry entry, Path cache, Set<String> entries) {
        try {
            String filename = Path.of(entry.getName()).getFileName().toString();
            Path target = cache.resolve(filename);
            if (!Files.exists(target) || Files.size(target) != entry.getSize()) {
                try (InputStream in = jar.getInputStream(entry)) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            entries.add(target.toAbsolutePath().toString());
        } catch (Exception ex) {
            log.warn("Unable to extract {}", entry.getName(), ex);
        }
    }

    private static void extractClass(JarFile jar, ZipEntry entry, Path classes) {
        try {
            String relative = entry.getName().substring("BOOT-INF/classes/".length());
            if (relative.isBlank()) {
                return;
            }
            Path target = classes.resolve(relative);
            Files.createDirectories(target.getParent());
            try (InputStream in = jar.getInputStream(entry)) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ex) {
            log.warn("Unable to extract class {}", entry.getName(), ex);
        }
    }
}
