/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.qa.css;

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
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import javax.tools.ToolProvider;

import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.sandbox.jdt.triggerpattern.test.policy.PinnedGitRepository;

class PinnedUpstreamCssTest {
    private static final String BUNDLE = "org.sandbox.qa.css.corpus";
    private static final Path ROOT = Path.of(required("repo")).toAbsolutePath().normalize();
    private static final Path FIXTURES = ROOT.resolve("qa/upstream-css-cleanups/src/testFixtures");

    @ParameterizedTest(name = "{0}: every CSS file preserves structure and diagnostics")
    @CsvSource({
        "core,https://github.com/eclipse-jdt/eclipse.jdt.core.git,6725c16c24d94c83302346dc384bb915a0f2fe1a,2,0",
        "ui,https://github.com/eclipse-jdt/eclipse.jdt.ui.git,54bd63e6453a7eb5db0bfc1d60f17e9568095619,3,0",
        "swt,https://github.com/eclipse-platform/eclipse.platform.swt.git,3e3b24b4691c4f18a7d5e22653fe28d4c47385d7,8,1"
    })
    void everyPinnedCssFilePreservesStructureAndDiagnostics(String family, String remote,
            String commit, int expectedFiles, int expectedRejections, @TempDir Path temporary) throws Exception {
        Path output = Path.of(required("output")).toAbsolutePath().resolve(family);
        assertFalse(Files.exists(output), "Use a fresh evidence directory: " + output);
        Files.createDirectories(output);
        String productCommit = required("productCommit");
        try (var repository = new FileRepositoryBuilder().findGitDir(ROOT.toFile()).build()) {
            var productTree = repository.resolve(productCommit + ":sandbox_css_cleanup");
            assertNotNull(productTree, "The product source commit must be available locally");
            assertEquals(repository.resolve("HEAD:sandbox_css_cleanup"), productTree,
                    "Rebuild the product: CSS production sources differ from the supplied product commit");
            Files.writeString(output.resolve("source-identity.txt"), "repository=" + remote
                    + "\nref=refs/tags/R4_41\ncommit=" + commit + "\nproductCommit=" + productCommit
                    + "\nharnessCommit=" + repository.resolve("HEAD").name() + "\n");
        }
        Path runtime = temporary.resolve("runtime");
        copyTree(Path.of(required("product")).toRealPath(), runtime);
        try (var files = Files.list(runtime.resolve("plugins"))) {
            Path cssBundle = files.filter(p -> p.getFileName().toString().startsWith("sandbox_css_cleanup_"))
                    .filter(p -> p.toString().endsWith(".jar")).findFirst().orElseThrow();
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(cssBundle)));
            Files.writeString(output.resolve("css-bundle-sha256.txt"), digest + "  " + cssBundle.getFileName() + "\n");
        }
        copyTree(FIXTURES, output.resolve("helper-inputs"));
        Files.copy(Path.of(required("node.modulesDirectory")).getParent().resolve("package-lock.json"), output.resolve("package-lock.json"));
        Files.copy(ROOT.resolve("qa/upstream-css-cleanups/src/test/java/org/sandbox/qa/css/CssDiagnosticIdentity.java"),
                output.resolve("helper-inputs/CssDiagnosticIdentity.java"));
        compileHelper(runtime, output);
        URI fetch = fetchUri(family, remote, commit);
        Files.writeString(output.resolve("fetch-uri.txt"), fetch + "\n");
        try (var source = PinnedGitRepository.cloneAt(temporary.resolve("upstream"), fetch, "refs/tags/R4_41", commit)) {
            assertEquals(commit, source.headCommit());
            int exit = launch(runtime, output, source.directory(), family);
            assertEquals(0, exit, "Actual CSS corpus execution failed; see " + output.resolve("eclipse.log"));
            var receipt = new Properties();
            try (var reader = Files.newBufferedReader(output.resolve("corpus/receipt.properties"))) { receipt.load(reader); }
            assertEquals(expectedRejections == 0 ? "PASS" : "PASS_WITH_REJECTIONS", receipt.getProperty("status"), receipt.toString());
            assertEquals(Integer.toString(expectedFiles), receipt.getProperty("files"), "Incomplete source inventory");
            assertEquals(Integer.toString(expectedRejections), receipt.getProperty("rejectedFiles"), "Unexpected rejected source");
            assertEquals(Integer.toString(expectedFiles - expectedRejections), receipt.getProperty("formattedFiles"));
            assertEquals("0", receipt.getProperty("failedFiles"));
            assertEquals("3.6.2", receipt.getProperty("prettier"));
            assertEquals("17.14.1", receipt.getProperty("stylelint"));
        }
    }

    private static URI fetchUri(String family, String remote, String commit) throws Exception {
        String mirrors = System.getProperty("sandbox.css.mirrors");
        if (mirrors == null || mirrors.isBlank()) return URI.create(remote);
        Path mirror = Path.of(mirrors).resolve(family).toRealPath();
        try (var repository = new FileRepositoryBuilder().findGitDir(mirror.toFile()).build()) {
            assertEquals(remote, repository.getConfig().getString("remote", "origin", "url"), "Mirror repository identity differs");
            var tag = repository.resolve("refs/tags/R4_41^{commit}");
            assertNotNull(tag, "Mirror does not advertise the pinned tag");
            assertEquals(commit, tag.name(), "Mirror tag does not match the upstream pin");
        }
        return mirror.toUri();
    }

    private static int launch(Path runtime, Path output, Path checkout, String family) throws Exception {
        List<String> command = List.of(runtime.resolve("eclipse").toString(), "--launcher.suppressErrors", "-nosplash", "-clean", "-consoleLog",
                "-vm", Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-data", output.resolve("workspace").toString(), "-application", BUNDLE + ".run",
                checkout.toString(), output.resolve("corpus").toString(), family, output.resolve("helper-inputs").toString(),
                "-vmargs", "-Xmx512m", "-XX:-UsePerfData", "-Dsandbox.css.node.installDirectory=" + required("node.installDirectory"),
                "-Dsandbox.css.node.modulesDirectory=" + required("node.modulesDirectory"));
        Files.write(output.resolve("eclipse-command.txt"), command);
        Process process = new ProcessBuilder(command).directory(runtime.toFile()).redirectErrorStream(true)
                .redirectOutput(output.resolve("eclipse.log").toFile()).start();
        try {
            assertTrue(process.waitFor(10, TimeUnit.MINUTES), "CSS corpus execution timed out");
            Files.writeString(output.resolve("exit-code.txt"), Integer.toString(process.exitValue()));
            return process.exitValue();
        } finally {
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        }
    }

    private static void compileHelper(Path runtime, Path output) throws Exception {
        Path classes = output.resolve("helper-classes");
        Files.createDirectories(classes);
        String classpath;
        try (var files = Files.walk(runtime.resolve("plugins"))) {
            classpath = files.filter(p -> p.toString().endsWith(".jar")).sorted().map(Path::toString).collect(Collectors.joining(File.pathSeparator));
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Java 25 JDK required");
        int compilation;
        try (var log = Files.newOutputStream(output.resolve("helper-compile.log"))) {
            compilation = compiler.run(null, log, log, "--release", "25", "-encoding", "UTF-8", "-proc:none", "-classpath", classpath,
                    "-d", classes.toString(), output.resolve("helper-inputs/CssCorpusApplication.java").toString(),
                    output.resolve("helper-inputs/CssDiagnosticIdentity.java").toString());
        }
        assertEquals(0, compilation, Files.readString(output.resolve("helper-compile.log")));
        Path archive = runtime.resolve("plugins/" + BUNDLE + "_1.0.0.jar");
        try (var input = Files.newInputStream(output.resolve("helper-inputs/MANIFEST.MF"));
                var jar = new JarOutputStream(Files.newOutputStream(archive), new Manifest(input))) {
            addEntry(jar, "fragment.xml", output.resolve("helper-inputs/fragment.xml"));
            try (var files = Files.walk(classes)) {
                for (Path file : files.filter(Files::isRegularFile).sorted().toList()) addEntry(jar, classes.relativize(file).toString().replace('\\', '/'), file);
            }
        }
        Files.copy(archive, output.resolve(BUNDLE + "_1.0.0.jar"));
        Files.writeString(runtime.resolve("configuration/org.eclipse.equinox.simpleconfigurator/bundles.info"),
                "\n" + BUNDLE + ",1.0.0,plugins/" + BUNDLE + "_1.0.0.jar,4,false\n", StandardOpenOption.APPEND);
    }

    private static void addEntry(JarOutputStream jar, String name, Path source) throws IOException {
        jar.putNextEntry(new JarEntry(name));
        Files.copy(source, jar);
        jar.closeEntry();
    }

    private static void copyTree(Path source, Path destination) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                Files.createDirectories(destination.resolve(source.relativize(directory)));
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.copy(file, destination.resolve(source.relativize(file)), StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static String required(String suffix) {
        String value = System.getProperty("sandbox.css." + suffix);
        assertNotNull(value, "Missing sandbox.css." + suffix);
        assertFalse(value.isBlank(), "Empty sandbox.css." + suffix);
        return value;
    }
}
