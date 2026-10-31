/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FiniteSeriesQualificationTest {
    @TempDir static Path temporary;
    static Path fixtures;

    @BeforeAll static void prepare() throws Exception {
        fixtures = temporary.resolve("fixtures");
        FiniteSeriesQualification.generate(fixtures);
    }

    @Test void everyProductionReductionCompilesAndAgreesAcrossItsCrossover() throws Exception {
        for (String family : List.of("polynomial", "stream", "affine", "geometric", "product")) {
            try (var input = inputs(fixtures, family, 16)) {
                input.setup();
                assertNotSame(input.original.getClass().getClassLoader(), input.generated.getClass().getClassLoader());
                for (int n : new int[] {-17, 0, 1, 16, (int) input.minimumIterations - 1,
                        (int) input.minimumIterations, (int) input.minimumIterations + 1, 1_000_000})
                    for (int seed : new int[] {0, 17, Integer.MIN_VALUE, Integer.MAX_VALUE})
                        assertEquals(input.original.applyAsInt(n, seed), input.generated.applyAsInt(n, seed),
                                family + ": count=" + n + ", seed=" + seed);
                assertTrue(input.minimumIterations > 31);
                assertTrue(input.minimumIterations <= 1_000_000);
            }
        }
    }

    @Test void independentlyKnownValuesPreventTwoEmptyImplementationsFromPassing() throws Exception {
        try (var input = inputs(fixtures, "polynomial", 16)) {
            input.setup();
            assertEquals(31, input.original.applyAsInt(4, 17));
            assertEquals(31, input.generated.applyAsInt(4, 17));
            assertEquals(17, input.generated.applyAsInt(-1, 17));
        }
        try (var input = inputs(fixtures, "affine", 16)) {
            input.setup();
            assertEquals(464, input.generated.applyAsInt(3, 17));
        }
        for (var expected : java.util.Map.of("stream", 56, "geometric", 57, "product", 459).entrySet()) {
            try (var input = inputs(fixtures, expected.getKey(), 16)) {
                input.setup();
                assertEquals(expected.getValue().intValue(), input.original.applyAsInt(3, 17));
                assertEquals(expected.getValue().intValue(), input.generated.applyAsInt(3, 17));
            }
        }
    }

    @Test void sourceTamperingAndIncompleteQualificationFailBeforeMeasurement() throws Exception {
        for (String variant : List.of("original", "generated")) {
            Path copy = copyFixture("changed-" + variant);
            Files.writeString(copy.resolve("polynomial/" + variant + "/Calculation.java"), "// changed\n",
                    java.nio.file.StandardOpenOption.APPEND);
            try (var input = inputs(copy, "polynomial", 16)) { assertThrows(IOException.class, input::setup); }
        }
        Path incomplete = copyFixture("incomplete"); Files.delete(incomplete.resolve("COMPLETED"));
        try (var input = inputs(incomplete, "polynomial", 16)) { assertThrows(IOException.class, input::setup); }
        assertThrows(IOException.class, () -> FiniteSeriesQualification.generate(fixtures));
    }

    @Test void driftAfterSetupFailsAtTeardown() throws Exception {
        Path copy = copyFixture("late-drift");
        var input = inputs(copy, "polynomial", 16); input.setup();
        Files.writeString(copy.resolve("polynomial/generated/Calculation.java"), "// drift\n",
                java.nio.file.StandardOpenOption.APPEND);
        assertThrows(IOException.class, input::close);
    }

    @Test void reportRetainsAllRawPairsAndRejectsMissingOrDifferentMeasurementSettings() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var rows = measurements(); Path raw = temporary.resolve("jmh.json"), report = temporary.resolve("review.json");
        mapper.writeValue(raw.toFile(), rows);
        FiniteSeriesBenchmarkReport.main(new String[] {raw.toString(), fixtures.toString(), report.toString()});
        var result = mapper.readTree(report.toFile());
        assertEquals(10, result.path("comparisons").size());
        assertEquals(CorpusFiles.sha256(raw), result.path("rawJmhSha256").asText());
        assertEquals("LOWER_TIME", result.path("comparisons").get(0).path("timing").asText());
        for (String field : List.of("jvm", "jdkVersion", "forks", "measurementIterations", "measurementTime", "threads")) {
            var bad = rows.deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode) bad.get(0)).put(field, "different");
            mapper.writeValue(raw.toFile(), bad);
            assertThrows(IllegalArgumentException.class, () -> FiniteSeriesBenchmarkReport.main(
                    new String[] {raw.toString(), fixtures.toString(), report.toString()}), field);
        }
        rows.remove(0); mapper.writeValue(raw.toFile(), rows);
        assertThrows(IllegalArgumentException.class, () -> FiniteSeriesBenchmarkReport.main(
                new String[] {raw.toString(), fixtures.toString(), report.toString()}));
    }

    @Test void reportRejectsMissingOrShortSettingsOnBothSides() throws Exception {
        for (String field : List.of("warmupTime", "measurementTime", "jvm", "jdkVersion", "vmVersion", "vmName", "jmhVersion", "jvmArgs")) {
            var rows = measurements();
            for (var row : rows) ((com.fasterxml.jackson.databind.node.ObjectNode) row).remove(field);
            reject(rows, fixtures, "missing " + field);
        }
        for (String field : List.of("warmupTime", "measurementTime")) {
            var rows = measurements();
            for (var row : rows) ((com.fasterxml.jackson.databind.node.ObjectNode) row).put(field, "1 ms");
            reject(rows, fixtures, "short " + field);
        }
        var rows = measurements();
        for (var row : rows) ((com.fasterxml.jackson.databind.node.ObjectNode) row).put("forks", "2");
        reject(rows, fixtures, "numeric string is not a JMH fork count");
    }

    @Test void reportRejectsInventedScoresAndIntervalsEvenWithPositiveRawSamples() throws Exception {
        var rows = measurements();
        var generated = (com.fasterxml.jackson.databind.node.ObjectNode) rows.get(1).path("primaryMetric");
        generated.set("rawData", rows.get(0).path("primaryMetric").path("rawData").deepCopy());
        reject(rows, fixtures, "equal measured data cannot support a lower generated score");
        rows = measurements(); generated = (com.fasterxml.jackson.databind.node.ObjectNode) rows.get(1).path("primaryMetric");
        generated.putArray("scoreConfidence").add(1).add(2);
        reject(rows, fixtures, "interval must be derived from samples and contain the score");
        rows = measurements(); generated = (com.fasterxml.jackson.databind.node.ObjectNode) rows.get(1).path("primaryMetric");
        generated.put("scoreError", 100);
        reject(rows, fixtures, "reported uncertainty must match samples");
    }

    @Test void reportRejectsADifferentEnvironmentForACompleteLaterPair() throws Exception {
        for (String field : List.of("jvm", "jdkVersion", "vmName", "vmVersion", "jvmArgs")) {
            var rows = measurements();
            for (int index : new int[] {2, 3}) {
                var row = (com.fasterxml.jackson.databind.node.ObjectNode) rows.get(index);
                if (field.equals("jvmArgs")) ((com.fasterxml.jackson.databind.node.ArrayNode) row.path(field)).add("-Ddifferent=true");
                else row.put(field, "another environment");
            }
            reject(rows, fixtures, "a later complete pair differs: " + field);
        }
    }

    @Test void reportRejectsACompleteInternallyConsistentFixtureSwapAfterTiming() throws Exception {
        Path copy = copyFixture("swapped-after-timing"); var rows = measurements();
        for (var row : rows) ((com.fasterxml.jackson.databind.node.ObjectNode) row.path("params")).put("fixtureDirectory", copy.toString());
        Path changed = copy.resolve("polynomial/generated/Calculation.java");
        Files.writeString(changed, "\n", java.nio.file.StandardOpenOption.APPEND);
        var manifest = new java.util.Properties();
        try (var reader = Files.newBufferedReader(copy.resolve("benchmark.properties"))) { manifest.load(reader); }
        manifest.setProperty("polynomial.generated", CorpusFiles.sha256(changed));
        MathCorpusRunner.writeProperties(copy.resolve("benchmark.properties"), manifest);
        reject(rows, copy, "valid new sources cannot inherit an old measurement");
    }

    private static void reject(com.fasterxml.jackson.databind.node.ArrayNode rows, Path fixture, String reason) throws Exception {
        Path raw = temporary.resolve("invalid-jmh.json"), report = temporary.resolve("invalid-review.json");
        new com.fasterxml.jackson.databind.ObjectMapper().writeValue(raw.toFile(), rows);
        assertThrows(IllegalArgumentException.class, () -> FiniteSeriesBenchmarkReport.main(
                new String[] {raw.toString(), fixture.toString(), report.toString()}), reason);
    }

    private static com.fasterxml.jackson.databind.node.ArrayNode measurements() throws Exception {
        var rows = new com.fasterxml.jackson.databind.ObjectMapper().createArrayNode();
        for (String family : List.of("polynomial", "stream", "affine", "geometric", "product"))
            for (String count : List.of("16", "1000000")) for (String variant : List.of("original", "generated")) {
                var row = rows.addObject(); row.put("benchmark", FiniteSeriesBenchmark.class.getName() + "." + variant);
                row.putObject("params").put("family", family).put("count", count).put("fixtureDirectory", fixtures.toString())
                        .put("fixtureManifestSha256", CorpusFiles.sha256(fixtures.resolve("benchmark.properties")));
                row.put("jvm", "java"); row.put("jdkVersion", "25"); row.put("vmVersion", "25"); row.put("jmhVersion", "1.37");
                row.put("vmName", "test VM"); row.putArray("jvmArgs").add("-Xms128m").add("-Xmx512m");
                row.put("mode", "avgt"); row.put("threads", 1); row.put("forks", 2);
                row.put("warmupIterations", 3); row.put("measurementIterations", 5);
                row.put("warmupBatchSize", 1); row.put("measurementBatchSize", 1);
                row.put("warmupTime", "500 ms"); row.put("measurementTime", "500 ms");
                int score = variant.equals("original") ? 100 : 50;
                var metric = row.putObject("primaryMetric").put("score", score).put("scoreError", 0).put("scoreUnit", "us/op");
                metric.putArray("scoreConfidence").add(score).add(score);
                var raw = metric.putArray("rawData");
                for (int fork = 0; fork < 2; fork++) { var samples = raw.addArray(); for (int i = 0; i < 5; i++) samples.add(score); }
                row.putObject("secondaryMetrics").putObject("gc.alloc.rate.norm").put("score", 0).put("scoreUnit", "B/op");
            }
        return rows;
    }

    private static FiniteSeriesBenchmark.Inputs inputs(Path root, String family, int count) throws Exception {
        var input = new FiniteSeriesBenchmark.Inputs();
        input.fixtureManifestSha256 = CorpusFiles.sha256(root.resolve("benchmark.properties"));
        input.fixtureDirectory = root.toString(); input.family = family; input.count = count; return input;
    }
    private static Path copyFixture(String name) throws Exception {
        Path copy = temporary.resolve(name);
        try (var files = Files.walk(fixtures)) {
            for (Path file : files.toList()) {
                Path target = copy.resolve(fixtures.relativize(file));
                if (Files.isDirectory(file)) Files.createDirectories(target); else Files.copy(file, target);
            }
        }
        return copy;
    }
}
