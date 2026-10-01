/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.qa.reporting;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/** Installs only the measured patches in a disposable Tycho 5.0.4 repository. */
public class TychoProbeInstaller {
    private static final String PIN = "bb86061a7a8d0c3ee4d02f358cf30be6e3e6ae4e";
    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("Expected isolated Maven repository, compiled probe classes, stock dependency jars and evidence directory");
        Path repo = Path.of(args[0]).toRealPath();
        if (!Files.readString(repo.resolve(".sandbox-reporting-investigation")).strip().equals(PIN)) {
            throw new IllegalStateException("Missing explicit disposable-repository marker");
        }
        Path classes = Path.of(args[1]).toRealPath();
        Path stock = Path.of(args[2]).toRealPath();
        Path evidence = Path.of(args[3]);
        Files.createDirectories(evidence);
        install(repo, classes, stock, evidence, "org.eclipse.tycho.surefire.junit5", "surefire-junit-platform",
                "org/apache/maven/surefire/junitplatform/RunListenerAdapter");
        install(repo, classes, stock, evidence, "org.eclipse.tycho.surefire.osgibooter", "maven-surefire-common",
                "org/apache/maven/plugin/surefire/report/StatelessXmlReporter");
    }
    private static void install(Path repo, Path classes, Path stock, Path evidence, String bundle, String artifact, String family) throws Exception {
        Path jar = repo.resolve("org/eclipse/tycho/" + bundle + "/5.0.4/" + bundle + "-5.0.4.jar").toRealPath();
        if (!jar.startsWith(repo)) throw new IllegalStateException("Tycho jar escapes isolated repository");
        Map<String,byte[]> expected = new LinkedHashMap<>();
        try (JarFile original = new JarFile(stock.resolve(artifact + "-3.5.6.jar").toFile())) {
            for (JarEntry entry : original.stream().filter(e -> belongs(e.getName(), family)).toList()) {
                expected.put(entry.getName(), original.getInputStream(entry).readAllBytes());
            }
        }
        Map<String,byte[]> replacements = new LinkedHashMap<>();
        try (var files = Files.list(classes.resolve(family).getParent())) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String entry = classes.relativize(file).toString().replace('\\', '/');
                if (belongs(entry, family)) replacements.put(entry, Files.readAllBytes(file));
            }
        }
        String before = sha(jar);
        replaceVerified(jar, expected, replacements);
        Files.writeString(evidence.resolve(bundle + ".properties"), "baselineSHA256=" + before
                + "\npatchedSHA256=" + sha(jar) + "\nreplacedEntries=" + replacements.keySet() + "\n");
        System.out.println("Patched isolated " + bundle + ": " + replacements.keySet());
    }
    private static boolean belongs(String entry, String family) {
        return entry.equals(family + ".class") || (entry.startsWith(family + "$") && entry.endsWith(".class"));
    }
    static void replaceVerified(Path jar, Map<String,byte[]> expected, Map<String,byte[]> replacements) throws Exception {
        if (expected.isEmpty() || !expected.keySet().equals(replacements.keySet())) {
            throw new IllegalStateException("Incomplete replacement class family");
        }
        Path backup = jar.resolveSibling(jar.getFileName() + ".baseline");
        if (Files.exists(backup)) throw new IllegalStateException("Refusing to patch an already modified runtime");
        Map<String,byte[]> contents = new LinkedHashMap<>();
        try (JarFile input = new JarFile(jar.toFile())) {
            for (JarEntry entry : input.stream().toList()) {
                String name = entry.getName();
                String upper = name.toUpperCase(Locale.ROOT);
                if (upper.startsWith("META-INF/") && (upper.endsWith(".SF") || upper.endsWith(".RSA")
                        || upper.endsWith(".DSA") || upper.endsWith(".EC"))) {
                    throw new IllegalStateException("Refusing to modify signed jar " + jar);
                }
                if (contents.put(name, input.getInputStream(entry).readAllBytes()) != null) {
                    throw new IllegalStateException("Duplicate jar entry " + name);
                }
            }
        }
        for (var original : expected.entrySet()) {
            if (!Arrays.equals(contents.get(original.getKey()), original.getValue())) {
                throw new IllegalStateException("Unexpected original bytecode: " + original.getKey());
            }
        }
        Path temporary = Files.createTempFile(jar.getParent(), "reporting-probe-", ".jar");
        try {
            try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(temporary))) {
                for (var entry : contents.entrySet()) {
                    JarEntry copy = new JarEntry(entry.getKey());
                    copy.setTime(0);
                    output.putNextEntry(copy);
                    output.write(replacements.getOrDefault(entry.getKey(), entry.getValue()));
                    output.closeEntry();
                }
            }
            Files.copy(jar, backup);
            Files.move(temporary, jar, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
    private static String sha(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
}
