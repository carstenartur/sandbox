/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openjdk.jmh.util.ListStatistics;

/** Paired observations with uncertainty; deliberately does not tune optimizer crossover weights. */
public final class FiniteSeriesBenchmarkReport {
    private FiniteSeriesBenchmarkReport() { }
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Supply JMH JSON, fixture directory and output JSON");
        Path raw = Path.of(args[0]), fixtures = Path.of(args[1]); var mapper = new ObjectMapper();
        var manifest = read(fixtures.resolve("benchmark.properties"));
        if (!Files.isRegularFile(fixtures.resolve("COMPLETED"))
                || !"sandbox.finite-series-fixtures/v1".equals(manifest.getProperty("schema")))
            throw new IllegalArgumentException("Incomplete fixtures");
        CorpusFiles.requireHash(fixtures.resolve("implementation.properties"), manifest.getProperty("implementationSha256", ""));
        CorpusFiles.requireHash(fixtures.resolve("sandbox-source.properties"), manifest.getProperty("sourceReceiptSha256", ""));
        CorpusFiles.requireHash(fixtures.resolve("qualification.json"), manifest.getProperty("qualificationSha256", ""));
        var pairs = new LinkedHashMap<String, Map<String, JsonNode>>();
        JsonNode environment = null;
        String manifestHash = CorpusFiles.sha256(fixtures.resolve("benchmark.properties"));
        String prefix = FiniteSeriesBenchmark.class.getName() + ".";
        for (var row : mapper.readTree(raw.toFile())) {
            if (!row.isObject()) throw new IllegalArgumentException("Expected a JMH result object");
            String name = row.path("benchmark").asText();
            if (!name.startsWith(prefix)) throw new IllegalArgumentException("Unexpected benchmark");
            String variant = name.substring(prefix.length()), family = row.path("params").path("family").asText();
            String count = row.path("params").path("count").asText();
            if (!List.of("original", "generated").contains(variant) || !FiniteSeriesQualification.FAMILIES.contains(family)
                    || !List.of("16", "1000000").contains(count)
                    || !manifestHash.equals(row.path("params").path("fixtureManifestSha256").asText())
                    || !Path.of(row.path("params").path("fixtureDirectory").asText()).toAbsolutePath().normalize()
                            .equals(fixtures.toAbsolutePath().normalize())) throw new IllegalArgumentException("Unknown measured configuration");
            requireSettings(row);
            if (environment == null) environment = row;
            else for (String key : List.of("jvm", "jvmArgs", "jdkVersion", "vmName", "vmVersion", "jmhVersion"))
                if (!environment.path(key).equals(row.path(key))) throw new IllegalArgumentException("Mixed measurement environment: " + key);
            ((com.fasterxml.jackson.databind.node.ObjectNode) row).set("verifiedPrimaryMetric", verifiedTiming(row.path("primaryMetric")));
            var allocation = row.path("secondaryMetrics").path("gc.alloc.rate.norm");
            if (!"B/op".equals(allocation.path("scoreUnit").asText()) || !allocation.path("score").isNumber()
                    || !Double.isFinite(allocation.path("score").asDouble()) || allocation.path("score").asDouble() < 0)
                throw new IllegalArgumentException("Missing allocation measurement");
            CorpusFiles.requireHash(fixtures.resolve(family).resolve(variant).resolve("Calculation.java"), manifest.getProperty(family + "." + variant, ""));
            if (pairs.computeIfAbsent(family + "/" + count, ignored -> new LinkedHashMap<>()).put(variant, row) != null)
                throw new IllegalArgumentException("Duplicate timing row");
        }
        if (pairs.size() != 10) throw new IllegalArgumentException("Expected all ten family/count pairs");
        var rows = new ArrayList<Map<String, Object>>();
        for (var pair : pairs.entrySet()) {
            var original = pair.getValue().get("original"); var generated = pair.getValue().get("generated");
            if (original == null || generated == null) throw new IllegalArgumentException("Incomplete timing pair");
            for (String key : List.of("params", "jvm", "jvmArgs", "jdkVersion", "vmName", "vmVersion", "jmhVersion", "mode",
                    "threads", "forks", "warmupIterations", "measurementIterations", "warmupTime", "measurementTime",
                    "warmupBatchSize", "measurementBatchSize"))
                if (!original.path(key).equals(generated.path(key))) throw new IllegalArgumentException("Mismatched measurement: " + key);
            var before = original.path("verifiedPrimaryMetric"); var after = generated.path("verifiedPrimaryMetric");
            rows.add(Map.of("case", pair.getKey(), "timing", FullCallBenchmarkReport.comparison(before, after),
                    "generatedOverOriginal", after.path("score").asDouble() / before.path("score").asDouble(),
                    "originalMicros", before, "generatedMicros", after,
                    "originalAllocations", original.path("secondaryMetrics").path("gc.alloc.rate.norm"),
                    "generatedAllocations", generated.path("secondaryMetrics").path("gc.alloc.rate.norm")));
        }
        var first = pairs.values().iterator().next().get("original");
        mapper.writerWithDefaultPrettyPrinter().writeValue(Path.of(args[2]).toFile(), Map.of(
                "schema", "sandbox.finite-series-performance/v2", "rawJmhSha256", CorpusFiles.sha256(raw),
                "fixtureManifestSha256", manifestHash,
                "source", read(fixtures.resolve("sandbox-source.properties")),
                "sdkSha256", read(fixtures.resolve("implementation.properties")).getProperty("sdk.sha256"),
                "jdkVersion", first.path("jdkVersion"), "jvmArgs", first.path("jvmArgs"),
                "interpretation", "The measured manifest parameter binds sources. Means and 99.9% confidence intervals are reconstructed from all ten raw samples with JMH statistics. Complete generated guards/fallbacks remain timed. Separated intervals are local directional evidence; overlap is inconclusive. Ratios do not set optimizer crossover weights or establish general speedups.",
                "allocationInterpretation", "GC profiler samples include JVM/harness work and final-trial hash verification/cleanup, which is outside primary latency timing. These allocation values are unsuitable for isolating candidate allocations or claiming allocation improvements/regressions. All original raw samples are retained.",
                "comparisons", rows));
    }

    private static void requireSettings(JsonNode row) {
        for (var setting : Map.of("forks", 2, "threads", 1, "warmupIterations", 3, "measurementIterations", 5,
                "warmupBatchSize", 1, "measurementBatchSize", 1).entrySet()) {
            var value = row.path(setting.getKey());
            if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() != setting.getValue())
                throw new IllegalArgumentException("Unqualified " + setting.getKey());
        }
        for (var setting : Map.of("mode", "avgt", "jmhVersion", "1.37", "warmupTime", "500 ms", "measurementTime", "500 ms").entrySet())
            if (!row.path(setting.getKey()).isTextual() || !setting.getValue().equals(row.path(setting.getKey()).textValue()))
                throw new IllegalArgumentException("Unqualified " + setting.getKey());
        for (String key : List.of("jvm", "jdkVersion", "vmName", "vmVersion"))
            if (!row.path(key).isTextual() || row.path(key).textValue().isBlank()) throw new IllegalArgumentException("Missing " + key);
        var arguments = row.path("jvmArgs");
        if (!arguments.isArray() || arguments.isEmpty()) throw new IllegalArgumentException("Missing JVM arguments");
        var flags = new ArrayList<String>();
        for (var argument : arguments) {
            if (!argument.isTextual() || argument.textValue().isBlank()) throw new IllegalArgumentException("Invalid JVM argument");
            flags.add(argument.textValue());
        }
        if (!flags.containsAll(List.of("-Xms128m", "-Xmx512m"))) throw new IllegalArgumentException("Unqualified heap settings");
    }

    private static JsonNode verifiedTiming(JsonNode metric) {
        var samples = metric.path("rawData"); var statistics = new ListStatistics();
        if (!metric.isObject() || !"us/op".equals(metric.path("scoreUnit").asText()) || !samples.isArray() || samples.size() != 2)
            throw new IllegalArgumentException("Missing independent fork samples");
        for (var fork : samples) {
            if (!fork.isArray() || fork.size() != 5) throw new IllegalArgumentException("Missing measured iterations");
            for (var sample : fork) {
                if (!sample.isNumber() || !Double.isFinite(sample.asDouble()) || sample.asDouble() <= 0)
                    throw new IllegalArgumentException("Invalid raw timing sample");
                statistics.addValue(sample.asDouble());
            }
        }
        double mean = statistics.getMean(), error = statistics.getMeanErrorAt(0.999);
        double[] interval = statistics.getConfidenceIntervalAt(0.999);
        requireStatistic(metric.path("score"), mean); requireStatistic(metric.path("scoreError"), error);
        var reported = metric.path("scoreConfidence");
        if (!reported.isArray() || reported.size() != 2) throw new IllegalArgumentException("Missing confidence interval");
        requireStatistic(reported.get(0), interval[0]); requireStatistic(reported.get(1), interval[1]);
        if (metric.path("scoreError").asDouble() < 0 || reported.get(0).asDouble() > metric.path("score").asDouble()
                || metric.path("score").asDouble() > reported.get(1).asDouble()) throw new IllegalArgumentException("Invalid timing summary");
        var verified = (com.fasterxml.jackson.databind.node.ObjectNode) metric.deepCopy();
        // Classify with reconstructed values, so serialization-rounding tolerance cannot change the verdict.
        verified.put("score", mean).put("scoreError", error);
        verified.putArray("scoreConfidence").add(interval[0]).add(interval[1]); return verified;
    }
    private static void requireStatistic(JsonNode actual, double expected) {
        if (!actual.isNumber() || !Double.isFinite(actual.asDouble()) || !Double.isFinite(expected)
                || Math.abs(actual.asDouble() - expected) > Math.max(1e-12, 64 * Math.ulp(expected)))
            throw new IllegalArgumentException("Timing summary does not match the raw samples");
    }
    private static Properties read(Path file) throws Exception {
        var values = new Properties(); try (var reader = Files.newBufferedReader(file)) { values.load(reader); } return values;
    }
}
