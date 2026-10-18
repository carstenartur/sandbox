/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;

import de.regelsuche.sdk.optimization.ComputationOptimizer;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.tools.ToolProvider;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.JavaComputationExtractor;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;

/** Incoming compound assignments keep the old local value, narrowing and the write boundary. */
class IncomingCompoundAssignmentTest {
    @TempDir Path temporary;

    @Test void everyIntegralCompoundOperatorKeepsPromotionNarrowingAndWraparound() {
        for (NumericKind kind : List.of(NumericKind.BYTE, NumericKind.SHORT, NumericKind.CHAR, NumericKind.INT, NumericKind.LONG)) {
            for (String operator : List.of("+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<=", ">>=", ">>>=")) {
                String statement = "value " + operator + " right;";
                String source = "class Calculation { long compute(" + kind.name().toLowerCase(java.util.Locale.ROOT)
                        + " value, int right) { " + statement + " return value; }}";
                var extraction = new JavaComputationExtractor().extractRuntime(MathTestSupport.parse(source), source, options());
                var region = extraction.regions().stream().filter(r -> r.start() == source.indexOf(statement))
                        .findFirst().orElseThrow(() -> new AssertionError(kind + " " + operator + ": " + extraction));
                assertEquals(statement.length(), region.length(), "An incoming write must stay a singleton region");
                assertEquals(1, region.outputs().size());
                assertFalse(region.outputs().getFirst().declaration());
                assertFalse(region.outputs().getFirst().returnValue());
                String valueId = region.inputNames().entrySet().stream().filter(e -> e.getValue().equals("value"))
                        .findFirst().orElseThrow().getKey();
                assertEquals(kind.type(), region.plan().inputs().get(valueId));
                var prepared = ComputationOptimizer.prepare(region.plan());
                for (long edge : new long[] {Long.MIN_VALUE, Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE, Long.MAX_VALUE}) {
                    Object oldValue = narrow(kind, edge);
                    for (int right : new int[] {-1, 1, 2, 17, 32, 65}) {
                        Map<String, Object> inputs = new HashMap<>();
                        region.inputNames().forEach((id, name) -> inputs.put(id, name.equals("value") ? oldValue : Integer.valueOf(right)));
                        Object actual = prepared.execute(inputs).get(region.outputs().getFirst().id());
                        assertEquals(number(narrow(kind, oracle(kind, number(oldValue), right, operator))), number(actual),
                                kind + " " + edge + " " + operator + " " + right);
                    }
                }
            }
        }
    }

    @Test void incomingXorCarriesTheOldValueAndTheActualFieldReadAsDistinctInputs() {
        String source = "class Calculation { static long[] ROUND; long compute(long value, int index) { "
                + "value ^= ROUND[index]; return value; }}";
        var extraction = new JavaComputationExtractor().extractRuntime(MathTestSupport.parse(source), source, options());
        var region = extraction.regions().stream().filter(r -> r.start() == source.indexOf("value ^="))
                .findFirst().orElseThrow(() -> new AssertionError(extraction));
        assertEquals(1, region.arrayReads().size());
        assertEquals(2, region.plan().inputs().size());
        assertTrue(region.inputNames().containsValue("value"));
        assertEquals("ROUND[index]", source.substring(region.arrayReads().getFirst().start(),
                region.arrayReads().getFirst().start() + region.arrayReads().getFirst().length()));
    }

    @Test void aRealRewritePreservesTheOldValueWhenTheCompoundRightSideThrows() throws Exception {
        String source = "public class Calculation { static int[] words; "
                + "public static int compute(int value, int[] left, int[] right, int mask) { words = left; "
                + "try { value ^= (words[0] & mask) ^ (right[0] & mask); return value; } "
                + "catch (RuntimeException failure) { return value; } }}";
        var analysis = MathematicalAnalysis.analyze(MathTestSupport.parse(source), source, options(), new NullProgressMonitor(), -1, 0);
        assertTrue(analysis.changed(), analysis.diagnostics().toString());
        assertTrue(analysis.evidence().stream().anyMatch(e -> e.original().contains("value ^=")), analysis.diagnostics().toString());
        assertEquals(analysis.replacements().size(), analysis.evidence().size());
        analysis.evidence().forEach(e -> assertNotNull(e.proof()));
        var document = new Document(source);
        var undo = analysis.newEdit().apply(document, TextEdit.CREATE_UNDO);
        String generated = document.get();
        undo.apply(document);
        assertEquals(source, document.get());
        MathTestSupport.parse(generated);
        try (var before = compile(source); var after = compile(generated)) {
            for (int old : new int[] {Integer.MIN_VALUE, -1, 0, 19, Integer.MAX_VALUE}) {
                assertEquals(old ^ 31 ^ -7, invoke(before, old, new int[] {31}, new int[] {-7}, -1));
                assertEquals(invoke(before, old, new int[] {31}, new int[] {-7}, -1),
                        invoke(after, old, new int[] {31}, new int[] {-7}, -1));
                assertEquals(old, invoke(after, old, null, new int[0], -1));
                assertEquals(invoke(before, old, null, new int[0], -1), invoke(after, old, null, new int[0], -1));
                assertEquals(old, invoke(after, old, new int[] {31}, null, -1));
                assertEquals(invoke(before, old, new int[] {31}, new int[0], -1),
                        invoke(after, old, new int[] {31}, new int[0], -1));
            }
        }
    }

    @Test void unsupportedWriteTargetsEffectsAndProfilesStayOutsideIncomingCompoundAdmission() {
        for (String statement : List.of("field ^= words[0];", "words[0] ^= value;", "value += (value = 1);",
                "floating += value;", "value += floating;", "value ^= words[index++];")) {
            String source = "class Calculation { int field; int compute(int value, int[] words, int index, double floating) { "
                    + statement + " return 0; }}";
            var extraction = new JavaComputationExtractor().extractRuntime(MathTestSupport.parse(source), source, options());
            assertTrue(extraction.regions().stream().noneMatch(r -> r.start() == source.indexOf(statement)), statement + ": " + extraction);
        }
        String source = "class Calculation { int compute(int value, int right) { value ^= right; return value; }}";
        for (SafetyProfile safety : List.of(SafetyProfile.CHECKED_THROW, SafetyProfile.GUARDED_FALLBACK)) {
            var values = new HashMap<>(options().toMap());
            values.put(MathCleanUpOptions.SAFETY, safety.name());
            values.put(MathCleanUpOptions.CHECKED_OPT_IN, "true");
            var extraction = new JavaComputationExtractor().extractRuntime(MathTestSupport.parse(source), source,
                    MathCleanUpOptions.parse(values, 17));
            assertTrue(extraction.regions().stream().noneMatch(r -> r.start() == source.indexOf("value ^=")), safety + ": " + extraction);
        }
    }

    @Test void authoredConstantFormulasRemainProtectedButLiteralOperandsAreRuntimeWork() {
        String source = "class Calculation { int compute(int value) { value ^= (32 - 11); return value; }}";
        var extraction = new JavaComputationExtractor().extractRuntime(MathTestSupport.parse(source), source, options());
        assertTrue(extraction.regions().stream().noneMatch(r -> r.start() == source.indexOf("value ^=")), extraction.toString());
        String literal = source.replace("(32 - 11)", "21");
        var allowed = new JavaComputationExtractor().extractRuntime(MathTestSupport.parse(literal), literal, options());
        assertTrue(allowed.regions().stream().anyMatch(r -> r.start() == literal.indexOf("value ^=")), allowed.toString());
    }

    private static MathCleanUpOptions options() {
        return new MathCleanUpOptions(true, EnumSet.of(NumericKind.BYTE, NumericKind.SHORT, NumericKind.CHAR, NumericKind.INT, NumericKind.LONG),
                SafetyProfile.PRESERVE_JAVA, OptimizationGoal.READABILITY, 1_000_000, 20_000, false, 17, List.of());
    }
    private static Object narrow(NumericKind kind, long value) {
        return switch (kind) {
            case BYTE -> Byte.valueOf((byte) value); case SHORT -> Short.valueOf((short) value);
            case CHAR -> Character.valueOf((char) value); case INT -> Integer.valueOf((int) value);
            case LONG -> Long.valueOf(value); default -> throw new IllegalArgumentException(kind.name());
        };
    }
    private static long number(Object value) {
        return value instanceof Character character ? character.charValue() : ((Number) value).longValue();
    }
    private static long oracle(NumericKind kind, long value, int right, String operator) {
        if (kind == NumericKind.LONG) return switch (operator) {
            case "+=" -> value + right; case "-=" -> value - right; case "*=" -> value * right;
            case "/=" -> value / right; case "%=" -> value % right; case "&=" -> value & right;
            case "|=" -> value | right; case "^=" -> value ^ right; case "<<=" -> value << right;
            case ">>=" -> value >> right; case ">>>=" -> value >>> right; default -> throw new IllegalArgumentException(operator);
        };
        int left = (int) value;
        // Java promotes these operands to int; overflow must happen before widening.
        int result = switch (operator) {
            case "+=" -> left + right; case "-=" -> left - right; case "*=" -> left * right;
            case "/=" -> left / right; case "%=" -> left % right; case "&=" -> left & right;
            case "|=" -> left | right; case "^=" -> left ^ right; case "<<=" -> left << right;
            case ">>=" -> left >> right; case ">>>=" -> left >>> right; default -> throw new IllegalArgumentException(operator);
        };
        return result;
    }
    private URLClassLoader compile(String source) throws Exception {
        Path directory = Files.createTempDirectory(temporary, "incoming-compound-");
        Path file = directory.resolve("Calculation.java");
        Files.writeString(file, source, StandardCharsets.UTF_8);
        var compiler = Objects.requireNonNull(ToolProvider.getSystemJavaCompiler(), "A JDK is required");
        assertEquals(0, compiler.run(null, null, null, "--release", "17", "-proc:none", "-d", directory.toString(), file.toString()));
        return new URLClassLoader(new URL[] {directory.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    }
    private static Object invoke(ClassLoader loader, int value, int[] left, int[] right, int mask) throws Exception {
        try {
            return loader.loadClass("Calculation").getMethod("compute", int.class, int[].class, int[].class, int.class)
                    .invoke(null, value, left, right, mask);
        } catch (InvocationTargetException failure) { return Objects.requireNonNull(failure.getCause()).getClass(); }
    }
}
