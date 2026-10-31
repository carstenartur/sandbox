/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.IntBinaryOperator;
import javax.tools.ToolProvider;
import org.openjdk.jmh.annotations.*;

/** Complete compiled original versus actual emitted Java, including all guards and fallbacks. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 2, jvmArgsAppend = {"-Xms128m", "-Xmx512m"})
public class FiniteSeriesBenchmark {
    @State(Scope.Thread)
    public static class Inputs implements AutoCloseable {
        @Param({""}) public String fixtureDirectory;
        @Param({""}) public String fixtureManifestSha256;
        @Param({"polynomial", "stream", "affine", "geometric", "product"}) public String family;
        @Param({"16", "1000000"}) public int count;
        IntBinaryOperator original, generated;
        long minimumIterations;
        private final int[] counts = new int[64], seeds = new int[64];
        private final List<URLClassLoader> loaders = new ArrayList<>();
        private Properties manifest, implementation;
        private String manifestHash;
        private Path compiled;
        private int cursor;

        @Setup(Level.Trial) public void setup() throws Exception {
            if (fixtureDirectory == null || fixtureDirectory.isBlank() || !FiniteSeriesQualification.FAMILIES.contains(family)
                    || count != 16 && count != 1_000_000) throw new IllegalArgumentException("Unknown benchmark configuration");
            Path root = Path.of(fixtureDirectory);
            manifest = read(root.resolve("benchmark.properties"));
            manifestHash = fixtureManifestSha256;
            if (manifestHash == null || !manifestHash.matches("[0-9a-f]{64}"))
                throw new IOException("Supply the fixture manifest SHA as a measured JMH parameter");
            CorpusFiles.requireHash(root.resolve("benchmark.properties"), manifestHash);
            if (!"sandbox.finite-series-fixtures/v1".equals(manifest.getProperty("schema"))
                    || !"17".equals(manifest.getProperty("targetJava")) || !Files.isRegularFile(root.resolve("COMPLETED")))
                throw new IOException("Expected completed Java-17 finite-series fixtures");
            implementation = read(root.resolve("implementation.properties"));
            requireCurrent(); MathCorpusRunner.requireUnchangedImplementation(implementation);
            minimumIterations = Long.parseLong(manifest.getProperty(family + ".minimumIterations"));
            if (minimumIterations <= 31 || minimumIterations > 1_000_000)
                throw new IOException("Small/large datasets no longer straddle the recorded crossover");
            try {
                compiled = Files.createTempDirectory("finite-series-jmh-");
                original = compile("original"); generated = compile("generated");
                var random = new Random(0xF1717E);
                for (int i = 0; i < counts.length; i++) {
                    counts[i] = count + (i & 15); seeds[i] = random.nextInt();
                    requireEqual(counts[i], seeds[i]);
                }
                for (int n : new int[] {-17, 0, 1, 16, (int) minimumIterations - 1,
                        (int) minimumIterations, (int) minimumIterations + 1, 1_000_000})
                    for (int seed : new int[] {0, 17, Integer.MIN_VALUE, Integer.MAX_VALUE}) requireEqual(n, seed);
                requireCurrent(); cursor = 0;
                System.out.println("FINITE_SERIES_QUALIFIED family=" + family + " count=" + count
                        + " manifest=" + manifestHash + " sdk=" + implementation.getProperty("sdk.sha256")
                        + " comparisons=96 minimumIterations=" + minimumIterations);
            } catch (Exception | Error failure) {
                try { cleanup(); } catch (IOException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
                throw failure;
            }
        }

        private void requireEqual(int n, int seed) throws IOException {
            if (original.applyAsInt(n, seed) != generated.applyAsInt(n, seed))
                throw new IOException("Compiled result mismatch: " + family + ", count=" + n + ", seed=" + seed);
        }

        private void requireCurrent() throws IOException {
            Path root = Path.of(fixtureDirectory);
            CorpusFiles.requireHash(root.resolve("benchmark.properties"), manifestHash);
            for (String receipt : List.of("implementation", "sandbox-source", "qualification")) {
                String filename = receipt + (receipt.equals("qualification") ? ".json" : ".properties");
                String key = receipt.equals("sandbox-source") ? "sourceReceiptSha256" : receipt + "Sha256";
                CorpusFiles.requireHash(root.resolve(filename), manifest.getProperty(key, ""));
            }
            for (String variant : List.of("original", "generated"))
                CorpusFiles.requireHash(root.resolve(family).resolve(variant).resolve("Calculation.java"),
                        manifest.getProperty(family + "." + variant, ""));
        }

        private IntBinaryOperator compile(String variant) throws Exception {
            Path output = Files.createDirectory(compiled.resolve(variant));
            Path source = output.resolve("Calculation.java");
            Files.copy(Path.of(fixtureDirectory).resolve(family).resolve(variant).resolve("Calculation.java"), source);
            Path bridge = output.resolve("Invocation.java");
            Files.writeString(bridge, "public final class Invocation implements java.util.function.IntBinaryOperator {"
                    + "public int applyAsInt(int n,int seed){return Calculation.run(n,seed);}}\n");
            var compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null || compiler.run(null, null, null, "--release", "17", "-proc:none", "-d",
                    output.toString(), source.toString(), bridge.toString()) != 0)
                throw new IOException("Cannot compile " + family + " " + variant);
            var loader = new URLClassLoader(new java.net.URL[] {output.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
            loaders.add(loader);
            return (IntBinaryOperator) loader.loadClass("Invocation").getConstructor().newInstance();
        }

        @TearDown(Level.Trial) @Override public void close() throws Exception {
            try {
                if (compiled != null) { requireCurrent(); MathCorpusRunner.requireUnchangedImplementation(implementation); }
            } finally { cleanup(); }
        }
        private void cleanup() throws IOException {
            try { for (var loader : loaders) loader.close(); }
            finally {
                loaders.clear();
                if (compiled != null) {
                    try (var files = Files.walk(compiled)) {
                        for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
                    } finally { compiled = null; }
                }
            }
        }
        private static Properties read(Path file) throws IOException {
            var properties = new Properties();
            try (var reader = Files.newBufferedReader(file)) { properties.load(reader); }
            return properties;
        }
    }

    @Benchmark public int original(Inputs input) {
        int i = input.cursor++ & 63; return input.original.applyAsInt(input.counts[i], input.seeds[i]);
    }
    @Benchmark public int generated(Inputs input) {
        int i = input.cursor++ & 63; return input.generated.applyAsInt(input.counts[i], input.seeds[i]);
    }
}
