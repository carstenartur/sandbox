/* SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.distribution;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AggregateEvidenceFreshnessTest {
    @TempDir Path temporary;

    @Test
    void rejectsUnchangedEvidenceWithoutPreviousIdentity() throws Exception {
        Path file = evidence(temporary);
        var previous = AggregateInstallationVerifier.snapshot(file);
        rejectsStale(file, new AggregateInstallationVerifier.FileSnapshot(null, previous.lastModified()));
    }

    @Test
    void rejectsOlderEvidenceWithoutPreviousIdentity() throws Exception {
        Path file = evidence(temporary);
        var previous = new AggregateInstallationVerifier.FileSnapshot(null, FileTime.fromMillis(20_000));
        rejectsStale(file, previous);
    }

    @Test
    void rejectsUnchangedEvidenceWithoutEitherIdentity() throws Exception {
        try (var fileSystem = FileSystems.newFileSystem(temporary.resolve("evidence.zip"), Map.of("create", "true"))) {
            Path file = evidence(fileSystem.getPath("/"));
            var previous = AggregateInstallationVerifier.snapshot(file);
            assertNull(previous.fileKey(), "The ZIP provider exercises genuinely unavailable file identities");
            rejectsStale(file, previous);
        }
    }

    @Test
    void rejectsUnchangedEvidenceWhenCurrentIdentityIsUnavailable() throws Exception {
        try (var fileSystem = FileSystems.newFileSystem(temporary.resolve("evidence.zip"), Map.of("create", "true"))) {
            Path file = evidence(fileSystem.getPath("/"));
            var current = AggregateInstallationVerifier.snapshot(file);
            assertNull(current.fileKey());
            rejectsStale(file, new AggregateInstallationVerifier.FileSnapshot("previous-identity", current.lastModified()));
        }
    }

    @Test
    void acceptsNewerEvidenceWithoutEitherIdentity() throws Exception {
        try (var fileSystem = FileSystems.newFileSystem(temporary.resolve("evidence.zip"), Map.of("create", "true"))) {
            Path file = evidence(fileSystem.getPath("/"));
            var previous = AggregateInstallationVerifier.snapshot(file);
            assertNull(previous.fileKey());
            Files.setLastModifiedTime(file, FileTime.fromMillis(20_000));
            assertDoesNotThrow(() -> AggregateInstallationVerifier.requireFreshFile(file, previous, "Runtime probe result"));
        }
    }

    @Test
    void acceptsNewerEvidenceWithoutPreviousIdentity() throws Exception {
        Path file = evidence(temporary);
        var previous = new AggregateInstallationVerifier.FileSnapshot(null, FileTime.fromMillis(1_000));
        assertDoesNotThrow(() -> AggregateInstallationVerifier.requireFreshFile(file, previous, "Runtime probe result"));
    }

    @Test
    void requiresAResultEvenWithoutAPreviousSnapshot() throws Exception {
        assertThrows(IOException.class, () -> AggregateInstallationVerifier.requireFreshFile(
                temporary.resolve("missing.json"), null, "Runtime probe result"));
        Path file = evidence(temporary);
        assertDoesNotThrow(() -> AggregateInstallationVerifier.requireFreshFile(file, null, "Runtime probe result"));
    }

    private static Path evidence(Path directory) throws IOException {
        Path file = Files.writeString(directory.resolve("runtime.json"), "{}");
        Files.setLastModifiedTime(file, FileTime.fromMillis(10_000));
        return file;
    }

    private static void rejectsStale(Path file, AggregateInstallationVerifier.FileSnapshot previous) {
        IOException failure = assertThrows(IOException.class,
                () -> AggregateInstallationVerifier.requireFreshFile(file, previous, "Runtime probe result"));
        assertEquals("Runtime probe result is stale: " + file, failure.getMessage());
    }
}
