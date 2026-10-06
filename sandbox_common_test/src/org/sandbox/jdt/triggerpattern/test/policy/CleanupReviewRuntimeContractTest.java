/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.triggerpattern.test.policy;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the actual Docker invocation adapter without requiring a Docker daemon. */
@EnabledOnOs(OS.LINUX)
class CleanupReviewRuntimeContractTest {
    @TempDir Path temporary;

    @Test
    void suppliedJdkIsReadOnlyAndUsedByBothJavaHomeAndPathLaunchers() throws Exception {
        Path home = Files.createDirectories(temporary.resolve("JDK with spaces"));
        Files.createDirectories(home.resolve("bin"));
        Files.writeString(home.resolve("release"), "JAVA_VERSION=\"25\"\n");
        Path java = Files.writeString(home.resolve("bin/java"), "#!/bin/sh\nexit 0\n");
        assertTrue(java.toFile().setExecutable(true));
        Run run = runAdapter(home.toString());
        assertEquals(0, run.exitCode(), run.output());
        List<String> args = arguments(run.arguments());
        assertArgumentPair(args, "--volume", home.toRealPath() + ":/opt/sandbox-review-jdk:ro");
        assertArgumentPair(args, "--env", "JAVA_HOME=/opt/sandbox-review-jdk");
        assertArgumentPair(args, "--env", "PATH=/opt/sandbox-review-jdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin");
        assertFalse(args.contains("--java-home"), "Host runtime selection is not a cleanup application argument");
        assertEquals("JAVA_VERSION=\"25\"\n", Files.readString(home.resolve("release")));
    }

    @Test
    void omittedRuntimeRetainsTheContainerDefault() throws Exception {
        Run run = runAdapter(null);
        assertEquals(0, run.exitCode(), run.output());
        assertTrue(arguments(run.arguments()).stream().noneMatch(arg -> arg.contains("sandbox-review-jdk")));
    }

    @Test
    void incompleteJdkIsRejectedBeforeInvokingDocker() throws Exception {
        Path invalid = Files.createDirectory(temporary.resolve("incomplete-jdk"));
        Run run = runAdapter(invalid.toString());
        assertNotEquals(0, run.exitCode());
        assertTrue(run.output().contains("JDK must contain an executable bin/java and a release file"), run.output());
        assertFalse(Files.exists(run.arguments()), "An invalid JDK must not reach Docker");
    }

    @Test
    void repositoryReviewProvisionsAndForwardsTheSupportedRuntime() throws Exception {
        Path root = root();
        String workflow = Files.readString(root.resolve(".github/workflows/pr-auto-cleanup.yml"));
        int setup = workflow.indexOf("name: Set up review JDK");
        int action = workflow.indexOf("name: Analyze changed Java source and publish suggestions");
        assertTrue(setup >= 0 && setup < action, "The JDK must be provisioned before the cleanup action");
        assertTrue(workflow.substring(setup, action).contains("java-version: '25'"));
        assertTrue(workflow.substring(action).contains("java-home: ${{ env.JAVA_HOME }}"));
        String composite = Files.readString(root.resolve(".github/actions/cleanup-review/action.yml"));
        assertTrue(composite.contains("REVIEW_JAVA_HOME: ${{ inputs.java-home }}"));
        assertTrue(composite.contains("--java-home \"${REVIEW_JAVA_HOME:-}\""));
    }

    private Run runAdapter(String javaHome) throws Exception {
        Path repository = Files.createDirectory(temporary.resolve("repository"));
        Path source = Files.createDirectories(repository.resolve("project/src"));
        Files.writeString(source.getParent().resolve(".project"), "<projectDescription><name>fixture</name><natures>"
                + "<nature>org.eclipse.jdt.core.javanature</nature></natures></projectDescription>");
        Path java = Files.writeString(source.resolve("A.java"), "class A {}\n");
        Files.writeString(repository.resolve("cleanup.properties"), "cleanup.explicit_encoding=true\n");
        git(repository, "init");
        git(repository, "config", "user.name", "Sandbox test");
        git(repository, "config", "user.email", "sandbox@example.invalid");
        git(repository, "add", ".");
        git(repository, "commit", "-m", "base");
        Files.writeString(java, "class A {} // selected change\n");
        git(repository, "add", ".");
        git(repository, "commit", "-m", "head");
        Path arguments = temporary.resolve("docker-arguments");
        Path docker = Files.writeString(temporary.resolve("docker"), """
                #!/usr/bin/env bash
                set -euo pipefail
                if [[ "$1" == image ]]; then printf 'fixture@sha256:identity\n'; fi
                if [[ "$1" == run ]]; then printf '%s\0' "$@" > "$DOCKER_ARGUMENTS"; fi
                """.replace("\0", "\\0"));
        assertTrue(docker.toFile().setExecutable(true));
        List<String> command = new ArrayList<>(List.of("bash",
                root().resolve(".github/actions/cleanup-review/run-cleanup-review.sh").toString(),
                "--base-sha", "HEAD^", "--head-sha", "HEAD", "--config-file", "cleanup.properties",
                "--image", "fixture-image", "--output-dir", temporary.resolve("evidence").toString()));
        if (javaHome != null) command.addAll(List.of("--java-home", javaHome));
        ProcessBuilder builder = new ProcessBuilder(command).directory(repository.toFile());
        builder.environment().put("DOCKER_BIN", docker.toString());
        builder.environment().put("DOCKER_ARGUMENTS", arguments.toString());
        builder.environment().put("SANDBOX_CLEANUP_SKIP_PULL", "true");
        Path output = temporary.resolve("adapter.log");
        Process process = builder.redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Cleanup adapter timed out");
            return new Run(process.exitValue(), Files.readString(output, StandardCharsets.UTF_8), arguments);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private void git(Path repository, String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of("git", "-c", "core.autocrlf=false"));
        command.addAll(List.of(arguments));
        Path output = temporary.resolve("git.log");
        Process process = new ProcessBuilder(command).directory(repository.toFile())
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(20, TimeUnit.SECONDS));
            assertEquals(0, process.exitValue(), Files.readString(output));
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private static List<String> arguments(Path file) throws Exception {
        return Arrays.asList(Files.readString(file).split("\0"));
    }

    private static void assertArgumentPair(List<String> arguments, String key, String value) {
        int index = arguments.indexOf(value);
        assertTrue(index > 0 && key.equals(arguments.get(index - 1)), arguments.toString());
        assertTrue(index < arguments.indexOf("fixture-image"), "Docker runtime options must precede the image");
    }

    private static Path root() {
        for (Path root = Path.of("").toAbsolutePath(); root != null; root = root.getParent()) {
            if (Files.isRegularFile(root.resolve(".github/actions/cleanup-review/action.yml"))) return root;
        }
        throw new IllegalStateException("Cannot find the cleanup review action");
    }

    private record Run(int exitCode, String output, Path arguments) { }
}
