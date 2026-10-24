/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;
import javax.tools.ToolProvider;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;

/** Generates the measured bodies through the real optimizer; no hand-written replacement. */
@SuppressWarnings("nls")
class MathSafetyProfileIntegrationTest {
    private static final String SOURCE = """
            public class Calculation {
                public static long[] compute(long seed, long baseSeed) {
                    long intermediate = seed + baseSeed;
                    long result = intermediate - baseSeed;
                    return new long[] {result};
                }
            }
            """;
    @TempDir Path temporary;

    @ParameterizedTest
    @EnumSource(value = SafetyProfile.class, names = {"GUARDED_FALLBACK", "CHECKED_THROW"})
    void generatedFixtureRetainsAllSafetyCostsAndOriginalOverflow(SafetyProfile safety) throws Exception {
        var options = options(safety, OptimizationGoal.READABILITY);
        long started = System.nanoTime();
        var analysis = MathematicalAnalysis.analyze(MathTestSupport.parse(SOURCE, 17), SOURCE,
                options, new NullProgressMonitor(), -1, 0);
        assertTrue(analysis.changed(), analysis.diagnostics().toString());
        assertEquals(1, analysis.evidence().size());
        assertTrue(analysis.evidence().getFirst().cost().checkWork() > 0);
        assertFalse(analysis.evidence().getFirst().cost().estimatedRuntimeImprovement());
        Document document = new Document(SOURCE);
        var undo = analysis.newEdit().apply(document, TextEdit.CREATE_UNDO);
        String generated = document.get();
        long elapsed = System.nanoTime() - started;
        undo.apply(document);
        assertEquals(SOURCE, document.get());

        var runtime = MathematicalAnalysis.analyze(MathTestSupport.parse(SOURCE, 17), SOURCE,
                options(safety, OptimizationGoal.LOWER_ESTIMATED_RUNTIME), new NullProgressMonitor(), -1, 0);
        assertFalse(runtime.changed(), "Safety-check costs cannot be omitted to claim a cheaper plan");
        assertTrue(runtime.diagnostics().stream().anyMatch(d ->
                d.message().contains("NO_IMPROVEMENT_WITH_FULL_POLICY_COST")), runtime.diagnostics().toString());

        Method before = compile(SOURCE), after = compile(generated);
        long[][] safe = {{0, 0}, {1, -2}, {Long.MAX_VALUE, 0}, {Long.MIN_VALUE, 0},
                {Long.MAX_VALUE / 2, Long.MAX_VALUE / 2}, {Long.MIN_VALUE / 2, Long.MIN_VALUE / 2}};
        for (long[] input : safe) {
            assertArrayEquals(new long[] {input[0]}, (long[]) before.invoke(null, input[0], input[1]));
            assertArrayEquals(new long[] {input[0]}, (long[]) after.invoke(null, input[0], input[1]));
        }
        for (long[] input : new long[][] {{Long.MAX_VALUE, 1}, {Long.MIN_VALUE, -1},
                {Long.MAX_VALUE - 17, 65_536}, {Long.MIN_VALUE + 17, -65_536}}) {
            assertArrayEquals(new long[] {input[0]}, (long[]) before.invoke(null, input[0], input[1]));
            if (safety == SafetyProfile.CHECKED_THROW) {
                var cause = assertThrows(InvocationTargetException.class,
                        () -> after.invoke(null, input[0], input[1])).getCause();
                assertEquals("MATH_OVERFLOW", assertInstanceOf(ArithmeticException.class, cause).getMessage());
            } else assertArrayEquals(new long[] {input[0]}, (long[]) after.invoke(null, input[0], input[1]));
        }
        String configured = System.getProperty("math.qa.safetyFixtureDirectory");
        if (configured != null && !configured.isBlank()) {
            Path root = Path.of(configured).resolve(safety == SafetyProfile.CHECKED_THROW ? "checked-long" : "guarded-long");
            writeNewOrIdentical(root.resolve("original/Calculation.java"), SOURCE);
            writeNewOrIdentical(root.resolve("generated/Calculation.java"), generated);
            writeNewOrIdentical(root.resolve("fixture.properties"), "schema=math-safety-fixture/v1\n"
                    + "computation=long-add-subtract\nsafety=" + safety + "\ngoal=READABILITY\ntargetJava=17\n");
            Path report = root.resolve("analysis.txt");
            if (!Files.exists(report)) Files.writeString(report,
                    "javaRuntime=" + System.getProperty("java.runtime.version") + "\n"
                    + "parseSearchVerifyEmitElapsedNanos=" + elapsed + "\n"
                    + "runtimeMeasurementsRun=false\nestimatedRuntimeImprovement=false\n"
                    + analysis.replacements().getFirst().description() + "\n" + analysis.evidence() + "\n",
                    StandardOpenOption.CREATE_NEW);
        }
    }

    private static MathCleanUpOptions options(SafetyProfile safety, OptimizationGoal goal) {
        return new MathCleanUpOptions(true, Set.of(NumericKind.LONG), safety, goal,
                100_000, 2_000, safety == SafetyProfile.CHECKED_THROW, 17, List.of());
    }

    private Method compile(String source) throws Exception {
        Path directory = Files.createTempDirectory(temporary, "profile-");
        Path file = directory.resolve("Calculation.java");
        Files.writeString(file, source, StandardCharsets.UTF_8);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "17",
                "-classpath", directory.toString(), "-d", directory.toString(), file.toString()));
        try (var loader = new URLClassLoader(new java.net.URL[] {directory.toUri().toURL()}, null)) {
            return loader.loadClass("Calculation").getMethod("compute", long.class, long.class);
        }
    }

    private static void writeNewOrIdentical(Path file, String content) throws Exception {
        Files.createDirectories(file.getParent());
        if (Files.exists(file)) assertEquals(content, Files.readString(file, StandardCharsets.UTF_8), "Refusing to overwrite fixture " + file);
        else Files.writeString(file, content, StandardOpenOption.CREATE_NEW);
    }
}
