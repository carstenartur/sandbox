/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.qa.cleanup;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
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
    // Each row specifies a profile stem and the cleanup settings that define its coverage.
    private static final String PROFILE_CONTRACT = """
            all-java-preserve explicit_encoding_keep_behavior=true loop_conversion.enabled=true loop_conversion.target_format=stream loop_conversion.with_mathematics=false mathematics.safetyProfile=PRESERVE_JAVA junitcleanup_best_effort=false junitcleanup_6_compatibility=false
            container-all container_contracts=true
            encoding-aggregate_to_utf8 explicit_encoding=true explicit_encoding_aggregate_to_utf8=true explicit_encoding_insert_utf8=false explicit_encoding_keep_behavior=false
            encoding-insert_utf8 explicit_encoding=true explicit_encoding_aggregate_to_utf8=false explicit_encoding_insert_utf8=true explicit_encoding_keep_behavior=false
            encoding-keep_behavior explicit_encoding=true explicit_encoding_aggregate_to_utf8=false explicit_encoding_insert_utf8=false explicit_encoding_keep_behavior=true
            general-type use_general_type=true
            hintfile-all hintfile=true
            int-to-enum int_to_enum=true int_to_enum.project_wide=true
            jface jfacecleanup=true
            junit-best-effort junitcleanup=true junitcleanup_4_externalresource=true junitcleanup_4_ruleexternalresource=true junitcleanup_best_effort=true junitcleanup_6_compatibility=false
            junit-strict junitcleanup=true junitcleanup_4_externalresource=true junitcleanup_4_ruleexternalresource=true junitcleanup_best_effort=false junitcleanup_6_compatibility=false
            junit6-compatibility junitcleanup=true junitcleanup_4_externalresource=true junitcleanup_4_ruleexternalresource=true junitcleanup_best_effort=false junitcleanup_6_compatibility=true
            loop-enhanced_for loop_conversion.enabled=true loop_conversion.target_format=enhanced_for loop_conversion.with_mathematics=false
            loop-iterator_while loop_conversion.enabled=true loop_conversion.target_format=iterator_while loop_conversion.with_mathematics=false
            loop-stream loop_conversion.enabled=true loop_conversion.target_format=stream loop_conversion.with_mathematics=false
            math-checked mathematics=true mathematics.safetyProfile=CHECKED_THROW mathematics.checkedOptIn=true mathematics.mathematicalOptIn=false
            math-guarded mathematics=true mathematics.safetyProfile=GUARDED_FALLBACK mathematics.checkedOptIn=false mathematics.mathematicalOptIn=false
            math-mathematical-opt-in mathematics=true mathematics.safetyProfile=PRESERVE_JAVA mathematics.checkedOptIn=false mathematics.mathematicalOptIn=true
            math-preserve mathematics=true mathematics.safetyProfile=PRESERVE_JAVA mathematics.checkedOptIn=false mathematics.mathematicalOptIn=false
            math-with-loop mathematics=true mathematics.safetyProfile=PRESERVE_JAVA mathematics.checkedOptIn=false mathematics.mathematicalOptIn=false loop_conversion.enabled=true loop_conversion.target_format=stream loop_conversion.with_mathematics=true
            method-reuse method_reuse=true
            pattern-missing_hashcode missing_hashcode=true
            pattern-obsolete_collection obsolete_collection=true
            pattern-overridable_in_constructor overridable_in_constructor=true
            pattern-print_stacktrace print_stacktrace=true
            pattern-shift_out_of_range shift_out_of_range=true
            pattern-string_simplification string_simplification=true
            pattern-system_out system_out=true
            pattern-threading threading=true
            pattern-wrong_string_comparison wrong_string_comparison=true
            platform-status simplify_status_creation=true
            """;

    @Test
    void rejectsMissingProfilesAndChangedModes(@TempDir Path temporary) throws Exception {
        Path profiles = temporary.resolve("profiles");
        copyTree(FIXTURES.resolve("profiles"), profiles);
        Path loop = profiles.resolve("loop-iterator_while.properties");
        Path junit = profiles.resolve("junit-best-effort.properties");
        Files.delete(loop);
        assertAll(
                () -> assertThrows(AssertionError.class, () -> verifyProfiles(profiles)),
                () -> {
                    Files.copy(FIXTURES.resolve("profiles").resolve(loop.getFileName()), loop);
                    Files.writeString(junit, "cleanup.unrecognized=true\n", StandardCharsets.UTF_8);
                    assertThrows(AssertionError.class, () -> verifyProfiles(profiles));
                });
    }

    @Test
    void rejectsUnverifiedProductBytes(@TempDir Path artifact) throws Exception {
        Path commit = artifact.resolve("source-commit.txt");
        Path archive = artifact.resolve("product.tar.gz");
        Files.writeString(commit, "source\n", StandardCharsets.UTF_8);
        Files.writeString(archive, "archive\n", StandardCharsets.UTF_8);
        // Known SHA-256 values for the two exact UTF-8 payloads above.
        Files.writeString(artifact.resolve("SHA256SUMS"), """
                b8bb034f9b63bd0254fbc7c157cae746c75853f4643d6cea844dc48ddb57f522  source-commit.txt
                371e16ce98051a3ea7af3eaef8b87d69033154fb5bb33da349d611f0fae061d6  product.tar.gz
                """, StandardCharsets.UTF_8);
        assertEquals(archive, verifyProductArtifact(artifact, "source"));
        Files.writeString(archive, "other archive\n", StandardCharsets.UTF_8);
        assertThrows(AssertionError.class, () -> verifyProductArtifact(artifact, "source"));
    }

    @Test
    void rejectsMissingOrExtraExecutionsInPassingReceipt(@TempDir Path corpus) throws Exception {
        Properties receipt = new Properties();
        receipt.setProperty("project.names", "sample.project");
        receipt.setProperty("profile.names", "first,second");
        receipt.setProperty("projects", "1");
        receipt.setProperty("profiles", "2");
        receipt.setProperty("executions", "2");
        receipt.setProperty("failures", "0");
        for (String profile : List.of("first", "second")) {
            Files.writeString(Files.createDirectories(corpus.resolve("sample.project/" + profile))
                    .resolve("result.txt"), "PASS\n", StandardCharsets.UTF_8);
        }
        Set<String> profiles = Set.of("first", "second");
        verifyCoverage(corpus, receipt, profiles, 1);
        Files.move(corpus.resolve("sample.project/first"), corpus.resolve("sample.project/unexpected"));
        assertThrows(AssertionError.class, () -> verifyCoverage(corpus, receipt, profiles, 1));
        Files.move(corpus.resolve("sample.project/unexpected"), corpus.resolve("sample.project/first"));
        Files.writeString(corpus.resolve("sample.project/first/result.txt"), "FAIL\n", StandardCharsets.UTF_8);
        assertThrows(AssertionError.class, () -> verifyCoverage(corpus, receipt, profiles, 1));
    }

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
        verifyProfiles(FIXTURES.resolve("profiles"));
    }

    private static Set<String> verifyProfiles(Path directory) throws IOException {
        List<String[]> contract = PROFILE_CONTRACT.lines().map(row -> row.split(" ")).toList();
        Set<String> expected = contract.stream().map(entries -> entries[0]).collect(Collectors.toCollection(TreeSet::new));
        assertEquals(31, contract.size(), "The corpus must keep all 31 explicit profiles");
        assertEquals(31, expected.size(), "Duplicate corpus profile in contract");
        try (var files = Files.list(directory)) {
            var actual = files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".properties") && !name.equals("registrations.properties"))
                    .map(name -> name.substring(0, name.length() - ".properties".length())).collect(Collectors.toSet());
            assertEquals(expected, actual, "Unexpected cleanup profile inventory in " + directory);
        }
        for (String[] entries : contract) {
            Properties profile = properties(directory.resolve(entries[0] + ".properties"));
            for (int i = 1; i < entries.length; i++) {
                String[] setting = entries[i].split("=", 2);
                assertEquals(setting[1], profile.getProperty("cleanup." + setting[0]), entries[0] + ": " + setting[0]);
            }
            if ("true".equals(profile.getProperty("cleanup.loop_conversion.enabled"))) {
                for (String source : List.of("classic_for", "enhanced_for", "iterator_while", "stream"))
                    assertEquals("true", profile.getProperty("cleanup.loop_conversion.from." + source), entries[0] + ": " + source);
            }
        }
        return expected;
    }

    private static Path verifyProductArtifact(Path artifact, String head) throws Exception {
        Path checksums = artifact.resolve("SHA256SUMS");
        assertTrue(Files.isRegularFile(checksums), "Missing product build checksums: " + checksums);
        var hashes = new TreeMap<String, String>();
        for (String line : Files.readAllLines(checksums, StandardCharsets.UTF_8)) {
            String[] entry = line.split("  ", 2);
            assertTrue(entry.length == 2 && entry[0].matches("[0-9a-f]{64}"), "Invalid SHA256SUMS entry: " + line);
            assertEquals(null, hashes.put(entry[1], entry[0]), "Duplicate SHA256SUMS entry: " + entry[1]);
        }
        for (String file : List.of("source-commit.txt", "product.tar.gz")) {
            Path path = artifact.resolve(file);
            assertTrue(Files.isRegularFile(path), "Missing product build file: " + path);
            assertNotNull(hashes.get(file), "Missing checksum for " + path);
            var digest = MessageDigest.getInstance("SHA-256");
            try (var input = new DigestInputStream(Files.newInputStream(path), digest)) {
                input.transferTo(OutputStream.nullOutputStream());
            }
            assertEquals(hashes.get(file), HexFormat.of().formatHex(digest.digest()), "Product checksum mismatch: " + path);
        }
        assertEquals(head, Files.readString(artifact.resolve("source-commit.txt"), StandardCharsets.UTF_8).strip(),
                "Build the product artifact from this source checkout");
        return artifact.resolve("product.tar.gz");
    }

    private static void verifyCoverage(Path corpus, Properties receipt, Set<String> profiles, int count) throws IOException {
        String projectNames = receipt.getProperty("project.names", "");
        var projects = new TreeSet<>(List.of(projectNames.split(",")));
        assertEquals(count, projects.size(), "Unexpected pinned project inventory: " + projectNames);
        assertEquals(String.join(",", projects), projectNames, "Expected sorted unique project names");
        assertEquals(String.join(",", new TreeSet<>(profiles)), receipt.getProperty("profile.names"), "Receipt profile inventory");
        assertEquals(Integer.toString(count), receipt.getProperty("projects"), "Receipt project count");
        assertEquals(Integer.toString(profiles.size()), receipt.getProperty("profiles"), "Receipt profile count");
        assertEquals(Integer.toString(count * profiles.size()), receipt.getProperty("executions"), "Receipt execution count");
        assertEquals("0", receipt.getProperty("failures"), "Receipt failure count");
        var expected = new TreeSet<Path>();
        for (String project : projects)
            for (String profile : profiles)
                expected.add(Path.of(project, profile, "result.txt"));
        try (var files = Files.walk(corpus)) {
            var actual = files.filter(path -> path.getFileName().toString().equals("result.txt"))
                    .map(corpus::relativize).filter(path -> path.getNameCount() == 3).collect(Collectors.toCollection(TreeSet::new));
            var missing = new TreeSet<>(expected);
            missing.removeAll(actual);
            actual.removeAll(expected);
            assertTrue(missing.isEmpty() && actual.isEmpty(), "Missing results: " + missing + "; unexpected results: " + actual);
        }
        for (Path result : expected)
            assertEquals("PASS", Files.readString(corpus.resolve(result), StandardCharsets.UTF_8).strip(), result.toString());
    }

    @Test
    @EnabledIfSystemProperty(named = "sandbox.cleanup.product", matches = ".+")
    void actualCorpusHelperResolvesAndInventoriesInstalledCleanups(@TempDir Path temporary) throws Exception {
        verifyProfiles(FIXTURES.resolve("profiles"));
        String expected = "BASELINE scope.ä units=1\nAPPLY scope.ä profile";
        byte[] log = ("noise".repeat(5000) + "\nBASELINE scope.ä units=1\nwarning\nAPPLY scope.ä profile")
                .getBytes(StandardCharsets.UTF_8);
        var relayed = new ByteArrayOutputStream();
        try (var input = new ByteArrayInputStream(log) {
            @Override public synchronized int read(byte[] bytes, int offset, int length) {
                return super.read(bytes, offset, Math.min(length, 3));
            }
        }; var live = new PrintStream(relayed, true, StandardCharsets.UTF_8) {
            // Surefire decodes each write independently; splitting a UTF-8 character loses its identity.
            @Override public void write(int value) { write(new byte[] {(byte) value}, 0, 1); }
            @Override public void write(byte[] bytes, int offset, int length) {
                byte[] decoded = new String(bytes, offset, length, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
                super.write(decoded, 0, decoded.length);
            }
        }) {
            relayProgress(input, () -> false, live);
            assertEquals(expected, relayed.toString(StandardCharsets.UTF_8), "Relay progress across chunks, suppress noise, retain final partial lines");
            assertThrows(IOException.class, () -> relayProgress(new InputStream() {
                @Override public int read() throws IOException { throw new IOException("read failure"); }
            }, () -> false, live));
        }
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
        assertEquals("PASS", receipt.getProperty("diagnostic.scope.regression"), "Dependent caller diagnostics must be checked");
    }

    @Test
    @EnabledIfSystemProperty(named = "sandbox.cleanup.family", matches = "core|ui|swt")
    void pinnedUpstreamSourcesPreserveCompilerDiagnostics() throws Exception {
        Set<String> profiles = verifyProfiles(FIXTURES.resolve("profiles"));
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
        String productCommit;
        try (var repository = new FileRepositoryBuilder().findGitDir(ROOT.toFile()).build()) {
            productCommit = repository.resolve(Constants.HEAD).name();
        }
        Path artifact = Path.of(required("productArtifact")).toRealPath();
        Path archive = verifyProductArtifact(artifact, productCommit);
        Path output = Path.of(required("output")).toAbsolutePath().normalize();
        Path runtime = output.resolve("runtime");
        assertFalse(Files.exists(output), "Use a fresh evidence directory: " + output);
        Files.createDirectories(output);
        Files.writeString(output.resolve("source-identity.txt"), remote + "\nrefs/tags/R4_41\n" + commit + "\n");
        Files.writeString(output.resolve("product-commit.txt"), productCommit + "\n");
        Files.copy(artifact.resolve("SHA256SUMS"), output.resolve("product-SHA256SUMS"));
        Files.createDirectory(runtime);
        Path extractionLog = output.resolve("product-extract.log");
        var extraction = new ProcessBuilder("tar", "-xzf", archive.toString(), "-C", runtime.toString())
                .redirectErrorStream(true).redirectOutput(extractionLog.toFile()).start();
        try {
            assertTrue(extraction.waitFor(5, TimeUnit.MINUTES), "Product extraction timed out: " + extractionLog);
            assertEquals(0, extraction.exitValue(), "Cannot extract product: " + extractionLog);
        } finally {
            if (extraction.isAlive()) extraction.destroyForcibly();
        }
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
                assertEquals("PASS", receipt.getProperty("diagnostic.scope.regression"), "Native environment guards must run in every family");
                verifyCoverage(output.resolve("corpus"), receipt, profiles, switch (family) {
                    case "core" -> 17;
                    case "ui" -> 18;
                    case "swt" -> 12;
                    default -> throw new IllegalArgumentException(family);
                });
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
                checkout.toString(), profiles.toString(), corpus.toString(), family, "-vmargs",
                family.equals("inventory") ? "-Xmx512m" : "-Xmx4g");
        Files.write(output.resolve("eclipse-command.txt"), command);
        Process process = new ProcessBuilder(command).directory(runtime.toFile()).redirectErrorStream(true)
                .redirectOutput(output.resolve("eclipse.log").toFile()).start();
        // Keep direct redirection: a failed progress reader cannot truncate or lose the full process log.
        var progress = new CompletableFuture<Void>();
        Thread reader = Thread.ofVirtual().name("cleanup-progress").start(() -> {
            try (var log = Files.newInputStream(output.resolve("eclipse.log"))) {
                relayProgress(log, process::isAlive, System.out);
                progress.complete(null);
            } catch (Throwable failure) {
                progress.completeExceptionally(failure);
            }
        });
        try {
            CompletableFuture.anyOf(process.onExit(), progress).get(timeoutMinutes, TimeUnit.MINUTES);
            progress.get(10, TimeUnit.SECONDS); // Drain the final bytes and surface reader I/O failures.
            int exit = process.exitValue();
            Files.writeString(output.resolve("exit-code.txt"), Integer.toString(exit));
            return exit;
        } catch (TimeoutException timeout) {
            throw new AssertionError("Cleanup process or log reader timed out; see " + output, timeout);
        } finally {
            try {
                if (process.isAlive()) {
                    process.descendants().forEach(ProcessHandle::destroyForcibly);
                    process.destroyForcibly();
                    process.waitFor(10, TimeUnit.SECONDS);
                }
            } finally {
                reader.interrupt();
                assertTrue(reader.join(Duration.ofSeconds(10)), "Cleanup log reader did not stop");
            }
        }
    }

    private static void relayProgress(InputStream log, BooleanSupplier processAlive, PrintStream live) throws IOException, InterruptedException {
        byte[] buffer = new byte[8192], line = new byte[8192];
        int length = 0;
        boolean truncated = false;
        while (true) {
            boolean exited = !processAlive.getAsBoolean();
            int count = log.read(buffer);
            if (count < 0) {
                if (exited) break; // Read once after observing exit, so the final write cannot race EOF.
                Thread.sleep(100);
                continue;
            }
            for (int i = 0; i < count; i++) {
                byte value = buffer[i];
                if (length < line.length) line[length++] = value;
                else truncated = true;
                if (value == '\n') {
                    forwardProgress(line, length, truncated, live);
                    length = 0;
                    truncated = false;
                }
            }
        }
        if (length > 0) forwardProgress(line, length, truncated, live);
    }

    private static void forwardProgress(byte[] line, int length, boolean truncated, PrintStream live) throws IOException {
        String text = new String(line, 0, length, StandardCharsets.UTF_8);
        if (text.startsWith("BASELINE ") || text.startsWith("APPLY ")) {
            live.print(text + (truncated ? " … [full line in eclipse.log]\n" : ""));
            live.flush();
            if (live.checkError()) throw new IOException("Cannot forward cleanup progress to Maven stdout");
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
