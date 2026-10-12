/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeSet;
import java.util.jar.JarFile;

/** Portable, exact source selection. An absent or unsafe input is never a successful scan. */
final class CorpusFiles {
    private CorpusFiles() { }
    static List<String> select(JarFile jar, List<String> requested) throws IOException {
        var available = new TreeSet<String>();
        for (var entry : jar.stream().filter(e -> !e.isDirectory() && e.getName().endsWith(".java")).toList()) {
            relative(entry.getName());
            if (!available.add(entry.getName())) throw new IOException("Duplicate Java archive entry: " + entry.getName());
        }
        if (available.isEmpty()) throw new IOException("Source archive contains no Java entries");
        if (requested.isEmpty()) return List.copyOf(available);
        var unique = new HashSet<String>();
        for (String name : requested) {
            relative(name);
            if (!unique.add(name) || !available.contains(name)) throw new IOException("Missing or duplicate selection: " + name);
        }
        return unique.stream().sorted().toList();
    }
    static Path relative(String name) throws IOException {
        if (name.isBlank() || !name.endsWith(".java") || name.indexOf('\\') >= 0 || name.indexOf(':') >= 0
                || name.startsWith("/") || name.contains("//")) throw new IOException("Unsafe Java entry: " + name);
        Path path = Path.of(name);
        for (Path part : path) if (part.toString().equals("..") || part.toString().equals(".")) throw new IOException("Unsafe Java entry: " + name);
        if (path.isAbsolute()) throw new IOException("Absolute Java entry: " + name);
        return path;
    }
    static String sha256(Path path) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[65536];
                for (int read; (read = input.read(buffer)) >= 0;) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException(unavailable); }
    }
    static void requireHash(Path path, String expected) throws IOException {
        if (!expected.matches("[0-9a-f]{64}") || !expected.equals(sha256(path))) throw new IOException("Source archive hash mismatch: " + path);
    }
}
