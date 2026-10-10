/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;

import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.SplittableRandom;
import java.util.Set;
import javax.tools.ToolProvider;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;

/** Cleanup policy, distinct from the SDK's ability to evaluate constants internally. */
class RuntimeOnlyMathTest {
    @TempDir Path temporary;

    @Test void constantProductsAndNamedConstantExpressionsRemainUntouched() {
        for (String body : List.of("return 5 * 7 * 11 * 13 * 17 * 19 * 23 * 29;",
                "final int p = 5 * 7 * 11; return p;",
                "int p = 5 * 7 * 11; return p + 0;")) {
            String source = source(body);
            var result = analyze(source);
            assertFalse(result.changed(), result.diagnostics().toString());
            assertTrue(result.evidence().isEmpty());
        }
    }

    @Test void constantPropagationThroughLocalsIsNotPresentedAsRuntimeOptimization() {
        String source = source("int x = 9; int y = 10; int k = 8; int j = x * y + k * 11; return j;")
                .replace("int x,int y,int z", "int unused1,int unused2,int unused3");
        assertFalse(analyze(source).changed());
    }

    @Test void readableConstantStatementsAndNamesSurviveAnAdjacentRuntimeOptimization() throws Exception {
        String source = source("""
                final int MASK = (1 << 4) - 1; // Keep the derivation.
                int primes = 5 * 7 * 11;
                int chosen = (x & y) | (~x & z);
                return (chosen & MASK) ^ primes;
                """);
        String generated = rewrite(source);
        assertTrue(generated.contains("final int MASK = (1 << 4) - 1; // Keep the derivation."), generated);
        assertTrue(generated.contains("int primes = 5 * 7 * 11;"), generated);
        assertTrue(generated.substring(generated.indexOf("return")).contains("MASK"), generated);
        assertFalse(generated.contains("385"), generated);
        compare(source, generated);
    }

    @Test void chooseOnRuntimeInputsUsesTheRealVerifiedPipeline() throws Exception {
        String source = source("return (x & y) | (~x & z);");
        String generated = rewrite(source);
        assertFalse(generated.contains("~"), generated);
        assertFalse(generated.contains("if ("), generated);
        assertFalse(generated.contains("_math"), generated);
        compare(source, generated);
    }

    @Test void majorityOnRuntimeInputsRetainsJavaOverflowAndSignBits() throws Exception {
        String source = source("return (x & y) | (x & z) | (y & z);");
        compare(source, rewrite(source));
    }

    @Test void commonMaskIsFactoredAcrossSeveralOriginalStatements() throws Exception {
        String source = source("int first = x & z; int second = y & z; return first ^ second;");
        String generated = rewrite(source);
        assertFalse(generated.contains("int first"), generated);
        assertFalse(generated.contains("int second"), generated);
        compare(source, generated);
    }

    @Test void localLoopCalculationsAreOptimizedWithoutChangingTheLoop() throws Exception {
        String source = source("""
                int sum = 0;
                for (int i = 0; i < 4; i++) {
                    int selected = (x & y) | (~x & z);
                    sum += selected;
                    x++;
                }
                return sum;
                """);
        String generated = rewrite(source);
        assertTrue(generated.contains("for (int i = 0; i < 4; i++)"), generated);
        assertTrue(generated.contains("sum += selected;"), generated);
        assertTrue(generated.contains("x++;"), generated);
        compare(source, generated);
    }

    @Test void runtimeCancellationCanStillProduceAConstantResult() throws Exception {
        String source = source("return (x ^ y) ^ (x ^ y);");
        compare(source, rewrite(source));
    }

    @Test void unknownCallsAndArrayReadsAreNotDuplicatedOrRemoved() {
        String calls = """
                public class Calculation {
                    static int calls;
                    static int value() { return calls++; }
                    public static int compute(int x,int y,int z) {
                        return (value() & y) | (~value() & z);
                    }
                }
                """;
        String arrays = """
                public class Calculation {
                    public static int compute(int[] words,int i,int y,int z) {
                        return (words[i++] & y) | (~words[i++] & z);
                    }
                }
                """;
        assertFalse(analyze(calls).changed());
        assertFalse(analyze(arrays).changed());
    }

    private static String source(String body) {
        return "public class Calculation { public static int compute(int x,int y,int z) {\n" + body + "\n}}";
    }

    private static MathematicalAnalysis.Analysis analyze(String source) {
        return MathematicalAnalysis.analyze(MathTestSupport.parse(source), source,
                new MathCleanUpOptions(true, Set.of(NumericKind.INT, NumericKind.LONG), SafetyProfile.PRESERVE_JAVA,
                        OptimizationGoal.LOWER_ESTIMATED_RUNTIME, 1_000_000L, 20_000, false, 17, List.of()),
                new NullProgressMonitor(), -1, 0);
    }

    private String rewrite(String source) throws Exception {
        var result = analyze(source);
        assertTrue(result.changed(), result.diagnostics().toString());
        assertTrue(result.evidence().stream().allMatch(e -> e.cost().estimatedRuntimeImprovement()));
        Document document = new Document(source);
        var undo = result.newEdit().apply(document, TextEdit.CREATE_UNDO);
        String generated = document.get();
        undo.apply(document);
        assertEquals(source, document.get(), "Undo must preserve the complete original source");
        assertFalse(analyze(generated).changed(), generated);
        return generated;
    }

    private void compare(String original, String generated) throws Exception {
        Method before = compile(original), after = compile(generated);
        int[] edges = {0, 1, -1, Integer.MIN_VALUE, Integer.MAX_VALUE, 0x55555555, 0xaaaaaaaa};
        for (int x : edges) for (int y : edges) for (int z : edges)
            assertEquals(before.invoke(null, x, y, z), after.invoke(null, x, y, z));
        SplittableRandom random = new SplittableRandom(1657);
        for (int i = 0; i < 256; i++) {
            int x = random.nextInt(), y = random.nextInt(), z = random.nextInt();
            assertEquals(before.invoke(null, x, y, z), after.invoke(null, x, y, z));
        }
    }

    private Method compile(String source) throws Exception {
        Path directory = Files.createTempDirectory(temporary, "compiled-");
        Path file = directory.resolve("Calculation.java");
        Files.writeString(file, source);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "17", "-d", directory.toString(), file.toString()), source);
        try (var loader = new URLClassLoader(new java.net.URL[] {directory.toUri().toURL()}, null)) {
            return loader.loadClass("Calculation").getMethod("compute", int.class, int.class, int.class);
        }
    }
}
