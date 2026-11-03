/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.triggerpattern.test.policy;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import javax.xml.parsers.DocumentBuilderFactory;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Real project Apply receipts and complete upstream test evidence share one Maven authority. */
class JdtMathQualificationTest {
    private static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private static final String CORE = "6725c16c24d94c83302346dc384bb915a0f2fe1a", UI = "54bd63e6453a7eb5db0bfc1d60f17e9568095619";
    private static final String RELENG = "10be605e8924c917d5f0013930278a2b1cb756f5";
    @TempDir Path temporary;

    @Test void receiptMustMatchTheObservedSource() throws Exception {
        var fixture = receipt();
        ((Map<String,Object>) ((List<?>) fixture.get("files")).getFirst()).put("observedSourceSha256", "stale");
        assertThrows(AssertionError.class, () -> checkFixture(fixture));
    }

    @Test void receiptRequiresCompleteUniqueSourceScope() throws Exception {
        var missing = receipt(); missing.put("files", List.of());
        var duplicate = receipt(); var files = (List<Object>) duplicate.get("files"); files.add(files.getFirst());
        assertAll(() -> assertThrows(AssertionError.class, () -> checkFixture(missing)),
                () -> assertThrows(AssertionError.class, () -> checkFixture(duplicate)));
    }

    @Test void receiptRequiresSuccessfulApplyAndMatchingProduct() throws Exception {
        var failed = receipt(); failed.put("status", "IN_PROGRESS");
        var wrongProduct = receipt(); wrongProduct.put("sdkSha256", "another-sdk");
        assertAll(() -> assertThrows(AssertionError.class, () -> checkFixture(failed)),
                () -> assertThrows(AssertionError.class, () -> checkFixture(wrongProduct)));
    }

    @Test void zeroChangesStillQualifyEverySourceFile() throws Exception { assertEquals(0, checkFixture(receipt())); }

    @Test void inventoriesMustExecuteEveryRequiredModule() throws Exception {
        xml("before", "main", false); xml("after", "main", false);
        assertThrows(AssertionError.class, () -> inventory(List.of("main", "gtk")));
        xml("before", "gtk", true); xml("after", "gtk", true);
        assertThrows(AssertionError.class, () -> inventory(List.of("main", "gtk")));
    }

    @Test void identicalExecutedInventoriesPass() throws Exception {
        for (String phase : List.of("before", "after")) for (String module : List.of("main", "gtk")) xml(phase, module, false);
        inventory(List.of("main", "gtk"));
    }

    @Test void aReplacedRuntimeClassCannotStandInForCompilerOutput() throws Exception {
        Path classes = Files.createDirectory(temporary.resolve("classes")); Files.write(classes.resolve("Sample.class"), new byte[] {1});
        Path binary = temporary.resolve("runtime.jar");
        try (var jar = new JarOutputStream(Files.newOutputStream(binary))) {
            jar.putNextEntry(new JarEntry("Sample.class")); jar.write(new byte[] {2}); jar.closeEntry();
        }
        assertThrows(AssertionError.class, () -> verifyClasses(classes, binary));
        Files.write(classes.resolve("Sample.class"), new byte[] {2}); verifyClasses(classes, binary);
        Files.write(classes.resolve("Missing.class"), new byte[] {3});
        assertThrows(AssertionError.class, () -> verifyClasses(classes, binary));
    }

    @Test void everyRuntimeMustUseTheExactMainBundle() {
        Path main = temporary.resolve("core/target/org.eclipse.jdt.core-1-SNAPSHOT.jar"), sdk = temporary.resolve("sdk/core.jar");
        Path ant = main.resolveSibling("org.eclipse.jdt.core-1-SNAPSHOT-antadapter.jar");
        assertAll(() -> assertThrows(AssertionError.class, () -> verifyLoadedBundles(Map.of(main, "core", sdk, "core"), main, "core")),
                () -> assertThrows(AssertionError.class, () -> verifyLoadedBundles(Map.of(ant, "core.antadapter", sdk, "core"), main, "core")));
        verifyLoadedBundles(Map.of(main, "core"), main, "core");
    }

    @Test void prebuiltLoaderChangesRequireSeparateBuildEvidence() {
        assertThrows(AssertionError.class, () -> verifyPrebuiltLoader("jar in jar loader/Loader.java", "original", "changed"));
        verifyPrebuiltLoader("jar in jar loader/Loader.java", "same", "same");
        verifyPrebuiltLoader("ui/Editor.java", "original", "changed");
    }

    @Test @Timeout(value = 350, unit = TimeUnit.MINUTES)
    @EnabledIfSystemProperty(named = "sandbox.math.jdt.family", matches = ".*")
    void pinnedOfficialReactorsPreserveBehavior() throws Exception {
        String family = property("family"); Path product = Path.of(property("product")).toRealPath(), output = Path.of(property("output")).toAbsolutePath();
        assertTrue(Set.of("core", "ui").contains(family), "Unknown JDT family: " + family);
        assertFalse(Files.exists(output), "Evidence output must be fresh"); Files.createDirectories(output);
        Path adapter;
        try (var plugins = Files.list(product.resolve("plugins"))) {
            var matches = plugins.filter(p -> p.getFileName().toString().matches("sandbox_math_cleanup_[0-9].*\\.jar")).toList();
            assertEquals(1, matches.size()); adapter = matches.getFirst();
        }
        assertEquals(property("adapterSha256"), hash(adapter));
        try (var checkout = Git.open(Path.of(".").toFile())) {
            assertEquals(property("productCommit"), checkout.getRepository().resolve("HEAD").name(), "Product and source checkout must have the same commit");
        }
        String sdk = hash(Path.of("sandbox_math_cleanup/lib/regelsuche-optimization-sdk.jar"));
        try (var jar = new JarFile(adapter.toFile()); var embedded = jar.getInputStream(jar.getJarEntry("lib/regelsuche-optimization-sdk.jar"))) {
            assertEquals(sdk, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(embedded.readAllBytes())));
        }
        JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve("input-identities.json").toFile(), Map.of(
                "coreCommit", CORE, "uiCommit", UI, "relengCommit", RELENG, "productCommit", property("productCommit"),
                "adapterSha256", hash(adapter), "sdkSha256", sdk, "family", family,
                "profileSha256", hash(Path.of("qa/math-optimization/profiles/bc-strict.properties"))));
        try (var core = PinnedGitRepository.cloneAt(temporary.resolve("core"), URI.create("https://github.com/eclipse-jdt/eclipse.jdt.core.git"), "R4_41", CORE);
                var ui = PinnedGitRepository.cloneAt(temporary.resolve("ui"), URI.create("https://github.com/eclipse-jdt/eclipse.jdt.ui.git"), "R4_41", UI);
                var releng = PinnedGitRepository.cloneAt(temporary.resolve("releng"), URI.create("https://github.com/eclipse-platform/eclipse.platform.releng.aggregator.git"), "R4_41", RELENG)) {
            Path repository = family.equals("core") ? core.directory() : ui.directory();
            Path corePom = core.directory().resolve("org.eclipse.jdt.core/pom.xml");
            String originalPom = Files.readString(corePom), replacement = "<baselineReplace>common</baselineReplace>";
            if (family.equals("core")) {
                assertTrue(originalPom.contains(replacement)); assertEquals(originalPom.indexOf(replacement), originalPom.lastIndexOf(replacement));
                Files.writeString(output.resolve("original-core.pom.xml"), originalPom);
                // Upstream's literal common overrides CLI properties and copies release JARs over changed classes.
                Files.writeString(corePom, originalPom.replace(replacement, "<baselineReplace>none</baselineReplace>"));
            }
            String configuredPom = Files.readString(corePom);
            Files.writeString(output.resolve("configured-core.pom.xml"), configuredPom);
            List<String> projects = family.equals("core") ? List.of("org.eclipse.jdt.core.compiler.batch", "org.eclipse.jdt.core")
                    : List.of("org.eclipse.jdt.core.manipulation", "org.eclipse.jdt.ui");
            var originals = new HashMap<String,Map<String,String>>();
            for (String project : projects) originals.put(project, sources(repository.resolve(project)));
            JSON.writeValue(output.resolve("original-source-hashes.json").toFile(), originals);
            Process process = new ProcessBuilder("bash", "qa/math-optimization/run-jdt-math-before-after.sh", temporary.toString(),
                    product.toString(), output.toString(), property("maven"), family).redirectErrorStream(true).redirectOutput(output.resolve("runner.log").toFile()).start();
            int exit;
            try { assertTrue(process.waitFor(340, TimeUnit.MINUTES), "Upstream qualification timed out"); exit = process.exitValue(); }
            finally {
                if (process.isAlive()) { process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); }
                // Keep partial native reports before the pinned fixture closes, including interrupted builds.
                try (var files = Files.walk(repository)) {
                    for (Path file : files.filter(Files::isRegularFile).filter(p ->
                            (p.getFileName().toString().startsWith("TEST-") && p.toString().endsWith(".xml") && p.toString().contains("/target/surefire-reports/"))
                            || (p.toString().contains("/target/surefire-reports/") && p.getFileName().toString().contains(".dump"))
                            || p.toString().endsWith("/target/work/configuration/config.ini")
                            || p.toString().endsWith("/target/work/data/.metadata/.log")
                            || (projects.stream().anyMatch(project -> p.startsWith(repository.resolve(project).resolve("target")))
                                    && (p.toString().endsWith(".class") || p.toString().endsWith(".jar")))).toList()) {
                        Path retained = output.resolve("last-raw-evidence").resolve(repository.relativize(file));
                        Files.createDirectories(retained.getParent()); Files.copy(file, retained);
                    }
                }
            }
            assertEquals(0, exit, "Upstream process failed; inspect retained exit codes and logs: " + output);
            assertEquals(configuredPom, Files.readString(corePom), "The identical build configuration must survive both phases");
            for (String project : projects) verifyReceipt(output.resolve(project + ".json"), project, repository.resolve(project), originals.get(project), sdk, hash(adapter));
            List<String> modules = family.equals("core") ? List.of("org.eclipse.jdt.compiler.tool.tests", "org.eclipse.jdt.core.tests.builder",
                    "org.eclipse.jdt.core.tests.compiler", "org.eclipse.jdt.core.tests.model", "org.eclipse.jdt.core.tests.performance",
                    "org.eclipse.jdt.apt.pluggable.tests", "org.eclipse.jdt.apt.tests", "org.eclipse.jdt.compiler.apt.tests")
                    : List.of("org.eclipse.jdt.text.tests", "org.eclipse.jdt.ui.tests", "org.eclipse.jdt.ui.tests.refactoring");
            verifyInventories(output.resolve("baseline/reports"), output.resolve("generated/reports"), modules, output.resolve("inventory"));
            verifyInventories(output.resolve("baseline"), output.resolve("generated"), List.of("reports"), output.resolve("inventory/all"));
            for (String phase : List.of("baseline", "generated")) verifyTestBundles(output.resolve(phase), projects, repository);
        }
    }

    private static Map<String,String> sources(Path project) throws Exception {
        var factory = DocumentBuilderFactory.newInstance(); factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var entries = factory.newDocumentBuilder().parse(project.resolve(".classpath").toFile()).getElementsByTagName("classpathentry");
        var result = new HashMap<String,String>();
        for (int i = 0; i < entries.getLength(); i++) {
            var entry = (org.w3c.dom.Element) entries.item(i);
            if (!entry.getAttribute("kind").equals("src")) continue;
            String root = entry.getAttribute("path"); assertFalse(root.startsWith("/"));
            assertTrue(entry.getAttribute("excluding").isEmpty() && entry.getAttribute("including").isEmpty(), "Pinned source roots have no filters");
            try (var files = Files.walk(project.resolve(root))) {
                for (Path file : files.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".java")).toList())
                    assertNull(result.put(project.relativize(file).toString().replace('\\', '/'), hash(file)), "Overlapping source roots");
            }
        }
        assertFalse(result.isEmpty()); return result;
    }
    private static void verifyTestBundles(Path phase, List<String> projects, Path repository) throws Exception {
        var loaded = new HashMap<Path,String>();
        try (var files = Files.walk(phase.resolve("runtime"))) {
            for (Path file : files.filter(p -> p.getFileName().toString().equals("config.ini")).toList()) {
                var properties = new Properties(); try (var reader = Files.newBufferedReader(file)) { properties.load(reader); }
                for (String bundle : properties.getProperty("osgi.bundles", "").split(",")) {
                    if (!bundle.startsWith("reference:file:")) continue;
                    Path path = Path.of(URI.create(bundle.substring("reference:".length()).split("@", 2)[0])).normalize();
                    if (loaded.containsKey(path)) continue;
                    Manifest manifest = null;
                    if (Files.isRegularFile(path) && path.toString().endsWith(".jar")) try (var jar = new JarFile(path.toFile())) { manifest = jar.getManifest(); }
                    else if (Files.isRegularFile(path.resolve("META-INF/MANIFEST.MF"))) try (var input = Files.newInputStream(path.resolve("META-INF/MANIFEST.MF"))) { manifest = new Manifest(input); }
                    if (manifest != null) {
                        String name = manifest.getMainAttributes().getValue("Bundle-SymbolicName");
                        loaded.put(path, name == null ? "" : name.split(";", 2)[0]);
                    }
                }
            }
        }
        var hashes = new HashMap<String,String>();
        for (String project : projects) {
            Path main = Path.of(Files.readString(phase.resolve("bundles").resolve(project + ".path")).strip());
            assertEquals(repository.resolve(project).resolve("target"), main.getParent());
            verifyLoadedBundles(loaded, main, project);
            try (var jar = new JarFile(phase.resolve("bundles").resolve(project + ".jar").toFile())) {
                assertEquals(project, jar.getManifest().getMainAttributes().getValue("Bundle-SymbolicName").split(";", 2)[0]);
            }
            hashes.put(project, hash(phase.resolve("bundles").resolve(project + ".jar")));
            verifyClasses(phase.resolve("classes").resolve(project), phase.resolve("bundles").resolve(project + ".jar"));
            // The pinned Antadapter classifier excludes these two compiler utilities in its POM.
            if (project.equals("org.eclipse.jdt.core")) {
                Path classes = phase.resolve("classes").resolve(project + "-antadapter");
                verifyClasses(classes, phase.resolve("bundles").resolve(project + "-antadapter.jar"),
                        "org/eclipse/jdt/core/CheckDebugAttributes", "org/eclipse/jdt/core/BuildJarIndex");
                Path embedded = phase.resolve("bundles").resolve(project + "-embedded-antadapter.jar");
                try (var jar = new JarFile(phase.resolve("bundles").resolve(project + ".jar").toFile());
                        var input = jar.getInputStream(jar.getJarEntry("jdtCompilerAdapter.jar"))) {
                    Files.copy(input, embedded);
                }
                verifyClasses(classes, embedded);
                hashes.put(project + "-embedded-antadapter", hash(embedded));
            }
        }
        JSON.writeValue(phase.resolve("verified-bundle-hashes.json").toFile(), hashes);
    }
    private static void verifyLoadedBundles(Map<Path,String> loaded, Path main, String project) {
        assertEquals(project, loaded.get(main), "Tests must load the exact reactor main bundle: " + main);
        loaded.forEach((path, name) -> { if (project.equals(name)) assertEquals(main, path, "A test runtime loaded another " + project); });
    }
    private static void verifyClasses(Path classes, Path binary, String... excludedPrefixes) throws Exception {
        try (var jar = new JarFile(binary.toFile())) {
            var entries = jar.stream().filter(e -> !e.isDirectory() && e.getName().endsWith(".class")).toList();
            assertFalse(entries.isEmpty(), "Runtime bundle must contain compiled classes");
            Set<String> expected;
            try (var files = Files.walk(classes)) {
                expected = files.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".class"))
                        .map(p -> classes.relativize(p).toString().replace('\\', '/'))
                        .filter(name -> List.of(excludedPrefixes).stream().noneMatch(name::startsWith)).collect(java.util.stream.Collectors.toSet());
            }
            assertEquals(expected, entries.stream().map(JarEntry::getName).collect(java.util.stream.Collectors.toSet()), "Compiled/runtime class inventory: " + binary);
            for (var entry : entries) try (var input = jar.getInputStream(entry)) {
                assertArrayEquals(Files.readAllBytes(classes.resolve(entry.getName())), input.readAllBytes(),
                        "Runtime class must equal fresh compiler output: " + binary + "!/" + entry.getName());
            }
        }
    }
    private static String property(String name) { String value = System.getProperty("sandbox.math.jdt." + name); assertNotNull(value, name); assertFalse(value.isBlank(), name); return value; }

    private Map<String,Object> receipt() throws Exception {
        Path file = temporary.resolve("demo/src/A.java"); Files.createDirectories(file.getParent()); Files.writeString(file, "class A {}\n");
        String hash = hash(file);
        var entry = new HashMap<String,Object>(Map.of("path", "/demo/src/A.java", "sourceSha256", hash,
                "afterSha256", hash, "observedSourceSha256", hash, "applicationStatus", "ORIGINAL", "changes", List.of()));
        return new HashMap<>(Map.of("project", "demo", "mode", "apply", "status", "SUCCESS", "sdkSha256", "sdk",
                "adapterBundleSha256", "adapter", "failures", List.of(), "files", new ArrayList<>(List.of(entry))));
    }
    private int checkFixture(Map<String,Object> receipt) throws Exception {
        Path report = temporary.resolve("receipt.json"); JSON.writeValue(report.toFile(), receipt);
        return verifyReceipt(report, "demo", temporary.resolve("demo"), Map.of("src/A.java", hash(temporary.resolve("demo/src/A.java"))), "sdk", "adapter");
    }
    private void xml(String phase, String module, boolean skipped) throws Exception {
        Path directory = Files.createDirectories(temporary.resolve(phase).resolve(module));
        Files.writeString(directory.resolve("TEST-suite.xml"), "<testsuite><testcase classname='" + module + "' name='runs'>"
                + (skipped ? "<skipped/>" : "") + "</testcase></testsuite>");
    }
    private void inventory(List<String> modules) throws Exception { verifyInventories(temporary.resolve("before"), temporary.resolve("after"), modules, temporary.resolve("inventory")); }
    private static String hash(Path path) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }

    private static int verifyReceipt(Path report, String project, Path directory, Map<String,String> originals, String sdk, String adapter) throws Exception {
        var headers = new HashMap<String,String>(); var seen = new HashSet<String>(); int changes = 0;
        // Native receipts contain complete sources and proof traces. Never materialize their JSON trees.
        try (JsonParser parser = JSON.createParser(report.toFile())) {
            assertEquals(JsonToken.START_OBJECT, parser.nextToken());
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String name = parser.currentName(); parser.nextToken();
                if (name.equals("files")) {
                    assertEquals(JsonToken.START_ARRAY, parser.currentToken());
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        var file = fields(parser, Set.of("path", "sourceSha256", "afterSha256", "observedSourceSha256", "applicationStatus", "changes"));
                        String prefix = "/" + project + "/", path = file.get("path"); assertNotNull(path);
                        assertTrue(path.startsWith(prefix), path); String relative = path.substring(prefix.length());
                        assertTrue(seen.add(relative), "Duplicate source receipt: " + path);
                        assertNotNull(originals.get(relative), "Unexpected source receipt: " + path);
                        assertEquals(originals.get(relative), file.get("sourceSha256"), path);
                        assertEquals(file.get("afterSha256"), file.get("observedSourceSha256"), path);
                        assertEquals(hash(directory.resolve(relative)), file.get("afterSha256"), path);
                        if (project.equals("org.eclipse.jdt.ui")) verifyPrebuiltLoader(relative, file.get("sourceSha256"), file.get("afterSha256"));
                        assertEquals(file.get("sourceSha256").equals(file.get("afterSha256")) ? "ORIGINAL" : "REPLACEMENT", file.get("applicationStatus"), path);
                        changes += Integer.parseInt(file.get("changes"));
                    }
                } else if (name.equals("failures")) {
                    assertEquals(JsonToken.START_ARRAY, parser.currentToken()); assertEquals(JsonToken.END_ARRAY, parser.nextToken());
                    headers.put(name, "empty");
                } else if (Set.of("project", "mode", "status", "sdkSha256", "adapterBundleSha256").contains(name)) {
                    assertEquals(JsonToken.VALUE_STRING, parser.currentToken()); assertNull(headers.put(name, parser.getText()), "Duplicate header");
                } else parser.skipChildren();
            }
            assertEquals(JsonToken.END_OBJECT, parser.currentToken()); assertNull(parser.nextToken());
        }
        assertEquals(originals.keySet(), seen, "Receipt must cover every project source unit"); assertFalse(seen.isEmpty());
        assertEquals(Map.of("project", project, "mode", "apply", "status", "SUCCESS", "sdkSha256", sdk,
                "adapterBundleSha256", adapter, "failures", "empty"), headers);
        System.out.printf("JDT_APPLY project=%s files=%d changes=%d allObservedHashesMatch=true%n", project, seen.size(), changes);
        return changes;
    }
    private static void verifyPrebuiltLoader(String relative, String before, String after) {
        if (relative.startsWith("jar in jar loader/")) assertEquals(before, after,
                "The upstream build reuses jar-in-jar-loader.zip; changed loader sources need explicit rebuild evidence");
    }
    private static Map<String,String> fields(JsonParser parser, Set<String> selected) throws Exception {
        assertEquals(JsonToken.START_OBJECT, parser.currentToken()); var values = new HashMap<String,String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String name = parser.currentName(); parser.nextToken();
            if (name.equals("changes")) {
                assertEquals(JsonToken.START_ARRAY, parser.currentToken()); int count = 0;
                while (parser.nextToken() != JsonToken.END_ARRAY) { parser.skipChildren(); count++; }
                assertNull(values.put(name, Integer.toString(count)));
            } else if (selected.contains(name)) {
                assertEquals(JsonToken.VALUE_STRING, parser.currentToken()); assertNull(values.put(name, parser.getText()));
            } else parser.skipChildren();
        }
        assertEquals(JsonToken.END_OBJECT, parser.currentToken()); return values;
    }
    private static void verifyInventories(Path before, Path after, List<String> modules, Path output) throws Exception {
        for (String module : modules) {
            var comparison = JUnitXmlInventoryComparator.compare(before.resolve(module), after.resolve(module), JUnitXmlInventoryComparator.Mapping.empty());
            comparison.write(output.resolve(module + ".json")); assertTrue(comparison.passed(), module + ": " + comparison.problems());
            assertTrue(comparison.baseline().passed() > 0 && comparison.migrated().passed() > 0, "Module must execute passing tests: " + module);
        }
    }
}
