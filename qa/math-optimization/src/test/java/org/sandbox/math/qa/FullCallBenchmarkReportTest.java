/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class FullCallBenchmarkReportTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path temporary;
    @Test void reportRequiresCompleteComparablePairsAndRetainsRawInputIdentity() throws Exception {
        var mapper = new ObjectMapper(); var rows = mapper.createArrayNode();
        for (String profile : BouncyCastleHashToCurveBenchmark.PROFILES) for (String size : new String[] {"32", "256"})
            for (String variant : new String[] {"original", "generated"}) {
                var row = rows.addObject(); row.put("benchmark", BouncyCastleHashToCurveBenchmark.class.getName() + "." + variant);
                row.putObject("params").put("curveProfile", profile).put("messageBytes", size);
                row.put("jvm", "java"); row.put("jdkVersion", "25"); row.putArray("jvmArgs").add("-Xmx512m");
                row.putObject("primaryMetric").put("score", 100).put("scoreUnit", "us/op").putArray("scoreConfidence").add(90).add(110);
            }
        var input = temporary.resolve("jmh.json"); var output = temporary.resolve("report.json");
        mapper.writeValue(input.toFile(), rows); FullCallBenchmarkReport.main(new String[] {input.toString(), output.toString()});
        var report = mapper.readTree(output.toFile()); assertEquals(8, report.path("comparisons").size());
        assertEquals(CorpusFiles.sha256(input), report.path("rawJmhSha256").asText());
        rows.add(rows.get(0).deepCopy()); mapper.writeValue(input.toFile(), rows);
        assertThrows(IllegalArgumentException.class, () -> FullCallBenchmarkReport.main(new String[] {input.toString(), output.toString()}));
        rows.remove(rows.size() - 1); rows.remove(rows.size() - 1); mapper.writeValue(input.toFile(), rows);
        assertThrows(IllegalArgumentException.class, () -> FullCallBenchmarkReport.main(new String[] {input.toString(), output.toString()}));
    }
    @Test void overlapIsInconclusiveAndSeparatedIntervalsHaveAnExplicitDirection() throws Exception {
        var json = new ObjectMapper();
        var original = json.readTree("{\"score\":100,\"scoreUnit\":\"us/op\",\"scoreConfidence\":[90,110]}");
        for (String test : new String[] {"95,85,105,INCONCLUSIVE", "75,70,80,LOWER_TIME", "125,120,130,HIGHER_TIME"}) {
            String[] parts = test.split(",");
            var candidate = json.readTree("{\"score\":" + parts[0] + ",\"scoreUnit\":\"us/op\",\"scoreConfidence\":[" + parts[1] + "," + parts[2] + "]}");
            assertEquals(parts[3], FullCallBenchmarkReport.comparison(original, candidate));
        }
        var invalid = original.deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode) invalid).put("scoreUnit", "ops/s");
        assertThrows(IllegalArgumentException.class, () -> FullCallBenchmarkReport.comparison(original, invalid));
        assertThrows(IllegalArgumentException.class, () -> FullCallBenchmarkReport.comparison(original, json.readTree("{}")));
    }
}
