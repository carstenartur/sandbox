/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BlsSourceCohortTest {
    private static final String PACKAGE = "org/bouncycastle/crypto/bls/";
    private static final List<String> TYPES = List.of("BLS12_381G2HashToCurve", "BLS12_381Pairing",
            "Fp12Element", "Fp2Element", "Fp6Element");
    @TempDir static Path temporary;
    private static Path binary, sources, profile, cohort, alternateJava;
    private static final ObjectMapper JSON = new ObjectMapper();

    @BeforeAll static void prepareActualSources() throws Exception {
        // The Maven execution names this entry point; its absence is a failed contract, not a skipped cohort.
        var preparer = assertDoesNotThrow(() -> Class.forName("org.sandbox.math.qa.BlsSourceCohort"),
                "The complete BLS source-cohort preparation entry point is required");
        var entry = assertDoesNotThrow(() -> preparer.getMethod("main", String[].class));
        binary = Path.of(System.getProperty("sandbox.math.bc.jar"));
        sources = Path.of(System.getProperty("sandbox.math.bc.sourceArchive"));
        profile = Path.of("qa/math-optimization/profiles/bc-bls.properties").toAbsolutePath();
        cohort = temporary.resolve("cohort");
        entry.invoke(null, (Object) new String[] {binary.toString(), sources.toString(), profile.toString(), cohort.toString()});
        String export = System.getProperty("sandbox.math.corpus.evidence");
        if (export != null && !export.isBlank()) {
            Path receipts = Path.of(export).resolve("bls-source-cohort"); Files.createDirectories(receipts);
            for (String file : List.of("review.json", "inputs.json", "cohort.json", "benchmark.properties"))
                Files.copy(cohort.resolve(file), receipts.resolve(file), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Test void canonicalPreparationIncludesAllFiveWholeClassesAndPreservesUnchangedFp12() throws Exception {
        var report = JSON.readTree(cohort.resolve("review.json").toFile());
        assertEquals(5, report.path("parsedFiles").asInt()); assertEquals(0, report.path("parseErrorFiles").asInt());
        assertEquals(9, report.path("acceptedRegions").asInt());
        var expected = Map.of("BLS12_381G2HashToCurve", 2, "BLS12_381Pairing", 1,
                "Fp12Element", 0, "Fp2Element", 1, "Fp6Element", 5);
        var manifest = BlsSourceCohort.read(cohort.resolve("benchmark.properties"));
        try (var archive = new JarFile(sources.toFile())) {
            for (String type : TYPES) {
                String path = PACKAGE + type + ".java";
                var rows = new java.util.ArrayList<com.fasterxml.jackson.databind.JsonNode>();
                report.path("files").forEach(row -> { if (path.equals(row.path("path").asText())) rows.add(row); });
                assertEquals(1, rows.size(), path);
                assertEquals(expected.get(type).intValue(), rows.getFirst().path("acceptedRegions").asInt(), path);
                try (var input = archive.getInputStream(archive.getJarEntry(path))) {
                    assertArrayEquals(input.readAllBytes(), Files.readAllBytes(cohort.resolve("original").resolve(path)));
                }
                for (String variant : List.of("original", "generated")) {
                    Path source = cohort.resolve(variant).resolve(path);
                    assertTrue(Files.isRegularFile(source), "Complete cohort requires " + source);
                    assertEquals(CorpusFiles.sha256(source), manifest.getProperty(variant + "." + path));
                }
                if (type.equals("Fp12Element")) assertArrayEquals(
                        Files.readAllBytes(cohort.resolve("original").resolve(path)),
                        Files.readAllBytes(cohort.resolve("generated").resolve(path)), "Unchanged Fp12 remains a compiled cohort member");
            }
        }
        assertEquals(CorpusFiles.sha256(cohort.resolve("review.json")), manifest.getProperty("reviewSha256"));
        assertEquals(CorpusFiles.sha256(cohort.resolve("cohort.json")), manifest.getProperty("cohortSha256"));
        assertThrows(IOException.class, () -> BlsSourceCohort.main(
                new String[] {binary.toString(), sources.toString(), profile.toString(), cohort.toString()}));
        assertThrows(IOException.class, () -> BlsSourceCohort.main(new String[] {binary.toString(), sources.toString(),
                "qa/math-optimization/profiles/bc-mathematical.properties", temporary.resolve("unconsented").toString()}));
    }

    @Test void completeG2AndPairingCallsLoadAllFiveCompiledClassesAndAgree() throws Exception {
        var loaded = new HashSet<String>();
        for (String workload : List.of("G2_32", "G2_256", "PAIRING")) try (var inputs = inputs(cohort, workload)) {
            inputs.setup();
            assertNotSame(inputs.original.getClass().getClassLoader(), inputs.generated.getClass().getClassLoader());
            for (int index = 0; index < 16; index++) {
                byte[] message = new byte[workload.equals("G2_256") ? 256 : 32]; message[0] = (byte) index;
                var original = inputs.original.apply(message); var generated = inputs.generated.apply(message);
                assertEquals(workload.equals("PAIRING") ? 576 : 96, generated.length);
                assertArrayEquals(original, generated, workload + " input " + index);
            }
            for (int length : new int[] {0, 1, 257}) {
                byte[] message = new byte[length];
                assertArrayEquals(inputs.original.apply(message), inputs.generated.apply(message), workload + " boundary " + length);
            }
            assertFalse(java.util.Arrays.equals(inputs.original.apply(new byte[]{0}), inputs.original.apply(new byte[]{1})),
                    "Distinct complete calls must not collapse into one constant output");
            assertEquals(inputs.loaded("original"), inputs.loaded("generated"));
            loaded.addAll(inputs.loaded("generated"));
        }
        assertEquals(TYPES.stream().map(type -> "org.bouncycastle.crypto.bls." + type).collect(java.util.stream.Collectors.toSet()), loaded);
    }

    @Test void sourceProvenanceAndManifestDriftAreRejectedBeforeMeasurement() throws Exception {
        for (String path : List.of("original/" + PACKAGE + "Fp12Element.java", "generated/" + PACKAGE + "BLS12_381G2HashToCurve.java",
                "evidence/" + PACKAGE + "BLS12_381Pairing.java.json", "profile.properties", "inputs.json", "review.json",
                "source-context/" + PACKAGE + "Fp2Element.java")) {
            Path copy = copyFixture("changed-" + path.replace('/', '-'));
            String hash = CorpusFiles.sha256(copy.resolve("benchmark.properties"));
            Files.writeString(copy.resolve(path), "\n", java.nio.file.StandardOpenOption.APPEND);
            assertThrows(IOException.class, () -> BlsSourceCohort.requireCurrent(copy, binary, sources, hash), path);
        }
        Path incomplete = copyFixture("incomplete"); Files.delete(incomplete.resolve("COMPLETED"));
        assertThrows(IOException.class, () -> BlsSourceCohort.requireCurrent(incomplete, binary, sources,
                CorpusFiles.sha256(incomplete.resolve("benchmark.properties"))));
        Path wrong = temporary.resolve("wrong.jar"); Files.write(wrong, new byte[]{1});
        assertThrows(IOException.class, () -> BlsSourceCohort.requireCurrent(cohort, wrong, sources,
                CorpusFiles.sha256(cohort.resolve("benchmark.properties"))));
        assertThrows(IOException.class, () -> BlsSourceCohort.requireCurrent(cohort, binary, wrong,
                CorpusFiles.sha256(cohort.resolve("benchmark.properties"))));
    }

    @Test void missingCompiledCohortCannotFallBackToTheOriginalArchive() throws Exception {
        try (var loader = new BlsBenchmark.VariantLoader(Files.createDirectory(temporary.resolve("empty-classes")), binary)) {
            for (String type : TYPES) assertThrows(ClassNotFoundException.class,
                    () -> loader.loadClass("org.bouncycastle.crypto.bls." + type), type);
        }
    }

    @Test void sourceDriftAfterSetupFailsAtTeardown() throws Exception {
        Path copy = copyFixture("late-drift");
        try (var inputs = inputs(copy, "G2_32")) {
            inputs.setup();
            Files.writeString(copy.resolve("generated/" + PACKAGE + "Fp2Element.java"), "\n", java.nio.file.StandardOpenOption.APPEND);
            assertThrows(IOException.class, inputs::close);
        }
    }

    @Test void reportRetainsAllThreePairsAndTheirReconstructedIntervalDirections() throws Exception {
        Path raw = temporary.resolve("valid-jmh.json"), report = temporary.resolve("valid-report.json");
        JSON.writeValue(raw.toFile(), measurements());
        BlsBenchmarkReport.main(new String[]{raw.toString(), cohort.toString(), report.toString()});
        var result = JSON.readTree(report.toFile()); assertEquals(3, result.path("comparisons").size());
        assertEquals(CorpusFiles.sha256(raw), result.path("rawJmhSha256").asText());
        assertEquals(CorpusFiles.sha256(cohort.resolve("benchmark.properties")), result.path("fixtureManifestSha256").asText());
        assertEquals(System.getProperty("java.version"), result.path("measurementRuntime").path("jdkVersion").asText());
        var expected = Map.of("G2_32", "LOWER_TIME", "G2_256", "INCONCLUSIVE", "PAIRING", "HIGHER_TIME");
        for (var row : result.path("comparisons")) assertEquals(expected.get(row.path("case").asText()), row.path("timing").asText());
    }

    @Test void reportRejectsIncompletePairsSettingsAndDifferentLaterEnvironments() throws Exception {
        var rows = measurements(); rows.remove(0); reject(rows, cohort);
        rows = measurements(); rows.set(5, rows.get(0).deepCopy()); reject(rows, cohort);
        for (String field : List.of("jvm", "jdkVersion", "forks", "threads", "warmupTime", "measurementTime", "jmhVersion", "jvmArgs")) {
            rows = measurements(); for (var row : rows) ((ObjectNode) row).remove(field); reject(rows, cohort);
        }
        rows = measurements(); for (var row : rows) ((ObjectNode) row).put("measurementTime", "1 ms"); reject(rows, cohort);
        rows = measurements(); for (var row : rows) ((ObjectNode) row).put("forks", "2"); reject(rows, cohort);
        for (String field : List.of("jvm", "jdkVersion", "vmName", "vmVersion", "jvmArgs")) {
            rows = measurements();
            for (int index : new int[]{2, 3}) {
                var row = (ObjectNode) rows.get(index);
                if (field.equals("jvmArgs")) ((ArrayNode) row.path(field)).add("-Ddifferent=true");
                else row.put(field, "another environment");
            }
            reject(rows, cohort);
        }
    }

    @Test void reportRejectsInventedStatisticsAndChangedFixtures() throws Exception {
        var rows = measurements();
        ((ObjectNode) rows.get(1).path("primaryMetric")).set("rawData", rows.get(0).path("primaryMetric").path("rawData").deepCopy());
        reject(rows, cohort);
        rows = measurements(); ((ObjectNode) rows.get(1).path("primaryMetric")).putArray("scoreConfidence").add(1).add(2); reject(rows, cohort);
        rows = measurements(); ((ObjectNode) rows.get(1).path("primaryMetric")).put("scoreError", 100); reject(rows, cohort);
        Path changed = copyFixture("swapped-fixture"); rows = measurements();
        for (var row : rows) ((ObjectNode) row.path("params")).put("fixtureDirectory", changed.toString());
        String path = PACKAGE + "Fp2Element.java";
        Files.writeString(changed.resolve("generated").resolve(path), "\n", java.nio.file.StandardOpenOption.APPEND);
        var manifest = BlsSourceCohort.read(changed.resolve("benchmark.properties"));
        manifest.setProperty("generated." + path, CorpusFiles.sha256(changed.resolve("generated").resolve(path)));
        try (var writer = Files.newBufferedWriter(changed.resolve("benchmark.properties"))) { manifest.store(writer, "changed fixture"); }
        reject(rows, changed);
    }

    @ParameterizedTest @ValueSource(strings = {"jvm", "jdkVersion", "vmName", "vmVersion"})
    void reportRejectsUniformForeignRuntime(String field) throws Exception {
        var rows = measurements();
        var foreign = Map.of("jvm", "/foreign/jdk/bin/java", "jdkVersion", "21.0.1",
                "vmName", "Foreign VM", "vmVersion", "21.0.1+12");
        for (var row : rows) ((ObjectNode) row).put(field, foreign.get(field));
        reject(rows, cohort);
    }

    @Test void unchangedQualificationRuntimeStillSetsUpAndReports() throws Exception {
        for (String operation : List.of("setup", "report")) {
            var result = runtimeProcess(Path.of(org.openjdk.jmh.util.Utils.getCurrentJvm()), operation, "qualified");
            assertEquals(0, result.exit(), result.log());
        }
    }

    @ParameterizedTest @ValueSource(strings = {"setup", "report"})
    void differentExecutableAndHomeCannotConsumeSourceQualification(String operation) throws Exception {
        var result = runtimeProcess(alternateJava(), operation, "foreign");
        assertNotEquals(Path.of(System.getProperty("java.home")).toRealPath(),
                Path.of(result.identity().path("javaHome").asText()).toRealPath());
        assertNotEquals(Path.of(org.openjdk.jmh.util.Utils.getCurrentJvm()).toRealPath(),
                Path.of(result.identity().path("jvm").asText()).toRealPath());
        assertNotEquals(0, result.exit(), "A different executable must not consume the qualified cohort: " + result.log());
        assertTrue(result.log().contains("qualification JVM"), result.log());
        if (operation.equals("report")) assertFalse(Files.exists(result.directory().resolve("report.json")));
    }

    private record RuntimeResult(int exit, String log, com.fasterxml.jackson.databind.JsonNode identity, Path directory) { }
    private static RuntimeResult runtimeProcess(Path javaExecutable, String operation, String label) throws Exception {
        Path output = Files.createDirectory(temporary.resolve(operation + "-" + label)), log = output.resolve("process.log");
        var process = new ProcessBuilder(javaExecutable.toString(), "-Xmx768m", "-cp", System.getProperty("java.class.path"),
                RuntimeProbe.class.getName(), operation, cohort.toString(), binary.toString(), sources.toString(), output.toString())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean finished = process.waitFor(90, java.util.concurrent.TimeUnit.SECONDS);
        if (!finished) { process.destroyForcibly(); process.waitFor(); }
        String export = System.getProperty("sandbox.math.corpus.evidence");
        if (export != null && !export.isBlank()) {
            Path retained = Path.of(export).resolve("bls-runtime-identity").resolve(operation + "-" + label);
            Files.createDirectories(retained);
            try (var files = Files.list(output)) {
                for (Path file : files.toList())
                    Files.copy(file, retained.resolve(file.getFileName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        String diagnostics = Files.readString(log);
        assertTrue(finished, "Runtime child timed out: " + diagnostics);
        var identity = JSON.readTree(output.resolve("runtime.json").toFile());
        String executable = identity.path("executable").asText();
        if (!executable.isBlank()) assertEquals(javaExecutable.toRealPath(), Path.of(executable).toRealPath(), diagnostics);
        assertEquals(javaExecutable.toRealPath(), Path.of(identity.path("jvm").asText()).toRealPath(), diagnostics);
        assertEquals(System.getProperty("java.runtime.version"), identity.path("javaRuntime").asText());
        assertEquals(System.getProperty("java.version"), identity.path("jdkVersion").asText());
        assertEquals(System.getProperty("java.vm.name"), identity.path("vmName").asText());
        assertEquals(System.getProperty("java.vm.version"), identity.path("vmVersion").asText());
        assertEquals(System.getProperty("java.class.path"), identity.path("classPath").asText());
        assertEquals(JSON.readTree(cohort.resolve("review.json").toFile()).path("runtimeInputs"), identity.path("runtimeInputs"));
        return new RuntimeResult(process.exitValue(), diagnostics, identity, output);
    }

    private static Path alternateJava() throws Exception {
        if (alternateJava != null) return alternateJava;
        Path original = Path.of(System.getProperty("java.home")).toRealPath();
        Path copy = Files.createDirectory(temporary.resolve("separate-jdk"));
        Files.walkFileTree(original, java.util.EnumSet.of(java.nio.file.FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                new java.nio.file.SimpleFileVisitor<>() {
                    @Override public java.nio.file.FileVisitResult preVisitDirectory(Path directory,
                            java.nio.file.attribute.BasicFileAttributes attributes) throws IOException {
                        Files.createDirectories(copy.resolve(original.relativize(directory))); return java.nio.file.FileVisitResult.CONTINUE;
                    }
                    @Override public java.nio.file.FileVisitResult visitFile(Path file,
                            java.nio.file.attribute.BasicFileAttributes attributes) throws IOException {
                        Path destination = copy.resolve(original.relativize(file));
                        try { Files.createLink(destination, file.toRealPath()); }
                        catch (IOException | UnsupportedOperationException unavailable) {
                            Files.copy(file, destination, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
                        }
                        return java.nio.file.FileVisitResult.CONTINUE;
                    }
                });
        alternateJava = copy.resolve("bin").resolve(Path.of(org.openjdk.jmh.util.Utils.getCurrentJvm()).getFileName());
        return alternateJava;
    }

    public static final class RuntimeProbe {
        public static void main(String[] args) throws Exception {
            cohort = Path.of(args[1]); binary = Path.of(args[2]); sources = Path.of(args[3]); Path output = Path.of(args[4]);
            JSON.writeValue(output.resolve("runtime.json").toFile(), Map.of(
                    "jvm", org.openjdk.jmh.util.Utils.getCurrentJvm(), "javaHome", System.getProperty("java.home"),
                    "executable", ProcessHandle.current().info().command().orElse(""),
                    "javaRuntime", System.getProperty("java.runtime.version"), "classPath", System.getProperty("java.class.path"),
                    "jdkVersion", System.getProperty("java.version"), "vmName", System.getProperty("java.vm.name"),
                    "vmVersion", System.getProperty("java.vm.version"), "runtimeInputs", ProjectMathScan.runtimeInputs()));
            if (args[0].equals("setup")) {
                try (var input = inputs(cohort, "G2_32")) { input.setup(); }
            } else {
                Path raw = output.resolve("synthetic-jmh.json"); JSON.writeValue(raw.toFile(), measurements());
                BlsBenchmarkReport.main(new String[]{raw.toString(), cohort.toString(), output.resolve("report.json").toString()});
            }
        }
    }

    private static BlsBenchmark.Inputs inputs(Path directory, String workload) throws Exception {
        var input = new BlsBenchmark.Inputs(); input.fixtureDirectory = directory.toString();
        input.fixtureManifestSha256 = CorpusFiles.sha256(directory.resolve("benchmark.properties"));
        input.binaryArchive = binary.toString(); input.sourceArchive = sources.toString(); input.workload = workload;
        return input;
    }
    private static Path copyFixture(String name) throws Exception {
        Path copy = temporary.resolve(name); Files.createDirectory(copy);
        var metadata = JSON.readTree(cohort.resolve("cohort.json").toFile());
        var files = new LinkedHashSet<>(List.of("benchmark.properties", "COMPLETED", "cohort.json"));
        metadata.path("files").properties().forEach(entry -> files.add(entry.getKey()));
        metadata.path("sourceDependencies").properties().forEach(entry -> files.add("source-context/" + entry.getKey()));
        // Fp2 is a required consulted source; copying it separately also covers drift of that proof dependency.
        files.add("source-context/" + PACKAGE + "Fp2Element.java");
        for (String file : files) {
            Path target = copy.resolve(file); Files.createDirectories(target.getParent()); Files.copy(cohort.resolve(file), target);
        }
        return copy;
    }
    private static void reject(ArrayNode rows, Path fixtures) throws Exception {
        Path raw = temporary.resolve("invalid-jmh.json"), report = temporary.resolve("invalid-report.json");
        JSON.writeValue(raw.toFile(), rows);
        assertThrows(IllegalArgumentException.class, () -> BlsBenchmarkReport.main(new String[]{raw.toString(), fixtures.toString(), report.toString()}));
    }
    private static ArrayNode measurements() throws Exception {
        var rows = JSON.createArrayNode();
        for (String workload : List.of("G2_32", "G2_256", "PAIRING")) for (String variant : List.of("original", "generated")) {
            var row = rows.addObject().put("benchmark", BlsBenchmark.class.getName() + "." + variant);
            row.putObject("params").put("workload", workload).put("fixtureDirectory", cohort.toString())
                    .put("fixtureManifestSha256", CorpusFiles.sha256(cohort.resolve("benchmark.properties")))
                    .put("binaryArchive", binary.toString()).put("sourceArchive", sources.toString());
            row.put("jvm", org.openjdk.jmh.util.Utils.getCurrentJvm()); row.put("jdkVersion", System.getProperty("java.version"));
            row.put("vmVersion", System.getProperty("java.vm.version")); row.put("vmName", System.getProperty("java.vm.name"));
            row.put("jmhVersion", "1.37"); row.putArray("jvmArgs").add("-Xms128m").add("-Xmx512m");
            row.put("mode", "avgt"); row.put("threads", 1); row.put("forks", 2);
            row.put("warmupIterations", 3); row.put("measurementIterations", 5);
            row.put("warmupBatchSize", 1); row.put("measurementBatchSize", 1);
            row.put("warmupTime", "500 ms"); row.put("measurementTime", "500 ms");
            int score = variant.equals("original") || workload.equals("G2_256") ? 100 : workload.equals("G2_32") ? 50 : 150;
            var metric = row.putObject("primaryMetric").put("score", score).put("scoreError", 0).put("scoreUnit", "us/op");
            metric.putArray("scoreConfidence").add(score).add(score);
            var raw = metric.putArray("rawData");
            for (int fork = 0; fork < 2; fork++) { var samples = raw.addArray(); for (int i = 0; i < 5; i++) samples.add(score); }
            row.putObject("secondaryMetrics").putObject("gc.alloc.rate.norm").put("score", 0).put("scoreUnit", "B/op");
        }
        return rows;
    }
}
