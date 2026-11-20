/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.qa.cleanup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import javax.tools.ToolProvider;
import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.triggerpattern.test.policy.PinnedGitRepository;
import org.w3c.dom.Element;

class UpstreamCleanupTest {
    private static final String BUNDLE = "org.sandbox.qa.cleanup.corpus";
    private static final Path ROOT = Path.of(System.getProperty("sandbox.cleanup.repo", ".")).toAbsolutePath().normalize();
    private static final Path FIXTURES = ROOT.resolve("qa/upstream-cleanups/src/testFixtures");

    @Test
    void profilesCoverEveryRegisteredSandboxCleanup() throws Exception {
        var actual = new TreeMap<String, String>();
        var xml = DocumentBuilderFactory.newInstance();
        xml.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (var modules = Files.list(ROOT)) {
            for (Path module : modules.filter(p -> p.getFileName().toString().startsWith("sandbox_")).sorted().toList()) {
                Path descriptor = module.resolve("plugin.xml");
                if (!Files.isRegularFile(descriptor)) continue;
                var extensions = xml.newDocumentBuilder().parse(descriptor.toFile()).getElementsByTagName("extension");
                for (int i = 0; i < extensions.getLength(); i++) {
                    var extension = (Element) extensions.item(i);
                    if (!extension.getAttribute("point").equals("org.eclipse.jdt.ui.cleanUps")) continue;
                    var registrations = extension.getElementsByTagName("cleanUp");
                    for (int j = 0; j < registrations.getLength(); j++) {
                        var cleanup = (Element) registrations.item(j);
                        String id = cleanup.getAttribute("id"), implementation = cleanup.getAttribute("class");
                        assertFalse(id.isBlank() || implementation.isBlank(), descriptor.toString());
                        assertEquals(null, actual.put(id, implementation), "Duplicate cleanup ID: " + id);
                    }
                }
            }
        }
        assertEquals(25, actual.size(), "Update the corpus inventory when registrations change");
        Properties expected = properties(FIXTURES.resolve("profiles/registrations.properties"));
        var expectedMap = new TreeMap<String, String>();
        expected.stringPropertyNames().forEach(key -> expectedMap.put(key, expected.getProperty(key)));
        assertEquals(actual, expectedMap, "Every source registration must be exercised by the runtime inventory");
        try (var files = Files.list(FIXTURES.resolve("profiles"))) {
            List<Path> profiles = files.filter(p -> p.toString().endsWith(".properties"))
                    .filter(p -> !p.getFileName().toString().equals("registrations.properties")).toList();
            assertFalse(profiles.isEmpty(), "No cleanup profiles configured");
            for (Path profile : profiles) assertTrue(properties(profile).containsValue("true"), "Disabled profile: " + profile);
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "sandbox.cleanup.product", matches = ".+")
    void actualCorpusHelperResolvesAndInventoriesInstalledCleanups(@TempDir Path temporary) throws Exception {
        Path runtime = temporary.resolve("runtime");
        copyTree(Path.of(required("product")).toRealPath(), runtime);
        compileHelper(runtime, temporary);
        Path checkout = Files.createDirectories(temporary.resolve("checkout"));
        Path corpus = temporary.resolve("inventory");
        int exit = launch(runtime, temporary, checkout, FIXTURES.resolve("profiles"), corpus, "inventory", 3);
        assertEquals(0, exit, Files.readString(temporary.resolve("eclipse.log")));
        Properties receipt = properties(corpus.resolve("receipt.properties"));
        assertEquals("PASS", receipt.getProperty("status"), receipt.toString());
        assertEquals("25", receipt.getProperty("registrations"), "Incomplete installed cleanup registry");
    }

    @Test
    @EnabledIfSystemProperty(named = "sandbox.cleanup.family", matches = "core|ui|swt")
    void pinnedUpstreamSourcesPreserveCompilerDiagnostics() throws Exception {
        String family = required("family");
        String remote = switch (family) {
            case "core", "ui" -> "https://github.com/eclipse-jdt/eclipse.jdt." + family + ".git";
            case "swt" -> "https://github.com/eclipse-platform/eclipse.platform.swt.git";
            default -> throw new IllegalArgumentException(family);
        };
        String commit = switch (family) {
            case "core" -> "6725c16c24d94c83302346dc384bb915a0f2fe1a";
            case "ui" -> "54bd63e6453a7eb5db0bfc1d60f17e9568095619";
            case "swt" -> "3e3b24b4691c4f18a7d5e22653fe28d4c47385d7";
            default -> throw new IllegalArgumentException(family);
        };
        String productCommit = required("productCommit");
        try (var repository = new FileRepositoryBuilder().findGitDir(ROOT.toFile()).build()) {
            assertEquals(repository.resolve(Constants.HEAD).name(), productCommit, "Product must come from this source commit");
        }
        Path output = Path.of(required("output")).toAbsolutePath().normalize();
        Path runtime = output.resolve("runtime");
        assertFalse(Files.exists(output), "Use a fresh evidence directory: " + output);
        Files.createDirectories(output);
        Files.writeString(output.resolve("source-identity.txt"), remote + "\nrefs/tags/R4_41\n" + commit + "\n");
        Files.writeString(output.resolve("product-commit.txt"), productCommit + "\n");
        copyTree(Path.of(required("product")).toRealPath(), runtime);
        provisionUpstreamTestSupport(runtime, output);
        copyTree(FIXTURES, output.resolve("helper-inputs"));
        compileHelper(runtime, output);
        Path checkout = output.resolve("upstream");
        try (var source = PinnedGitRepository.cloneAt(checkout, URI.create(remote), "refs/tags/R4_41", commit)) {
            assertEquals(commit, source.headCommit());
            copyTree(checkout, output.resolve("original-checkout"));
            try {
                int exit = launch(runtime, output, checkout, output.resolve("helper-inputs/profiles"),
                        output.resolve("corpus"), family, 330);
                assertEquals(0, exit, "Cleanup process failed; see " + output.resolve("eclipse.log"));
                Properties receipt = properties(output.resolve("corpus/receipt.properties"));
                assertEquals("PASS", receipt.getProperty("status"), receipt.toString());
                assertTrue(Integer.parseInt(receipt.getProperty("source.units", "0")) > 0, "No source units checked");
                assertEquals("25", receipt.getProperty("registrations"), "Incomplete runtime cleanup inventory");
            } finally {
                // Preserve the last actual files, including partial failures, before the JGit fixture deletes them.
                copyTree(checkout, output.resolve("last-checkout"));
            }
        }
    }

    private static void provisionUpstreamTestSupport(Path runtime, Path output) throws Exception {
        // The shipped SDK does not contain the upstream projects' test-only bundles.
        // Resolve their real transitive dependencies from the same fixed Eclipse release.
        String repository = "https://download.eclipse.org/eclipse/updates/4.41/R-4.41-202608281142/";
        String units = String.join(",",
                "org.eclipse.test.feature.group/3.9.800.v20260619-0701",
                "org.eclipse.text.tests/3.15.0.v20260604-1153",
                "org.eclipse.jface.text.tests/3.14.200.v20260814-0956",
                "org.eclipse.core.filebuffers.tests/3.14.100.v20260421-1151",
                "org.eclipse.unittest.ui/1.2.200.v20260603-0511");
        String profile = properties(runtime.resolve("configuration/config.ini")).getProperty("eclipse.p2.profile");
        assertNotNull(profile, "The actual product must have a p2 profile");
        List<String> command = List.of(runtime.resolve("eclipse").toString(), "--launcher.suppressErrors",
                "-nosplash", "-consoleLog", "-vm", Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-data", output.resolve("test-support-workspace").toString(),
                "-application", "org.eclipse.equinox.p2.director", "-repository", repository,
                "-installIU", units, "-destination", runtime.toString(), "-bundlepool", runtime.toString(),
                "-profile", profile, "-roaming", "-vmargs", "-Xmx1g",
                "-Declipse.p2.mirrors=false");
        Files.write(output.resolve("test-support-command.txt"), command);
        Process process = new ProcessBuilder(command).directory(runtime.toFile()).redirectErrorStream(true)
                .redirectOutput(output.resolve("test-support.log").toFile()).start();
        try {
            assertTrue(process.waitFor(20, TimeUnit.MINUTES), "Upstream test support provisioning timed out");
            Files.writeString(output.resolve("test-support-exit-code.txt"), Integer.toString(process.exitValue()));
            assertEquals(0, process.exitValue(), "Upstream test support could not be provisioned; see test-support.log");
        } finally {
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
            }
        }
    }

    private static int launch(Path runtime, Path output, Path checkout, Path profiles, Path corpus,
            String family, long timeoutMinutes) throws Exception {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        List<String> command = List.of(runtime.resolve("eclipse").toString(), "--launcher.suppressErrors", "-nosplash", "-clean", "-consoleLog",
                "-vm", java.toString(), "-data", output.resolve("workspace").toString(), "-application", BUNDLE + ".run",
                checkout.toString(), profiles.toString(), corpus.toString(), family, "-vmargs", "-Xmx4g");
        Files.write(output.resolve("eclipse-command.txt"), command);
        Process process = new ProcessBuilder(command).directory(runtime.toFile()).redirectErrorStream(true)
                .redirectOutput(output.resolve("eclipse.log").toFile()).start();
        try {
            assertTrue(process.waitFor(timeoutMinutes, TimeUnit.MINUTES), "Cleanup process timed out; see " + output);
            int exit = process.exitValue();
            Files.writeString(output.resolve("exit-code.txt"), Integer.toString(exit));
            return exit;
        } finally {
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
            }
        }
    }

    private static void compileHelper(Path runtime, Path output) throws Exception {
        Path classes = output.resolve("helper-classes");
        Files.createDirectories(classes);
        String classpath;
        try (var files = Files.walk(runtime.resolve("plugins"))) {
            classpath = files.filter(p -> p.toString().endsWith(".jar")).sorted().map(Path::toString)
                    .collect(Collectors.joining(File.pathSeparator));
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "A Java 25 JDK is required");
        int compilation;
        try (var log = Files.newOutputStream(output.resolve("helper-compile.log"))) {
            compilation = compiler.run(null, log, log, "--release", "25", "-encoding", "UTF-8", "-proc:none",
                    "-classpath", classpath, "-d", classes.toString(), FIXTURES.resolve("CorpusApplication.java").toString(),
                    ROOT.resolve("sandbox_test_commons/src/org/sandbox/jdt/ui/tests/quickfix/rules/CompilerDiagnosticAssertions.java").toString());
        }
        assertEquals(0, compilation, Files.readString(output.resolve("helper-compile.log")));
        Path archive = runtime.resolve("plugins/" + BUNDLE + "_1.0.0.jar");
        try (var manifest = Files.newInputStream(FIXTURES.resolve("MANIFEST.MF"));
                var jar = new JarOutputStream(Files.newOutputStream(archive), new Manifest(manifest))) {
            addEntry(jar, "fragment.xml", FIXTURES.resolve("fragment.xml"));
            try (var files = Files.walk(classes)) {
                for (Path file : files.filter(Files::isRegularFile).sorted().toList())
                    addEntry(jar, classes.relativize(file).toString().replace('\\', '/'), file);
            }
        }
        Files.copy(archive, output.resolve(BUNDLE + "_1.0.0.jar"));
        Files.writeString(runtime.resolve("configuration/org.eclipse.equinox.simpleconfigurator/bundles.info"),
                "\n" + BUNDLE + ",1.0.0,plugins/" + BUNDLE + "_1.0.0.jar,4,false\n", StandardOpenOption.APPEND);
    }

    private static void addEntry(JarOutputStream jar, String name, Path file) throws IOException {
        jar.putNextEntry(new JarEntry(name));
        Files.copy(file, jar);
        jar.closeEntry();
    }

    private static void copyTree(Path source, Path destination) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                if (directory.getFileName().toString().equals(".git")) return FileVisitResult.SKIP_SUBTREE;
                Files.createDirectories(destination.resolve(source.relativize(directory)));
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.copy(file, destination.resolve(source.relativize(file)), StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static Properties properties(Path path) throws IOException {
        var result = new Properties();
        try (var input = Files.newInputStream(path)) { result.load(input); }
        return result;
    }

    private static String required(String suffix) {
        String value = System.getProperty("sandbox.cleanup." + suffix);
        assertNotNull(value, "Missing sandbox.cleanup." + suffix);
        assertFalse(value.isBlank(), "Empty sandbox.cleanup." + suffix);
        return value;
    }
}
