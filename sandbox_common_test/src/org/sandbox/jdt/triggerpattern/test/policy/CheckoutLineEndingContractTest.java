/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.triggerpattern.test.policy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The actual checkout must preserve executable scripts and workflow contracts. */
class CheckoutLineEndingContractTest {
    @TempDir Path temporary;

    @Test
    void windowsGitSettingsCannotRewriteScriptsOrWorkflowText() throws Exception {
        Path repository = Files.createDirectory(temporary.resolve("checkout"));
        git(repository, "init");
        git(repository, "config", "core.autocrlf", "true");
        git(repository, "config", "core.eol", "crlf");
        git(repository, "config", "core.safecrlf", "false");
        Files.copy(repositoryRoot().resolve(".gitattributes"), repository.resolve(".gitattributes"));
        Map<String, byte[]> expected = Map.of(
            ".github/workflows/fixture.yml", "name: Fixture\non: push\n".getBytes(StandardCharsets.UTF_8),
            ".github/scripts/fixture.sh", "#!/bin/sh\nprintf 'fixture\\n'\n".getBytes(StandardCharsets.UTF_8),
            "mvnw", "#!/bin/sh\nexit 0\n".getBytes(StandardCharsets.UTF_8),
            "mvnw.cmd", "@echo off\r\nexit /b 0\r\n".getBytes(StandardCharsets.UTF_8),
            "sandbox_eclipse_help_swtbot_test/fixtures/mathematics/01-bouncy-castle-prime-product/before.java.txt",
                "int product = 3 * 5;\n".getBytes(StandardCharsets.UTF_8),
            "sandbox_eclipse_help_swtbot_test/fixtures/mathematics/01-bouncy-castle-prime-product/after.java.txt",
                "int product = 15;\n".getBytes(StandardCharsets.UTF_8),
            "fixture.png", new byte[] {0, 13, 10, 1, 10, 2});
        for (var entry : expected.entrySet()) {
            Path file = repository.resolve(entry.getKey());
            Files.createDirectories(file.getParent());
            Files.write(file, entry.getValue());
        }
        git(repository, "add", "--all");
        for (String file : expected.keySet()) Files.delete(repository.resolve(file));
        git(repository, "checkout-index", "--all");
        for (var entry : expected.entrySet()) {
            assertArrayEquals(entry.getValue(), Files.readAllBytes(repository.resolve(entry.getKey())), entry.getKey());
        }
    }

    private static void git(Path directory, String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of("git"));
        command.addAll(List.of(arguments));
        Path log = Files.createTempFile(directory.getParent(), "git-command-", ".log");
        Process process = new ProcessBuilder(command).directory(directory.toFile())
            .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), command.toString());
            assertEquals(0, process.exitValue(), () -> {
                try { return Files.readString(log, StandardCharsets.UTF_8); }
                catch (java.io.IOException failure) { return failure.toString(); }
            });
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private static Path repositoryRoot() {
        for (Path path = Path.of(System.getProperty("user.dir")).toAbsolutePath(); path != null; path = path.getParent()) {
            if (Files.isRegularFile(path.resolve(".gitattributes")) && Files.isRegularFile(path.resolve("pom.xml"))) return path;
        }
        throw new IllegalStateException("Cannot find the repository's checkout attributes");
    }
}
