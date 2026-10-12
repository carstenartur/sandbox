/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CorpusFilesTest {
    @TempDir Path directory;
    @Test void exactSelectionIsSortedAndExcludesUnrequestedSources() throws Exception {
        try (var jar = archive("b/B.java", "a/A.java", "README")) {
            assertEquals(List.of("a/A.java"), CorpusFiles.select(jar, List.of("a/A.java")));
            assertEquals(List.of("a/A.java", "b/B.java"), CorpusFiles.select(jar, List.of()));
        }
    }
    @Test void missingSelectionFailsInsteadOfProducingAnEmptySuccess() throws Exception {
        try (var jar = archive("A.java")) { assertThrows(IOException.class, () -> CorpusFiles.select(jar, List.of("B.java"))); }
    }
    @Test void duplicateSelectionFails() throws Exception {
        try (var jar = archive("A.java")) { assertThrows(IOException.class, () -> CorpusFiles.select(jar, List.of("A.java", "A.java"))); }
    }
    @Test void emptyArchiveFails() throws Exception {
        try (var jar = archive("README")) { assertThrows(IOException.class, () -> CorpusFiles.select(jar, List.of())); }
    }
    @Test void parentTraversalIsRejected() { assertThrows(IOException.class, () -> CorpusFiles.relative("../A.java")); }
    @Test void absolutePathsAreRejected() { assertThrows(IOException.class, () -> CorpusFiles.relative("/A.java")); }
    @Test void windowsPathsAreRejectedOnEveryHost() { assertThrows(IOException.class, () -> CorpusFiles.relative("C:\\A.java")); }
    @Test void nonJavaSelectionsAreRejected() { assertThrows(IOException.class, () -> CorpusFiles.relative("README")); }
    @Test void nestedTraversalIsRejected() { assertThrows(IOException.class, () -> CorpusFiles.relative("a/../A.java")); }
    @Test void sourceHashMustMatchBeforeProcessing() throws Exception {
        Path source = directory.resolve("source"); Files.writeString(source, "test");
        assertEquals("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08", CorpusFiles.sha256(source));
        assertThrows(IOException.class, () -> CorpusFiles.requireHash(source, "0".repeat(64)));
        assertThrows(IOException.class, () -> CorpusFiles.requireHash(source, "not-a-hash"));
    }
    private JarFile archive(String... entries) throws Exception {
        Path archive = directory.resolve("source.jar");
        try (var output = new JarOutputStream(Files.newOutputStream(archive))) {
            for (String name : entries) { output.putNextEntry(new JarEntry(name)); output.closeEntry(); }
        }
        return new JarFile(archive.toFile());
    }
}
