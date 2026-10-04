/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.benchmarks;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import javax.tools.ToolProvider;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Measures actual emitted Java against the original Java. Run with {@code -prof gc}
 * and retain JSON raw results. Fixture generation/search/proof are not timed.
 * The local affine fixture is not a Bouncy Castle public-API performance claim.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
public class MathematicsOptimizationBenchmark {
    /** The only runtime contract added by the harness; no optimizer or interpreter is loaded. */
    public interface Computation {
        long[] compute(long seed, long baseSeed);
    }

    @State(Scope.Thread)
    public static class Inputs implements AutoCloseable {
        @Param({"qa/math-optimization/fixtures/local/small", "qa/math-optimization/fixtures/local/wide"})
        public String fixtureDirectory;
        public int cursor;
        private final long[] seeds = new long[64];
        private final long[] bases = new long[64];
        private final List<URLClassLoader> loaders = new ArrayList<>();
        private Path compiledDirectory;
        private Computation original;
        private Computation generated;

        @Setup(Level.Trial)
        public void setup() throws Exception {
            compiledDirectory = Files.createTempDirectory("math-jmh-");
            original = compile("original");
            generated = compile("generated");
            Random random = new Random(93802455L);
            for (int i = 0; i < seeds.length; i++) {
                seeds[i] = random.nextLong();
                bases[i] = random.nextLong();
            }
            seeds[0] = 0;
            bases[0] = -10;
            seeds[1] = 1;
            bases[1] = 0;
            for (int i = 0; i < seeds.length; i++) {
                if (!Arrays.equals(original.compute(seeds[i], bases[i]), generated.compute(seeds[i], bases[i])))
                    throw new IllegalStateException("Original/generated fixture mismatch at input " + i);
            }
            cursor = 0;
        }

        private Computation compile(String variant) throws Exception {
            Path sourceFile = Path.of(fixtureDirectory).resolve(variant).resolve("Calculation.java");
            String source = Files.readString(sourceFile);
            // Only the harness entry signature changes; the emitted numeric body is untouched.
            source = source.replace("public class Calculation {", "public class Calculation implements "
                    + MathematicsOptimizationBenchmark.class.getCanonicalName() + ".Computation {")
                    .replace("public static long[] compute(", "public long[] compute(");
            Path output = Files.createDirectories(compiledDirectory.resolve(variant));
            Path file = output.resolve("Calculation.java");
            Files.writeString(file, source);
            var compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) throw new IllegalStateException("The benchmark setup requires a JDK compiler");
            int status = compiler.run(null, null, null, "--release", "17", "-classpath",
                    System.getProperty("java.class.path"), "-d", output.toString(), file.toString());
            if (status != 0) throw new IllegalStateException("Cannot compile " + variant + " fixture; javac=" + status);
            var loader = new URLClassLoader(new java.net.URL[] {output.toUri().toURL()},
                    MathematicsOptimizationBenchmark.class.getClassLoader());
            loaders.add(loader);
            return (Computation) loader.loadClass("Calculation").getConstructor().newInstance();
        }

        @TearDown(Level.Trial)
        @Override public void close() throws Exception {
            for (var loader : loaders) loader.close();
            loaders.clear();
            if (compiledDirectory != null) {
                try (var paths = Files.walk(compiledDirectory)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
                }
                compiledDirectory = null;
            }
        }
    }

    @Benchmark
    public long[] original(Inputs inputs) {
        int index = inputs.cursor++ & 63;
        return inputs.original.compute(inputs.seeds[index], inputs.bases[index]);
    }

    @Benchmark
    public long[] generated(Inputs inputs) {
        int index = inputs.cursor++ & 63;
        return inputs.generated.compute(inputs.seeds[index], inputs.bases[index]);
    }
}
