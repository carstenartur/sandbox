/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openjdk.jmh.annotations.Param;
import org.sandbox.benchmarks.MathematicsOptimizationBenchmark;
import org.sandbox.benchmarks.MathematicsOptimizationBenchmark.Inputs;

/** Checks the actual JMH compiler/input/outcome contract, without timing measurements. */
class MathBenchmarkContractTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @MethodSource("defaultFixtureDirectories")
    void registeredDefaultFixturesCompileAndExecute(String fixtureDirectory) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("qa/math-optimization"))) root = root.getParent();
        assertNotNull(root, "Run from the repository or a Maven module");
        try (Inputs inputs = new Inputs()) {
            inputs.fixtureDirectory = root.resolve(fixtureDirectory).toString();
            inputs.setup();
            var benchmark = new MathematicsOptimizationBenchmark();
            for (int i = 0; i < 64; i++) {
                inputs.cursor = i;
                long[] before = assertInstanceOf(long[].class, benchmark.original(inputs));
                inputs.cursor = i;
                assertArrayEquals(before, assertInstanceOf(long[].class, benchmark.generated(inputs)));
            }
        }
    }

    private static Stream<String> defaultFixtureDirectories() throws Exception {
        return Stream.of(Inputs.class.getField("fixtureDirectory").getAnnotation(Param.class).value());
    }

    @Test void compilesAndRunsBothJavaBodies() throws Exception {
        fixture("original", "return new long[] {seed + baseSeed};");
        fixture("generated", "return new long[] {baseSeed + seed};");
        try (Inputs inputs = inputs("default")) {
            inputs.setup();
            var benchmark = new MathematicsOptimizationBenchmark();
            Object original = benchmark.original(inputs);
            inputs.cursor = 0;
            assertArrayEquals(assertInstanceOf(long[].class, original),
                    assertInstanceOf(long[].class, benchmark.generated(inputs)));
            assertEquals(1, inputs.cursor);
        }
    }

    @Test void rejectsIncorrectGeneratedJavaBeforeMeasurement() throws Exception {
        fixture("original", "return new long[] {seed + baseSeed};");
        fixture("generated", "return new long[] {seed + baseSeed + 1};");
        try (Inputs inputs = inputs("default")) {
            assertThrows(IllegalStateException.class, inputs::setup);
        }
    }

    @Test void guardedOverflowReturnsTheOriginalWrappedValue() throws Exception {
        cancellation();
        fixture("generated", "return new long[] {seed};");
        metadata("GUARDED_FALLBACK");
        try (Inputs inputs = inputs("overflow")) {
            inputs.setup();
            var benchmark = new MathematicsOptimizationBenchmark();
            assertArrayEquals(new long[] {Long.MAX_VALUE}, assertInstanceOf(long[].class, benchmark.original(inputs)));
            inputs.cursor = 0;
            assertArrayEquals(new long[] {Long.MAX_VALUE}, assertInstanceOf(long[].class, benchmark.generated(inputs)));
            assertEquals(1, inputs.cursor);
        }
    }

    @Test void checkedOverflowMeasuresTheActualExceptionObject() throws Exception {
        cancellation();
        fixture("generated", """
                try { Math.addExact(seed, baseSeed); }
                catch (ArithmeticException overflow) { throw new ArithmeticException("MATH_OVERFLOW"); }
                return new long[] {seed};
                """);
        metadata("CHECKED_THROW");
        try (Inputs inputs = inputs("overflow")) {
            inputs.setup();
            var benchmark = new MathematicsOptimizationBenchmark();
            assertArrayEquals(new long[] {Long.MAX_VALUE}, assertInstanceOf(long[].class, benchmark.original(inputs)));
            inputs.cursor = 0;
            var observed = assertInstanceOf(ArithmeticException.class, benchmark.generated(inputs));
            assertEquals("MATH_OVERFLOW", observed.getMessage());
            assertEquals(1, inputs.cursor);
        }
    }

    @Test void rejectsMatchingButWrongBodiesWithAnIndependentOracle() throws Exception {
        fixture("original", "return new long[] {seed + 1};");
        fixture("generated", "return new long[] {seed + 1};");
        metadata("GUARDED_FALLBACK");
        try (Inputs inputs = inputs("safe")) {
            assertThrows(IllegalStateException.class, inputs::setup);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"return new long[] {seed};", "throw new ArithmeticException(\"WRONG_CODE\");",
            "throw new IllegalArgumentException(\"MATH_OVERFLOW\");", "return null;"})
    void rejectsIncorrectCheckedOverflowOutcomes(String body) throws Exception {
        cancellation();
        fixture("generated", body);
        metadata("CHECKED_THROW");
        try (Inputs inputs = inputs("overflow")) {
            assertThrows(IllegalStateException.class, inputs::setup);
        }
    }

    @Test void rejectsUnexpectedExceptionsOnSafeInputs() throws Exception {
        cancellation();
        fixture("generated", "throw new ArithmeticException(\"MATH_OVERFLOW\");");
        metadata("CHECKED_THROW");
        try (Inputs inputs = inputs("safe")) {
            assertThrows(IllegalStateException.class, inputs::setup);
        }
    }

    @Test void requiresKnownScenarioAndMatchingFixtureMetadata() throws Exception {
        cancellation();
        fixture("generated", "return new long[] {seed};");
        try (Inputs inputs = inputs("unknown")) {
            assertThrows(IllegalArgumentException.class, inputs::setup);
        }
        try (Inputs inputs = inputs("safe")) {
            assertThrows(IllegalArgumentException.class, inputs::setup);
        }
        metadata("UNKNOWN");
        try (Inputs inputs = inputs("safe")) {
            assertThrows(IllegalArgumentException.class, inputs::setup);
        }
    }

    private Inputs inputs(String scenario) {
        Inputs inputs = new Inputs();
        inputs.fixtureDirectory = temporary.toString();
        inputs.inputScenario = scenario;
        return inputs;
    }

    private void cancellation() throws Exception {
        fixture("original", "return new long[] {(seed + baseSeed) - baseSeed};");
    }

    private void metadata(String profile) throws Exception {
        Files.writeString(temporary.resolve("fixture.properties"),
                "schema=math-safety-fixture/v1\ncomputation=long-add-subtract\nsafety=" + profile
                + "\ngoal=READABILITY\ntargetJava=17\n");
    }

    private void fixture(String variant, String body) throws Exception {
        Path directory = Files.createDirectories(temporary.resolve(variant));
        Files.writeString(directory.resolve("Calculation.java"), "public class Calculation {\n"
                + "  public static long[] compute(long seed, long baseSeed) {\n" + body + "\n  }\n}\n");
    }
}
