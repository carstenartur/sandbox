/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Full-call calibration evidence, never an automatic rewrite of numerical cost weights. */
public final class FullCallBenchmarkReport {
    private FullCallBenchmarkReport() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Supply JMH JSON and output JSON");
        var mapper = new ObjectMapper(); Path input = Path.of(args[0]);
        var pairs = new LinkedHashMap<String, Map<String, JsonNode>>();
        for (var result : mapper.readTree(input.toFile())) {
            String benchmark = result.path("benchmark").asText();
            String prefix = BouncyCastleHashToCurveBenchmark.class.getName() + ".";
            if (!benchmark.startsWith(prefix)) throw new IllegalArgumentException("Unexpected benchmark");
            String variant = benchmark.substring(prefix.length());
            if (!variant.equals("original") && !variant.equals("generated")) throw new IllegalArgumentException("Unexpected variant");
            String curve = result.path("params").path("curveProfile").asText(), bytes = result.path("params").path("messageBytes").asText();
            if (!BouncyCastleHashToCurveBenchmark.PROFILES.contains(curve) || !java.util.Set.of("32", "256").contains(bytes))
                throw new IllegalArgumentException("Unexpected curve/message configuration");
            String key = curve + "/" + bytes;
            if (pairs.computeIfAbsent(key, ignored -> new LinkedHashMap<>()).put(variant, result) != null)
                throw new IllegalArgumentException("Duplicate measurement: " + key);
        }
        if (pairs.size() != 8) throw new IllegalArgumentException("Expected all eight curve/message pairs");
        var rows = new ArrayList<Map<String, Object>>();
        for (var pair : pairs.entrySet()) {
            JsonNode original = pair.getValue().get("original"), generated = pair.getValue().get("generated");
            if (original == null || generated == null || !original.path("params").equals(generated.path("params"))
                    || !original.path("jvm").equals(generated.path("jvm"))
                    || !original.path("jvmArgs").equals(generated.path("jvmArgs"))
                    || !original.path("jdkVersion").equals(generated.path("jdkVersion")))
                throw new IllegalArgumentException("Incomplete or mismatched pair: " + pair.getKey());
            JsonNode before = original.path("primaryMetric"), after = generated.path("primaryMetric");
            String direction = comparison(before, after);
            rows.add(Map.of("case", pair.getKey(), "timing", direction,
                    "timeRatio", after.path("score").asDouble() / before.path("score").asDouble(),
                    "originalMicros", before, "generatedMicros", after,
                    "originalAllocations", original.path("secondaryMetrics").path("gc.alloc.rate.norm"),
                    "generatedAllocations", generated.path("secondaryMetrics").path("gc.alloc.rate.norm")));
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(Path.of(args[1]).toFile(), Map.of(
                "schema", "sandbox.full-call-calibration/v1", "rawJmhSha256", CorpusFiles.sha256(input),
                "interpretation", "Non-overlapping JMH intervals are directional evidence, not a general speed guarantee. Overlap is inconclusive; these results do not set optimizer cost weights.",
                "comparisons", rows));
    }

    static String comparison(JsonNode original, JsonNode generated) {
        for (var metric : new JsonNode[] {original, generated}) {
            var interval = metric.path("scoreConfidence");
            if (!"us/op".equals(metric.path("scoreUnit").asText()) || !metric.path("score").isNumber()
                    || !Double.isFinite(metric.path("score").asDouble()) || metric.path("score").asDouble() <= 0
                    || interval.size() != 2 || !interval.get(0).isNumber() || !interval.get(1).isNumber()
                    || !Double.isFinite(interval.get(0).asDouble()) || !Double.isFinite(interval.get(1).asDouble())
                    || interval.get(0).asDouble() > interval.get(1).asDouble())
                throw new IllegalArgumentException("Invalid timing measurement");
        }
        if (generated.path("scoreConfidence").get(1).asDouble() < original.path("scoreConfidence").get(0).asDouble()) return "LOWER_TIME";
        if (generated.path("scoreConfidence").get(0).asDouble() > original.path("scoreConfidence").get(1).asDouble()) return "HIGHER_TIME";
        return "INCONCLUSIVE";
    }
}
