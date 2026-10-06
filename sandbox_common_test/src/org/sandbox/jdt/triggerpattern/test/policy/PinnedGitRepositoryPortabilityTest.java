/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.triggerpattern.test.policy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PinnedGitRepositoryPortabilityTest {
    @TempDir Path temporary;

    @Test
    void sourceBytesAndCheckoutPolicyDoNotDependOnHostLineEndings() throws Exception {
        Path source = temporary.resolve("source");
        try (Git git = Git.init().setDirectory(source.toFile()).call()) {
            var config = git.getRepository().getConfig();
            config.setBoolean("core", null, "autocrlf", false);
            config.save();
            byte[] lf = "first\nsecond\n".getBytes(StandardCharsets.UTF_8);
            byte[] crlf = "first\r\nsecond\r\n".getBytes(StandardCharsets.UTF_8);
            Files.write(source.resolve("lf.txt"), lf);
            Files.write(source.resolve("crlf.txt"), crlf);
            git.add().addFilepattern(".").call();
            var commit = git.commit().setMessage("Pinned byte fixtures")
                .setAuthor("Sandbox test", "sandbox@example.invalid")
                .setCommitter("Sandbox test", "sandbox@example.invalid").call();
            git.branchCreate().setName("fixture").setStartPoint(commit).call();
            try (var checkout = PinnedGitRepository.cloneAt(temporary.resolve("checkout"), source.toUri(),
                    "fixture", commit.name()); var inspected = Git.open(checkout.directory().toFile())) {
                assertEquals("false", inspected.getRepository().getConfig().getString("core", null, "autocrlf"));
                assertEquals("lf", inspected.getRepository().getConfig().getString("core", null, "eol"));
                assertArrayEquals(lf, Files.readAllBytes(checkout.directory().resolve("lf.txt")));
                assertArrayEquals(crlf, Files.readAllBytes(checkout.directory().resolve("crlf.txt")));
            }
        }
        assertFalse(Files.exists(temporary.resolve("checkout")));
    }

    @Test
    void closingOwnedReadOnlyFilesPreservesNeighboringFiles() throws Exception {
        Path source = temporary.resolve("source");
        Path destination = temporary.resolve("checkout");
        Path neighbor = Files.writeString(temporary.resolve("keep.txt"), "user-owned", StandardCharsets.UTF_8);
        Path readOnly = destination.resolve("read-only.txt");
        try (Git git = Git.init().setDirectory(source.toFile()).call()) {
            Files.writeString(source.resolve("fixture.txt"), "pinned\n", StandardCharsets.UTF_8);
            git.add().addFilepattern(".").call();
            var commit = git.commit().setMessage("Pinned cleanup fixture")
                .setAuthor("Sandbox test", "sandbox@example.invalid")
                .setCommitter("Sandbox test", "sandbox@example.invalid").call();
            git.branchCreate().setName("fixture").setStartPoint(commit).call();
            try (var checkout = PinnedGitRepository.cloneAt(destination, source.toUri(), "fixture", commit.name())) {
                assertEquals(commit.name(), checkout.headCommit());
                Files.writeString(readOnly, "owned by the fixture", StandardCharsets.UTF_8);
                assertTrue(readOnly.toFile().setWritable(false, false), "The fixture must really become read-only");
            } finally {
                if (Files.exists(readOnly)) readOnly.toFile().setWritable(true, false);
            }
        }
        assertFalse(Files.exists(destination));
        assertEquals("user-owned", Files.readString(neighbor, StandardCharsets.UTF_8));
    }
}
