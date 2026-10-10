/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.ui.helper.views;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BundleClassFingerprintTest {
    @TempDir Path temporary;

    @Test void packagingTimeQualifierAndEntryOrderDoNotChangeExecutableIdentity() throws Exception {
        var entries = new LinkedHashMap<String, byte[]>();
        entries.put("a/A.class", new byte[]{1, 2});
        entries.put("a/A$Nested.class", new byte[]{3, 4});
        var reverse = new LinkedHashMap<String, byte[]>();
        reverse.put("a/A$Nested.class", new byte[]{3, 4});
        reverse.put("a/A.class", new byte[]{1, 2});
        assertEquals(BundleClassFingerprint.sha256(jar(entries, 1600000000000L, "old")),
                BundleClassFingerprint.sha256(jar(reverse, 1700000000000L, "new")));
    }
    @Test void everyClassPathAndByteContributesToIdentity() throws Exception {
        String original = BundleClassFingerprint.sha256(jar(Map.of("a/A.class", new byte[]{1,2}), 0, "v"));
        for (var change : java.util.List.of(Map.of("a/A.class", new byte[]{1,3}),
                Map.of("a/B.class", new byte[]{1,2}),
                Map.of("a/A.class", new byte[]{1,2}, "a/B.class", new byte[]{3}))) {
            assertNotEquals(original, BundleClassFingerprint.sha256(jar(change, 0, "v")));
        }
    }
    @Test void explodedAndArchivedClassesHaveTheSameIdentity() throws Exception {
        var entries = Map.of("a/A.class", new byte[]{1,2}, "a/B.class", new byte[]{3,4});
        Path directory = Files.createDirectory(temporary.resolve("classes"));
        for (var entry : entries.entrySet()) {
            Path file = directory.resolve(entry.getKey()); Files.createDirectories(java.util.Objects.requireNonNull(file.getParent()));
            Files.write(file, entry.getValue());
        }
        assertEquals(BundleClassFingerprint.sha256(jar(entries, 0, "v")), BundleClassFingerprint.sha256(directory));
    }
    @Test void missingClassPayloadCannotBecomeAnApparentlyValidFingerprint() throws Exception {
        assertThrows(java.io.IOException.class, () -> BundleClassFingerprint.sha256(jar(Map.of(), 0, "v")));
    }
    private Path jar(Map<String,byte[]> entries, long time, String qualifier) throws Exception {
        Path file = Files.createTempFile(temporary, "bundle-", ".jar");
        try (var output = new JarOutputStream(Files.newOutputStream(file))) {
            for (var entry : entries.entrySet()) write(output, entry.getKey(), entry.getValue(), time);
            write(output, "META-INF/MANIFEST.MF", ("Manifest-Version: 1.0\nBundle-Version: 1.0.0." + qualifier + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8), time);
        }
        return file;
    }
    private static void write(JarOutputStream output, String name, byte[] bytes, long time) throws Exception {
        var entry = new JarEntry(name); entry.setTime(time); output.putNextEntry(entry); output.write(bytes); output.closeEntry();
    }
}
