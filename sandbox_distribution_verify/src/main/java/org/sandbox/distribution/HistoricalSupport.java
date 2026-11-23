/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.distribution;

import static org.sandbox.distribution.AggregateInstallationEvidence.require;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import javax.tools.ToolProvider;

/** Reconstructed implementation at 49e12331b056bdf8ae9c7a07d9dce9e0245ba1b1, never published. */
final class HistoricalSupport {
    static final String VERSION = "1.3.11.SNAPSHOT";
    static final String FEATURE_VERSION = "1.3.10.supportFixture";
    private static final String SERVICE = "org.junit.platform.launcher.PostDiscoveryFilter";
    private static final Map<String, String> BLOBS = Map.of(
            "ExternalResourceExtension.java", "9d3a4a0a0231eee875b283e7a68ee6c3411ef53e",
            "IgnoreInheritedTests.java", "4f3c462ba89a908541f9022abbe46e940d6655e5",
            "InheritedTestsFilter.java", "fbffe3de3f29fc58a75ee12bce55e8bf27861a21",
            SERVICE, "d17ff5659e835a9928128d48bfb8d326623388e1");

    static Path build(Path source, Path work, String classpath) throws Exception {
        Path classes = Files.createDirectories(work.resolve("historical-classes"));
        List<String> args = new ArrayList<>(List.of("--release", "21", "--source-path", "", "-classpath", classpath, "-d", classes.toString()));
        for (var entry : BLOBS.entrySet()) {
            String name = entry.getKey();
            try (var in = HistoricalSupport.class.getResourceAsStream("/historical-support/" + name)) {
                require(in != null, "Missing historical fixture " + name);
                byte[] bytes = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8);
                var sha = MessageDigest.getInstance("SHA-1"); // Git blob object ID, not a signature
                sha.update(("blob " + bytes.length + '\0').getBytes(StandardCharsets.US_ASCII));
                require(HexFormat.of().formatHex(sha.digest(bytes)).equals(entry.getValue()), "Historical source changed: " + name);
                Path dest = SERVICE.equals(name) ? classes.resolve("META-INF/services").resolve(name) : work.resolve(name);
                AggregateInstallationEvidence.createParentDirectories(dest);
                Files.write(dest, bytes);
                if (!SERVICE.equals(name)) args.add(dest.toString());
            }
        }
        require(ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)) == 0, "Historical support compilation failed");
        Manifest manifest = new Manifest();
        var headers = manifest.getMainAttributes();
        headers.putValue("Manifest-Version", "1.0");
        headers.putValue("Bundle-ManifestVersion", "2");
        headers.putValue("Bundle-SymbolicName", SupportArtifacts.BSN);
        headers.putValue("Bundle-Version", VERSION);
        headers.putValue("Bundle-RequiredExecutionEnvironment", "JavaSE-21");
        headers.putValue("Export-Package", "org.sandbox.junit.jupiter;version=\"1.3.11\";uses:=\"org.junit.jupiter.api.extension,"
                + "org.junit.jupiter.api.function,org.junit.platform.engine,org.junit.platform.launcher\"");
        headers.putValue("Import-Package", "org.junit.jupiter.api.extension;version=\"[5.14,7)\",org.junit.jupiter.api.function;version=\"[5.14,7)\","
                + "org.junit.platform.engine;version=\"[1.14,7)\",org.junit.platform.engine.support.descriptor;version=\"[1.14,7)\",org.junit.platform.launcher;version=\"[1.14,7)\"");
        Path jar = Files.createDirectories(source.resolve("plugins")).resolve(SupportArtifacts.BSN + '_' + VERSION + ".jar");
        try (var out = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            AggregateInstallationVerifier.writeClasses(classes, out);
        }
        Path feature = Files.createDirectories(source.resolve("features").resolve(SupportArtifacts.COMPONENT + '_' + FEATURE_VERSION));
        Files.writeString(feature.resolve("feature.xml"), "<feature id=\"" + SupportArtifacts.COMPONENT
                + "\" label=\"Historical support fixture\" version=\"" + FEATURE_VERSION + "\">\n  <plugin id=\"" + SupportArtifacts.BSN
                + "\" version=\"" + VERSION + "\" unpack=\"false\"/>\n</feature>\n");
        return jar;
    }

    private HistoricalSupport() {}
}
