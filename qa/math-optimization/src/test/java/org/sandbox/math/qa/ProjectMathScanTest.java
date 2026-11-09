/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import com.fasterxml.jackson.databind.ObjectMapper;

class ProjectMathScanTest {
    @TempDir Path directory;
    @Test void realPipelineWritesActualRankedChangesWithoutMutatingTheInput() throws Exception {
        String source = "public class Sample { public int f(int u,int v,int w) { return (u & v) | (~u & w); } "
                + "public int g(int[] x,int[] z) { int c=x[0]; z[0]=c; return c; } }";
        Path java = directory.resolve("Sample.java"); Files.writeString(java, source);
        var compiler = ToolProvider.getSystemJavaCompiler(); assertNotNull(compiler);
        assertEquals(0, compiler.run(null, null, null, "--release", "17", "-d", directory.toString(), java.toString()));
        Path binary = archive("binary.jar", "Sample.class", Files.readAllBytes(directory.resolve("Sample.class")));
        Path sources = archive("sources.jar", "Sample.java", source.getBytes(StandardCharsets.UTF_8));
        Path profile = profile(); String hash = CorpusFiles.sha256(sources);
        Path output = directory.resolve("result");
        ProjectMathScan.main(new String[] {binary.toString(), sources.toString(), profile.toString(), output.toString(), hash, "17", "--tasks", "Sample.java"});
        var json = new ObjectMapper().readTree(output.resolve("review.json").toFile());
        var inputs = new ObjectMapper().readTree(output.resolve("inputs.json").toFile());
        assertEquals("warning", inputs.path("compilerOptions")
                .path(org.eclipse.jdt.core.JavaCore.COMPILER_PB_UNNECESSARY_TYPE_CHECK).asText());
        assertTrue(json.path("files").get(0).path("originalWarnings").isArray());
        assertTrue(json.path("files").get(0).path("generatedWarnings").isArray());
        assertEquals("COMPLETED", json.path("status").asText());
        assertEquals(1, json.path("parsedFiles").asInt());
        assertEquals(0, json.path("parseErrorFiles").asInt());
        assertTrue(json.path("acceptedRegions").asInt() > 0);
        assertTrue(json.path("files").get(0).path("reviewCandidates").size() > 0);
        var tasks = json.path("files").get(0).path("computationTasks");
        assertEquals(2, tasks.size());
        var events = tasks.get(1).path("schedule");
        assertEquals(4, events.size());
        assertEquals("SOURCE_READ", events.get(0).path("kind").asText());
        assertEquals("LOCAL_COMMIT", events.get(1).path("kind").asText());
        assertEquals("ARRAY_STORE", events.get(2).path("kind").asText());
        assertEquals("RETURN", events.get(3).path("kind").asText());
        assertEquals(3, tasks.get(1).path("outputTypes").size());
        assertEquals(hash, CorpusFiles.sha256(sources)); assertEquals(source, Files.readString(java));
        assertNotEquals(source, Files.readString(output.resolve("generated/Sample.java")));
        assertTrue(Files.exists(output.resolve("COMPLETED")));
        var fixture = new Properties();
        try (var input = Files.newInputStream(output.resolve("benchmark.properties"))) { fixture.load(input); }
        assertEquals("sandbox.generated-corpus-fixture/v1", fixture.getProperty("schema"));
        assertEquals(CorpusFiles.sha256(binary), fixture.getProperty("binarySha256"));
        assertEquals(hash, fixture.getProperty("sourcesSha256"));
        assertEquals(CorpusFiles.sha256(output.resolve("original/Sample.java")), fixture.getProperty("original.Sample.java"));
        assertEquals(CorpusFiles.sha256(output.resolve("generated/Sample.java")), fixture.getProperty("generated.Sample.java"));
        assertEquals("17", fixture.getProperty("targetJava"));
        assertThrows(IOException.class, () -> ProjectMathScan.main(new String[] {binary.toString(), sources.toString(), profile.toString(), output.toString(), hash, "17"}));
    }
    @Test void incompleteProfileFailsBeforeAnalysis() throws Exception {
        Path path = directory.resolve("partial.properties"); Files.writeString(path, "cleanup.mathematics=true\n");
        assertThrows(IOException.class, () -> ProjectMathScan.profile(path, 17));
    }
    @Test void strictReedMullerRetainsTheVerifiedRuntimeMaskSpan() throws Exception {
        String entry = "org/bouncycastle/pqc/crypto/hqc/ReedMuller.java";
        String binary = System.getProperty("sandbox.math.bc.mavenBinary");
        Path output = directory.resolve("reed-muller");
        ProjectMathScan.main(new String[] {binary, System.getProperty("sandbox.math.bc.mavenSources"),
                "qa/math-optimization/profiles/bc-strict.properties", output.toString(),
                "b37ac84b1d5435ab7b8d166c16ab9f75e09f68f8ec50479bae433939b241b03f", "17", entry});
        var mapper = new ObjectMapper(); var report = mapper.readTree(output.resolve("review.json").toFile());
        assertEquals("COMPLETED", report.path("status").asText());
        var candidates = report.path("files").get(0).path("reviewCandidates");
        assertTrue(java.util.stream.StreamSupport.stream(candidates.spliterator(), false)
                .anyMatch(e -> e.path("offset").asInt() == 2062 && e.path("length").asInt() == 42),
                "Missing accepted ReedMuller span 2062/42: " + candidates);
        var evidence = mapper.readTree(output.resolve("evidence/" + entry + ".json").toFile());
        var accepted = java.util.stream.StreamSupport.stream(evidence.spliterator(), false)
                .filter(e -> e.path("offset").asInt() == 2062 && e.path("length").asInt() == 42).findFirst().orElseThrow();
        assertEquals("int abs = (posMask & t) | (~posMask & -t);", accepted.path("original").asText());
        assertEquals("BITVECTOR_TRUTH_TABLE", accepted.path("proof").path("proofMethods").get(0).asText());
        assertTrue(accepted.path("cost").path("estimatedRuntimeImprovement").asBoolean());
        for (String variant : java.util.List.of("original", "generated")) {
            Path source = output.resolve(variant + "/" + entry);
            assertTrue(Files.readString(source).contains("int posMask = t > 0 ? -1 : 0;"));
            assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "17", "-cp", binary,
                    "-d", Files.createDirectory(output.resolve(variant + "-classes")).toString(), source.toString()));
        }
    }
    @Test void maintainedCorpusProfilesAreCompleteAndPreserveTheirExplicitBudgets() throws Exception {
        for (String name : java.util.List.of("bc-strict", "bc-mathematical", "swt-strict")) {
            var options = ProjectMathScan.profile(Path.of("qa/math-optimization/profiles/" + name + ".properties"),
                    name.startsWith("swt") ? 21 : 17);
            assertEquals(1_000_000, options.workBudget()); assertEquals(20_000, options.maxStates());
            assertEquals(5, options.timeoutSeconds());
            assertEquals(name.equals("bc-mathematical"), options.mathematicalOptIn());
        }
    }
    private Path profile() throws Exception {
        var values = new HashMap<>(MathCleanUpOptions.defaults(17).toMap());
        values.put(MathCleanUpOptions.CLEANUP, "true"); values.put(MathCleanUpOptions.KINDS, "INT");
        values.put(MathCleanUpOptions.WORK_BUDGET, "1000000"); values.put(MathCleanUpOptions.MAX_STATES, "20000");
        values.put(MathCleanUpOptions.EXPLANATIONS, "NONE");
        var properties = new Properties(); properties.putAll(values); Path path = directory.resolve("profile.properties");
        try (var output = Files.newOutputStream(path)) { properties.store(output, "test input"); }
        return path;
    }
    private Path archive(String file, String entry, byte[] bytes) throws Exception {
        Path path = directory.resolve(file);
        try (var output = new JarOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new JarEntry(entry)); output.write(bytes); output.closeEntry();
        }
        return path;
    }
}
