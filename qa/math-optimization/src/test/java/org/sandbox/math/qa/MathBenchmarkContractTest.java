/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.benchmarks.MathematicsOptimizationBenchmark;
import org.sandbox.benchmarks.MathematicsOptimizationBenchmark.Inputs;

/** Checks the actual JMH setup/compiler, without making performance measurements. */
class MathBenchmarkContractTest {
    @TempDir Path temporary;

    @Test void compilesAndRunsBothJavaBodies() throws Exception {
        fixture("original", "seed + baseSeed");
        fixture("generated", "baseSeed + seed");
        try (Inputs inputs = new Inputs()) {
            inputs.fixtureDirectory = temporary.toString();
            inputs.setup();
            var benchmark = new MathematicsOptimizationBenchmark();
            inputs.cursor = 0;
            long[] original = benchmark.original(inputs);
            inputs.cursor = 0;
            assertArrayEquals(original, benchmark.generated(inputs));
            assertEquals(1, inputs.cursor);
        }
    }

    @Test void rejectsIncorrectGeneratedJavaBeforeMeasurement() throws Exception {
        fixture("original", "seed + baseSeed");
        fixture("generated", "seed + baseSeed + 1");
        try (Inputs inputs = new Inputs()) {
            inputs.fixtureDirectory = temporary.toString();
            IllegalStateException failure = assertThrows(IllegalStateException.class, inputs::setup);
            assertTrue(failure.getMessage().contains("fixture mismatch"), failure.getMessage());
        }
    }

    private void fixture(String variant, String expression) throws Exception {
        Path directory = Files.createDirectories(temporary.resolve(variant));
        Files.writeString(directory.resolve("Calculation.java"), "public class Calculation {\n"
                + "  public static long[] compute(long seed, long baseSeed) {\n"
                + "    return new long[] {" + expression + "};\n  }\n}\n");
    }
}
