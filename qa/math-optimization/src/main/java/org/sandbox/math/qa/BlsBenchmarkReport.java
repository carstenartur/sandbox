/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Complete paired BLS observations with reconstructed 99.9% intervals; no optimizer weight changes. */
public final class BlsBenchmarkReport {
    private BlsBenchmarkReport() { }
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Supply raw JMH JSON, its BLS fixture directory and report output");
        Path raw = Path.of(args[0]), directory = Path.of(args[1]).toAbsolutePath().normalize();
        var mapper = new ObjectMapper(); var measurements = mapper.readTree(raw.toFile());
        if (!measurements.isArray() || measurements.size() != 6) throw new IllegalArgumentException("Expected all six BLS timing rows");
        String digest = CorpusFiles.sha256(directory.resolve("benchmark.properties"));
        var pairs = new LinkedHashMap<String, Map<String, JsonNode>>();
        var currentRuntime = Map.of("jvm", org.openjdk.jmh.util.Utils.getCurrentJvm(),
                "jdkVersion", System.getProperty("java.version"), "vmName", System.getProperty("java.vm.name"),
                "vmVersion", System.getProperty("java.vm.version"));
        JsonNode environment = null;
        for (var row : measurements) {
            String prefix = BlsBenchmark.class.getName() + ".", name = row.path("benchmark").asText();
            if (!row.isObject() || !name.startsWith(prefix)) throw new IllegalArgumentException("Unexpected BLS measurement");
            String variant = name.substring(prefix.length()), workload = row.path("params").path("workload").asText();
            var parameters = row.path("params");
            if (!List.of("original", "generated").contains(variant) || !BlsBenchmark.WORKLOADS.contains(workload)
                    || !digest.equals(parameters.path("fixtureManifestSha256").asText())
                    || !directory.equals(Path.of(parameters.path("fixtureDirectory").asText()).toAbsolutePath().normalize())
                    || parameters.path("binaryArchive").asText().isBlank() || parameters.path("sourceArchive").asText().isBlank())
                throw new IllegalArgumentException("Unknown or swapped BLS fixture configuration");
            FiniteSeriesBenchmarkReport.requireSettings(row);
            for (var field : currentRuntime.entrySet())
                if (!field.getValue().equals(row.path(field.getKey()).asText()))
                    throw new IllegalArgumentException("Measurement differs from the qualified JVM: " + field.getKey());
            if (environment == null) environment = row;
            else {
                for (String key : List.of("jvm", "jvmArgs", "jdkVersion", "vmName", "vmVersion", "jmhVersion"))
                    if (!environment.path(key).equals(row.path(key))) throw new IllegalArgumentException("Mixed measurement environment: " + key);
                for (String key : List.of("binaryArchive", "sourceArchive"))
                    if (!environment.path("params").path(key).equals(parameters.path(key))) throw new IllegalArgumentException("Mixed source archives");
            }
            ((com.fasterxml.jackson.databind.node.ObjectNode) row).set("verifiedPrimaryMetric",
                    FiniteSeriesBenchmarkReport.verifiedTiming(row.path("primaryMetric")));
            var allocations = row.path("secondaryMetrics").path("gc.alloc.rate.norm");
            if (!"B/op".equals(allocations.path("scoreUnit").asText()) || !allocations.path("score").isNumber()
                    || !Double.isFinite(allocations.path("score").asDouble()) || allocations.path("score").asDouble() < 0)
                throw new IllegalArgumentException("Missing allocation measurement");
            if (pairs.computeIfAbsent(workload, ignored -> new LinkedHashMap<>()).put(variant, row) != null)
                throw new IllegalArgumentException("Duplicate BLS timing row");
        }
        if (environment == null || !pairs.keySet().equals(BlsBenchmark.WORKLOADS)) throw new IllegalArgumentException("Incomplete BLS workloads");
        var comparisons = new ArrayList<Map<String, Object>>();
        for (var pair : pairs.entrySet()) {
            var original = pair.getValue().get("original"); var generated = pair.getValue().get("generated");
            if (original == null || generated == null) throw new IllegalArgumentException("Missing BLS timing pair");
            for (String key : List.of("params", "jvm", "jvmArgs", "jdkVersion", "vmName", "vmVersion", "jmhVersion",
                    "mode", "threads", "forks", "warmupIterations", "measurementIterations", "warmupTime", "measurementTime",
                    "warmupBatchSize", "measurementBatchSize"))
                if (!original.path(key).equals(generated.path(key))) throw new IllegalArgumentException("Mismatched BLS pair: " + key);
            var before = original.path("verifiedPrimaryMetric"); var after = generated.path("verifiedPrimaryMetric");
            comparisons.add(Map.of("case", pair.getKey(), "timing", FullCallBenchmarkReport.comparison(before, after),
                    "generatedOverOriginal", after.path("score").asDouble() / before.path("score").asDouble(),
                    "originalMicros", before, "generatedMicros", after,
                    "originalAllocations", original.path("secondaryMetrics").path("gc.alloc.rate.norm"),
                    "generatedAllocations", generated.path("secondaryMetrics").path("gc.alloc.rate.norm")));
        }
        var cohort = BlsSourceCohort.requireCurrent(directory,
                Path.of(environment.path("params").path("binaryArchive").asText()),
                Path.of(environment.path("params").path("sourceArchive").asText()), digest);
        var scan = mapper.readTree(directory.resolve("review.json").toFile());
        var report = new LinkedHashMap<String, Object>();
        report.put("schema", "sandbox.bls-whole-call-performance/v1"); report.put("rawJmhSha256", CorpusFiles.sha256(raw));
        report.put("fixtureManifestSha256", digest); report.put("reviewSha256", CorpusFiles.sha256(directory.resolve("review.json")));
        report.put("classes", cohort.path("classes")); report.put("options", scan.path("options"));
        report.put("javaRuntime", scan.path("javaRuntime")); report.put("jvmArgs", environment.path("jvmArgs"));
        report.put("measurementRuntime", currentRuntime);
        report.put("comparisons", comparisons);
        report.put("interpretation", "All ten samples reconstruct the mean and 99.9% interval. Separated intervals are local directional evidence; overlap is inconclusive. Full generated guards and fallback paths remain timed. No optimizer cost weights, universal speedup or constant-time guarantee follows.");
        report.put("allocationInterpretation", "GC samples include JVM/harness work and final-trial source/runtime validation and cleanup outside primary latency timing. They cannot isolate candidate allocations. Raw samples are retained.");
        mapper.writerWithDefaultPrettyPrinter().writeValue(Path.of(args[2]).toFile(), report);
    }
}
