/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.ui.helper.views;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarFile;

/** Identity of every installed class, independent of JAR timestamps and build qualifier.
 * This is not the whole bundle identity; callers retain that separately as run evidence. */
final class BundleClassFingerprint {
    private BundleClassFingerprint() {}

    static String sha256(Path bundle) throws IOException, NoSuchAlgorithmException {
        Map<String, byte[]> classes = new TreeMap<>();
        if (Files.isDirectory(bundle)) {
            try (var files = Files.walk(bundle)) {
                for (Path file : files.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".class")).toList()) { //$NON-NLS-1$
                    String name = bundle.relativize(file).toString().replace(java.io.File.separatorChar, '/');
                    classes.put(name, Files.readAllBytes(file));
                }
            }
        } else {
            try (var jar = new JarFile(bundle.toFile())) {
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    if (!entry.isDirectory() && entry.getName().endsWith(".class")) { //$NON-NLS-1$
                        try (var input = jar.getInputStream(entry)) {
                            if (classes.put(entry.getName(), input.readAllBytes()) != null)
                                throw new IOException("Duplicate class entry: " + entry.getName()); //$NON-NLS-1$
                        }
                    }
                }
            }
        }
        if (classes.isEmpty()) throw new IOException("Bundle contains no class payload"); //$NON-NLS-1$
        MessageDigest digest = MessageDigest.getInstance("SHA-256"); //$NON-NLS-1$
        for (var entry : classes.entrySet()) {
            update(digest, entry.getKey().getBytes(StandardCharsets.UTF_8));
            update(digest, entry.getValue());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void update(MessageDigest digest, byte[] value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value.length).array());
        digest.update(value);
    }
}
