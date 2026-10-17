/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;

import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import javax.tools.ToolProvider;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.JavaComputationExtractor;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;

/** Numeric rewrites never authorize moving, combining or omitting a source read. */
@Timeout(120)
class OrderedSourceReadComputationTest {
    @TempDir Path temporary;

    @Test void capturedArrayIndicesKeepTheirAuthoredConstantFormula() throws Exception {
        String source = arraySource("int left = first[Calculation.STRIDE * 2] & mask;"
                + " int right = second[(STRIDE << 1) + 1] & mask; return left ^ right;");
        String generated = rewrite(source);
        assertEquals(1, occurrences(generated, "first[Calculation.STRIDE * 2]"));
        assertEquals(1, occurrences(generated, "second[(STRIDE << 1) + 1]"));
        assertTrue(generated.contains("static final int STRIDE = 2 * 2;"));
        assertTrue(generated.indexOf("first[Calculation.STRIDE * 2]") < generated.indexOf("second[(STRIDE << 1) + 1]"));
        try (var oldCode = compile(source); var newCode = compile(generated)) {
            Method before = oldCode.loadClass("Calculation").getMethod("calculate", int[].class, int[].class, int.class);
            Method after = newCode.loadClass("Calculation").getMethod("calculate", int[].class, int[].class, int.class);
            int[] values = {0, 1, -1, Integer.MIN_VALUE, Integer.MAX_VALUE, 5, 6, 7, -8, 9};
            for (int mask : values) assertSameOutcome(before, after, values, values.clone(), mask);
            assertSameOutcome(before, after, null, new int[0], -1);
            assertSameOutcome(before, after, new int[0], null, -1);
        }
    }

    @Test void indexedBufferCapturesKeepReadOrderFormulaAndBufferState() throws Exception {
        String source = bufferSource("int left = first.get(Calculation.STRIDE * 2) & mask;"
                + " int right = second.get((STRIDE << 1) + 1) & mask; return left ^ right;");
        String generated = rewrite(source);
        assertEquals(1, occurrences(generated, "first.get(Calculation.STRIDE * 2)"));
        assertEquals(1, occurrences(generated, "second.get((STRIDE << 1) + 1)"));
        assertTrue(generated.indexOf("first.get(") < generated.indexOf("second.get("));
        try (var oldCode = compile(source); var newCode = compile(generated)) {
            Method before = oldCode.loadClass("Calculation").getMethod("calculate", IntBuffer.class, IntBuffer.class, int.class);
            Method after = newCode.loadClass("Calculation").getMethod("calculate", IntBuffer.class, IntBuffer.class, int.class);
            IntBuffer writable = IntBuffer.wrap(new int[] {0, 1, -1, 3, 4, 5, 6, 7, Integer.MIN_VALUE, Integer.MAX_VALUE});
            writable.position(3).mark();
            IntBuffer readOnly = writable.asReadOnlyBuffer();
            for (int mask : new int[] {0, -1, 0x55555555, Integer.MIN_VALUE}) {
                assertSameOutcome(before, after, writable, readOnly, mask);
                assertEquals(3, writable.position());
                assertEquals(3, readOnly.position());
                assertEquals(10, writable.limit());
                assertEquals(10, readOnly.limit());
                writable.reset();
            }
            assertSameOutcome(before, after, null, IntBuffer.allocate(0), -1);
            assertSameOutcome(before, after, IntBuffer.allocate(0), null, -1);
            IntBuffer limited = writable.duplicate().limit(8);
            assertSameOutcome(before, after, limited, readOnly, -1);
            IntBuffer direct = java.nio.ByteBuffer.allocateDirect(40).asIntBuffer();
            for (int i = 0; i < 10; i++) direct.put(i, i * 17);
            assertSameOutcome(before, after, direct, direct.asReadOnlyBuffer(), -1);
        }
    }

    @Test void finalPrimitiveFieldsAreCapturedWithoutAssumingEqualReads() throws Exception {
        String source = "public class Calculation { public final int left, right;"
                + " public Calculation(int left, int right) { this.left=left; this.right=right; }"
                + " public static int calculate(Calculation owner, int mask) {"
                + " int a=owner.left & mask; int b=owner.right & mask; return a ^ b; }}";
        String generated = rewrite(source);
        assertEquals(1, occurrences(generated, "owner.left"));
        assertEquals(1, occurrences(generated, "owner.right"));
        assertTrue(generated.indexOf("owner.left") < generated.indexOf("owner.right"));
        try (var oldCode = compile(source); var newCode = compile(generated)) {
            Class<?> oldType = oldCode.loadClass("Calculation"), newType = newCode.loadClass("Calculation");
            Method before = oldType.getMethod("calculate", oldType, int.class);
            Method after = newType.getMethod("calculate", newType, int.class);
            for (int value : new int[] {0, -1, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                Object first = oldType.getConstructor(int.class, int.class).newInstance(value, ~value);
                Object second = newType.getConstructor(int.class, int.class).newInstance(value, ~value);
                assertEquals(outcome(before, first, 0x55555555), outcome(after, second, 0x55555555));
            }
            assertEquals(NullPointerException.class, outcome(before, null, -1));
            assertEquals(NullPointerException.class, outcome(after, null, -1));
        }
    }

    @Test void computedFinalStaticCoefficientsDoNotRequireMathematicalOptIn() throws Exception {
        String source = "public class Calculation { static final int COEFFICIENT = Integer.parseInt(\"9\");"
                + " public static int calculate(int value) { int coefficient = COEFFICIENT;"
                + " return ((coefficient + value) + 1) - 1; }}";
        String generated = rewrite(source);
        assertTrue(generated.contains("static final int COEFFICIENT = Integer.parseInt(\"9\");"));
        try (var oldCode = compile(source); var newCode = compile(generated)) {
            Method before = oldCode.loadClass("Calculation").getMethod("calculate", int.class);
            Method after = newCode.loadClass("Calculation").getMethod("calculate", int.class);
            for (int value : new int[] {0, 1, -1, Integer.MIN_VALUE, Integer.MAX_VALUE})
                assertSameOutcome(before, after, value);
        }
    }

    @Test void deadAndRepeatedBufferReadsStillExecuteExactlyOncePerOccurrence() throws Exception {
        String source = bufferSource("int unused = first.get(0); int left = second.get(0) & mask;"
                + " int right = second.get(0) & mask; return left ^ right;");
        var extraction = new JavaComputationExtractor().extractRuntime(MathTestSupport.parse(source), source, options());
        assertEquals(1, extraction.regions().size(), extraction.toString());
        // Three occurrences must remain three inputs, even for the same receiver and index.
        assertEquals(4, extraction.regions().getFirst().plan().inputs().size());
        String generated = rewrite(source);
        assertEquals(1, occurrences(generated, "first.get(0)"));
        assertEquals(2, occurrences(generated, "second.get(0)"));
        try (var oldCode = compile(source); var newCode = compile(generated)) {
            Method before = oldCode.loadClass("Calculation").getMethod("calculate", IntBuffer.class, IntBuffer.class, int.class);
            Method after = newCode.loadClass("Calculation").getMethod("calculate", IntBuffer.class, IntBuffer.class, int.class);
            assertSameOutcome(before, after, null, IntBuffer.allocate(0), -1);
            assertSameOutcome(before, after, IntBuffer.allocate(0), null, -1);
            assertSameOutcome(before, after, IntBuffer.wrap(new int[] {3}), IntBuffer.wrap(new int[] {-1}), 7);
        }
    }

    @Test void unsupportedIndexEffectsAndRelativeGetsRemainBoundaries() {
        for (String expression : List.of("first.get()", "first.get(next())", "first.get(INDEX)",
                "first.get(mask++)", "first.get(8 / mask)")) {
            String source = bufferSource("return (" + expression + ") + 0;")
                    .replace("public class Calculation {", "public class Calculation { static volatile int INDEX;"
                            + " static int next() { return 8; }");
            var result = MathematicalAnalysis.analyze(MathTestSupport.parse(source), source, options(), new NullProgressMonitor(), -1, 0);
            assertFalse(result.changed(), expression + ": " + result.diagnostics());
        }
    }

    @Test void checkedAndFallbackProfilesDoNotAdmitOpaqueBufferCaptures() {
        String source = bufferSource("return (first.get(0) + 1) - 1;");
        for (SafetyProfile safety : List.of(SafetyProfile.CHECKED_THROW, SafetyProfile.GUARDED_FALLBACK)) {
            var options = new MathCleanUpOptions(true, EnumSet.of(NumericKind.INT), safety,
                    OptimizationGoal.READABILITY, 1_000_000, 2000, safety == SafetyProfile.CHECKED_THROW, 17, List.of());
            var extraction = new JavaComputationExtractor().extractRuntime(MathTestSupport.parse(source), source, options);
            assertTrue(extraction.regions().isEmpty(), extraction.toString());
        }
    }

    private static String arraySource(String body) {
        return "public class Calculation { static final int STRIDE = 2 * 2;"
                + " public static int calculate(int[] first, int[] second, int mask) {" + body + "}}";
    }
    private static String bufferSource(String body) {
        return "import java.nio.IntBuffer; public class Calculation { static final int STRIDE = 2 * 2;"
                + " public static int calculate(IntBuffer first, IntBuffer second, int mask) {" + body + "}}";
    }
    private static MathCleanUpOptions options() {
        return new MathCleanUpOptions(true, EnumSet.of(NumericKind.INT, NumericKind.LONG, NumericKind.BYTE,
                NumericKind.SHORT, NumericKind.CHAR), SafetyProfile.PRESERVE_JAVA,
                OptimizationGoal.READABILITY, 1_000_000, 2000, false, 17, List.of());
    }
    private static String rewrite(String source) throws Exception {
        var result = MathematicalAnalysis.analyze(MathTestSupport.parse(source), source, options(), new NullProgressMonitor(), -1, 0);
        assertTrue(result.changed(), result.diagnostics().toString());
        assertEquals(result.replacements().size(), result.evidence().size());
        result.evidence().forEach(e -> assertNotNull(e.proof()));
        var document = new Document(source);
        var undo = result.newEdit().apply(document, TextEdit.CREATE_UNDO);
        String generated = document.get();
        assertNotEquals(source, generated);
        undo.apply(document);
        assertEquals(source, document.get());
        return generated;
    }
    private URLClassLoader compile(String source) throws Exception {
        Path root = Files.createTempDirectory(temporary, "source-");
        Path input = root.resolve("Calculation.java");
        Files.writeString(input, source, StandardCharsets.UTF_8);
        var compiler = Objects.requireNonNull(ToolProvider.getSystemJavaCompiler(), "A complete JDK is required");
        assertEquals(0, compiler.run(null, null, null, "--release", "17", "-proc:none", "-d", root.toString(), input.toString()));
        return new URLClassLoader(new URL[] {root.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    }
    private static void assertSameOutcome(Method before, Method after, Object... values) throws Exception {
        assertEquals(outcome(before, values), outcome(after, values));
    }
    private static Object outcome(Method method, Object... arguments) throws Exception {
        try { return method.invoke(null, arguments); }
        catch (InvocationTargetException failure) { return failure.getCause().getClass(); }
    }
    private static int occurrences(String text, String token) { return text.split(java.util.regex.Pattern.quote(token), -1).length - 1; }
}
