/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.benchmarks;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Properties;
import java.util.Set;
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
        @Param({"default"})
        public String inputScenario = "default";
        public int cursor;
        private final long[] seeds = new long[64];
        private final long[] bases = new long[64];
        private final Random random = new Random(93802455L);
        private final List<URLClassLoader> loaders = new ArrayList<>();
        private Path compiledDirectory;
        private Computation original;
        private Computation generated;
        private boolean checked;

        @Setup(Level.Trial)
        public void setup() throws Exception {
            validateMetadata();
            compiledDirectory = Files.createTempDirectory("math-jmh-");
            original = compile("original");
            generated = compile("generated");
            random.setSeed(93802455L);
            for (int i = 0; i < seeds.length; i++) {
                seeds[i] = random.nextLong();
                bases[i] = random.nextLong();
                if (inputScenario.equals("safe")) {
                    seeds[i] >>= 2;
                    bases[i] >>= 2;
                } else if (inputScenario.equals("overflow")) {
                    long offset = seeds[i] & 65535L;
                    long increment = 65536L + (bases[i] & 65535L);
                    seeds[i] = (i & 1) == 0 ? Long.MAX_VALUE - offset : Long.MIN_VALUE + offset;
                    bases[i] = (i & 1) == 0 ? increment : -increment;
                }
            }
            if (inputScenario.equals("overflow")) {
                seeds[0] = Long.MAX_VALUE; bases[0] = 1;
                seeds[1] = Long.MIN_VALUE; bases[1] = -1;
            } else {
                seeds[0] = 0; bases[0] = -10;
                seeds[1] = 1; bases[1] = 0;
                if (inputScenario.equals("safe")) {
                    seeds[2] = Long.MAX_VALUE; bases[2] = 0;
                    seeds[3] = Long.MIN_VALUE; bases[3] = 0;
                }
            }
            for (int i = 0; i < seeds.length; i++) {
                Object before = outcome(original, i), after = outcome(generated, i);
                if (inputScenario.equals("default")) {
                    if (!(before instanceof long[] a && after instanceof long[] b && Arrays.equals(a, b)))
                        throw new IllegalStateException("Original/generated fixture mismatch at input " + i);
                } else {
                    boolean overflow;
                    try { Math.addExact(seeds[i], bases[i]); overflow = false; }
                    catch (ArithmeticException expected) { overflow = true; }
                    if (overflow != inputScenario.equals("overflow"))
                        throw new IllegalStateException("Input dataset does not match its arithmetic contract");
                    requireValue(before, seeds[i]);
                    if (checked && overflow) {
                        if (!(after instanceof ArithmeticException exception)
                                || after.getClass() != ArithmeticException.class
                                || !"MATH_OVERFLOW".equals(exception.getMessage()))
                            throw new IllegalStateException("Missing or incorrect checked overflow at input " + i);
                    } else requireValue(after, seeds[i]);
                }
            }
            cursor = 0;
        }

        private void validateMetadata() throws Exception {
            if (!Set.of("default", "safe", "overflow").contains(inputScenario))
                throw new IllegalArgumentException("Unknown input scenario: " + inputScenario);
            Path metadata = Path.of(fixtureDirectory).resolve("fixture.properties");
            if (inputScenario.equals("default")) {
                if (Files.exists(metadata)) throw new IllegalArgumentException("Safety fixtures require an explicit input scenario");
                return;
            }
            if (!Files.isRegularFile(metadata)) throw new IllegalArgumentException("Missing safety fixture metadata");
            Properties properties = new Properties();
            try (var reader = Files.newBufferedReader(metadata, StandardCharsets.UTF_8)) { properties.load(reader); }
            if (!"math-safety-fixture/v1".equals(properties.getProperty("schema"))
                    || !"long-add-subtract".equals(properties.getProperty("computation"))
                    || !"READABILITY".equals(properties.getProperty("goal"))
                    || !"17".equals(properties.getProperty("targetJava")))
                throw new IllegalArgumentException("Unknown safety fixture contract");
            String safety = properties.getProperty("safety", "");
            if (!Set.of("GUARDED_FALLBACK", "CHECKED_THROW").contains(safety))
                throw new IllegalArgumentException("Unknown safety profile: " + safety);
            checked = safety.equals("CHECKED_THROW");
        }

        private Object outcome(Computation computation, int index) {
            try { return computation.compute(seeds[index], bases[index]); }
            catch (RuntimeException exception) { return exception; }
        }

        private static void requireValue(Object result, long expected) {
            if (!(result instanceof long[] values) || values.length != 1 || values[0] != expected)
                throw new IllegalStateException("Fixture violates the independent long-add-subtract value contract");
        }

        private Computation compile(String variant) throws Exception {
            Path sourceFile = Path.of(fixtureDirectory).resolve(variant).resolve("Calculation.java");
            String source = Files.readString(sourceFile, StandardCharsets.UTF_8);
            // Only the harness entry signature changes; the emitted numeric body is untouched.
            source = source.replace("public class Calculation {", "public class Calculation implements "
                    + MathematicsOptimizationBenchmark.class.getCanonicalName() + ".Computation {")
                    .replace("public static long[] compute(", "public long[] compute(");
            Path output = Files.createDirectories(compiledDirectory.resolve(variant));
            Path file = output.resolve("Calculation.java");
            Files.writeString(file, source, StandardCharsets.UTF_8);
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
    public Object original(Inputs inputs) {
        int index = inputs.cursor++ & 63;
        try { return inputs.original.compute(inputs.seeds[index], inputs.bases[index]); }
        catch (ArithmeticException exception) { return exception; }
    }

    @Benchmark
    public Object generated(Inputs inputs) {
        int index = inputs.cursor++ & 63;
        try { return inputs.generated.compute(inputs.seeds[index], inputs.bases[index]); }
        catch (ArithmeticException exception) { return exception; }
    }
}
