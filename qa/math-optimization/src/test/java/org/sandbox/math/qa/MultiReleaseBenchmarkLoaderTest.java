/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MultiReleaseBenchmarkLoaderTest {
    @TempDir Path temporary;

    @Test void runtimeDependenciesMatchNormalLoadingWhileCompiledOverridesWin() throws Exception {
        Path archive = temporary.resolve("multi-release.jar"), classes = Files.createDirectory(temporary.resolve("override"));
        var manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Multi-Release", "true");
        String entry = "org/bouncycastle/Probe.class";
        try (var jar = new JarOutputStream(Files.newOutputStream(archive), manifest)) {
            for (String variant : new String[] {"base", "runtime", "future"}) {
                String prefix = switch (variant) {
                    case "runtime" -> "META-INF/versions/9/";
                    case "future" -> "META-INF/versions/" + (Runtime.version().feature() + 1) + "/";
                    default -> "";
                };
                jar.putNextEntry(new JarEntry(prefix + entry)); jar.write(compile(variant)); jar.closeEntry();
            }
        }
        try (var normal = new URLClassLoader(new java.net.URL[] {archive.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
                var measured = new BouncyCastleHashToCurveBenchmark.VariantLoader(classes, archive)) {
            assertEquals("runtime", value(normal)); assertEquals(value(normal), value(measured));
        }
        Path override = classes.resolve(entry); Files.createDirectories(override.getParent()); Files.write(override, compile("compiled"));
        try (var measured = new BouncyCastleHashToCurveBenchmark.VariantLoader(classes, archive)) {
            assertEquals("compiled", value(measured));
        }
    }
    private byte[] compile(String value) throws Exception {
        Path directory = Files.createDirectory(temporary.resolve(value)), source = directory.resolve("Probe.java");
        Files.writeString(source, "package org.bouncycastle; public class Probe { public static String value() { return \"" + value + "\"; } }");
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "9", "-proc:none", "-d", directory.toString(), source.toString()));
        return Files.readAllBytes(directory.resolve("org/bouncycastle/Probe.class"));
    }
    private Object value(ClassLoader loader) throws Exception {
        return loader.loadClass("org.bouncycastle.Probe").getMethod("value").invoke(null);
    }
}
