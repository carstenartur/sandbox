/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.qa.reporting;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TychoProbeInstallerTest {
    @TempDir Path temp;
    @Test void replacesOnlyVerifiedClassAndKeepsResources() throws Exception {
        Path jar = jar("runtime.jar", Map.of("a/A.class", "original", "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n", "about.html", "license"));
        byte[] before = Files.readAllBytes(jar);
        TychoProbeInstaller.replaceVerified(jar, Map.of("a/A.class", bytes("original")), Map.of("a/A.class", bytes("patched")));
        assertArrayEquals(before, Files.readAllBytes(temp.resolve("runtime.jar.baseline")));
        try (JarFile patched = new JarFile(jar.toFile())) {
            assertEquals("patched", new String(patched.getInputStream(patched.getJarEntry("a/A.class")).readAllBytes(), StandardCharsets.UTF_8));
            assertEquals("license", new String(patched.getInputStream(patched.getJarEntry("about.html")).readAllBytes(), StandardCharsets.UTF_8));
            assertEquals(3, patched.size());
        }
    }
    @Test void mismatchingOriginalIsRejectedWithoutAnyWrite() throws Exception {
        Path jar = jar("mismatch.jar", Map.of("a/A.class", "unexpected"));
        byte[] before = Files.readAllBytes(jar);
        assertThrows(IllegalStateException.class, () -> TychoProbeInstaller.replaceVerified(jar,
                Map.of("a/A.class", bytes("original")), Map.of("a/A.class", bytes("patched"))));
        assertArrayEquals(before, Files.readAllBytes(jar));
        assertFalse(Files.exists(temp.resolve("mismatch.jar.baseline")));
    }
    @Test void signedJarIsNeverSilentlyResignedOrStripped() throws Exception {
        Path jar = jar("signed.jar", Map.of("a/A.class", "original", "META-INF/TEST.SF", "signature"));
        byte[] before = Files.readAllBytes(jar);
        assertThrows(IllegalStateException.class, () -> TychoProbeInstaller.replaceVerified(jar,
                Map.of("a/A.class", bytes("original")), Map.of("a/A.class", bytes("patched"))));
        assertArrayEquals(before, Files.readAllBytes(jar));
    }
    @Test void replacementFamilyMustBeComplete() throws Exception {
        Path jar = jar("missing.jar", Map.of("a/A.class", "original"));
        assertThrows(IllegalStateException.class, () -> TychoProbeInstaller.replaceVerified(jar,
                Map.of("a/A.class", bytes("original")), Map.of()));
        assertFalse(Files.exists(temp.resolve("missing.jar.baseline")));
    }
    @Test void ledgerInstallationPreservesStockAdapterAndExistingListeners() throws Exception {
        Path repo = Files.createDirectory(temp.resolve("isolated"));
        Files.writeString(repo.resolve(".sandbox-reporting-investigation"), "bb86061a7a8d0c3ee4d02f358cf30be6e3e6ae4e\n");
        String bundle = "org.eclipse.tycho.surefire.junit5";
        Path dest = Files.createDirectories(repo.resolve("org/eclipse/tycho/" + bundle + "/5.0.4"));
        String adapter = "org/apache/maven/surefire/junitplatform/RunListenerAdapter.class";
        String service = "META-INF/services/org.junit.platform.launcher.TestExecutionListener";
        Path runtime = jar("ledger-runtime.jar", Map.of(adapter, "original", service, "example.ExistingListener\n"));
        java.nio.file.Files.move(runtime, dest.resolve(bundle + "-5.0.4.jar"));
        Path stock = Files.createDirectory(temp.resolve("stock"));
        Files.move(jar("stock.jar", Map.of(adapter, "original")), stock.resolve("surefire-junit-platform-3.5.6.jar"));
        Path classes = Files.createDirectory(temp.resolve("classes"));
        String ledger = "org/sandbox/qa/reporting/ExecutionLedger.class";
        Files.createDirectories(classes.resolve(ledger).getParent());
        Files.writeString(classes.resolve(ledger), "ledger-bytecode");
        TychoProbeInstaller.main(new String[]{repo.toString(), classes.toString(), stock.toString(), temp.resolve("evidence").toString(), "LEDGER"});
        Path installed = dest.resolve(bundle + "-5.0.4.jar");
        try (JarFile result = new JarFile(installed.toFile())) {
            assertEquals("original", new String(result.getInputStream(result.getJarEntry(adapter)).readAllBytes(), StandardCharsets.UTF_8));
            assertEquals("example.ExistingListener\norg.sandbox.qa.reporting.ExecutionLedger\n",
                    new String(result.getInputStream(result.getJarEntry(service)).readAllBytes(), StandardCharsets.UTF_8));
            assertNotNull(result.getJarEntry(ledger));
        }
        assertTrue(Files.isRegularFile(installed.resolveSibling(installed.getFileName() + ".unmonitored")));
        assertFalse(Files.exists(installed.resolveSibling(installed.getFileName() + ".baseline")), "Reporter patch must retain its separate baseline slot");
        byte[] installedBytes = Files.readAllBytes(installed);
        assertThrows(IllegalStateException.class, () -> TychoProbeInstaller.main(new String[]{repo.toString(), classes.toString(), stock.toString(), temp.resolve("evidence").toString(), "LEDGER"}));
        assertArrayEquals(installedBytes, Files.readAllBytes(installed));
    }
    private Path jar(String name, Map<String,String> entries) throws Exception {
        Path file = temp.resolve(name);
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(file))) {
            for (var e : entries.entrySet()) {
                out.putNextEntry(new JarEntry(e.getKey())); out.write(bytes(e.getValue())); out.closeEntry();
            }
        }
        return file;
    }
    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
}
