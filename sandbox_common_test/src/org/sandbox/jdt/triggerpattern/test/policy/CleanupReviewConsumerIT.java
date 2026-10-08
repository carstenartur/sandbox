/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.triggerpattern.test.policy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Real Docker qualification of the standalone consumer, only in consumer-e2e. */
class CleanupReviewConsumerIT {
    @TempDir Path temporary;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nativeEclipseMetadataAndCompleteEditsSurviveExternalUse(boolean nested) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        Path action = root.resolve(".github/actions/cleanup-review/run-cleanup-review.sh");
        Path template = root.resolve("examples/cleanup-review/eclipse");
        Path repository = Files.createDirectory(temporary.resolve("independent consumer"));
        List<String> projects = nested ? List.of("plugin", "test project") : List.of("");
        for (String project : projects) copy(template, repository.resolve(project));
        if (nested) Files.writeString(repository.resolve(".project"),
                "<projectDescription><natures/></projectDescription>\n", StandardCharsets.UTF_8);
        run(repository, "git", "init", "-q");
        run(repository, "git", "config", "user.name", "Consumer Test");
        run(repository, "git", "config", "user.email", "consumer@example.invalid");
        run(repository, "git", "config", "core.autocrlf", "false");
        run(repository, "git", "add", ".");
        run(repository, "git", "commit", "-qm", "base");
        String base = run(repository, "git", "rev-parse", "HEAD").strip();
        for (String project : projects) {
            Path source = repository.resolve(project).resolve("src/example/EncodingExample.java");
            Files.writeString(source, Files.readString(source, StandardCharsets.UTF_8) + "\n// PR change\n", StandardCharsets.UTF_8);
        }
        run(repository, "git", "add", ".");
        run(repository, "git", "commit", "-qm", "PR head");
        String head = run(repository, "git", "rev-parse", "HEAD").strip();
        String expected = compileAndRun(repository.resolve(projects.getFirst()), temporary.resolve("before-bin"));
        assertEquals("2:1", expected.strip());
        Path evidence = temporary.resolve("evidence");
        run(repository, "bash", action.toString(), "--base-sha", base, "--head-sha", head,
                "--image", System.getProperty("cleanup.review.image", "ghcr.io/carstenartur/sandbox-cleanup:latest"),
                "--java-home", System.getProperty("java.home"), "--output-dir", evidence.toString());
        String patch = Files.readString(evidence.resolve("suggestions.patch"), StandardCharsets.UTF_8);
        assertTrue(patch.contains("+import java.nio.charset.StandardCharsets;"), patch);
        assertTrue(patch.contains("+        return StandardCharsets.UTF_8;"), patch);
        assertTrue(patch.contains("+        return StandardCharsets.ISO_8859_1;"), patch);
        assertEquals(projects.size(), run(repository, "git", "diff", "--name-only").lines().count());
        for (String project : projects) {
            Path target = repository.resolve(project);
            for (String metadata : List.of(".project", ".classpath", ".settings/org.eclipse.jdt.core.prefs")) {
                assertArrayEquals(Files.readAllBytes(template.resolve(metadata)), Files.readAllBytes(target.resolve(metadata)), metadata);
            }
            assertEquals(expected, compileAndRun(target, temporary.resolve("after-" + projects.indexOf(project))));
        }
        assertFalse(Files.exists(repository.resolve("sandbox_common_test")));
        run(repository, "git", "add", "-A");
        String tree = run(repository, "git", "write-tree");
        run(repository, "git", "apply", "--reverse", "--index", evidence.resolve("suggestions.patch").toString());
        assertEquals("", run(repository, "git", "status", "--porcelain").strip());
        run(repository, "git", "apply", "--index", evidence.resolve("suggestions.patch").toString());
        assertEquals(tree, run(repository, "git", "write-tree"));
        Path retained = root.resolve("sandbox_common_test/target/cleanup-review/consumer-evidence/" + (nested ? "nested" : "single"));
        copy(evidence, retained);
        Files.writeString(retained.resolve("verification.txt"), "Base: " + base + "\nHead: " + head
                + "\nComplete cleaned tree: " + tree + "Behavior before/after: " + expected, StandardCharsets.UTF_8);
    }

    private String compileAndRun(Path project, Path output) throws Exception {
        Files.createDirectories(output);
        run(project, Path.of(System.getProperty("java.home"), "bin", "javac").toString(), "--release", "11",
                "-d", output.toString(), "src/example/EncodingExample.java");
        return run(project, Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp", output.toString(), "example.EncodingExample");
    }

    private String run(Path cwd, String... command) throws Exception {
        Path log = Files.createTempFile(temporary, "command-", ".log");
        var builder = new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().remove("GITHUB_OUTPUT");
        Process process = builder.start();
        try {
            assertTrue(process.waitFor(Duration.ofMinutes(3).toSeconds(), TimeUnit.SECONDS), "Timed out: " + List.of(command));
            String text = Files.readString(log, StandardCharsets.UTF_8);
            assertEquals(0, process.exitValue(), List.of(command) + "\n" + text);
            return text;
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private static void copy(Path source, Path target) throws Exception {
        try (var paths = Files.walk(source)) {
            for (Path entry : paths.toList()) {
                Path destination = target.resolve(source.relativize(entry));
                if (Files.isDirectory(entry)) Files.createDirectories(destination);
                else Files.copy(entry, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
