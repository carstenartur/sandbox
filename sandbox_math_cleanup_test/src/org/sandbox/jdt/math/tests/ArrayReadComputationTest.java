/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;

import de.regelsuche.sdk.optimization.JavaExpressions;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.NumericOperation;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.tools.ToolProvider;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.JavaComputationExtractor;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;

/** Memory reads are ordered source obligations, not freely reusable numerical expressions. */
class ArrayReadComputationTest {
    @TempDir Path temporary;

    @Test void arrayInputsConnectMasksAcrossStatements() {
        String source = source("int a = words[i] & mask; int b = other[j] & mask; return a ^ b;");
        var extraction = extract(source);
        assertEquals(1, extraction.regions().size(), extraction.diagnostics().toString());
        var region = extraction.regions().getFirst();
        assertEquals(source.indexOf("int a ="), region.start());
        assertEquals(3, region.plan().inputs().size());
        assertFalse(region.inputNames().containsValue("a"));
        assertFalse(region.inputNames().containsValue("b"));
        assertTrue(region.inputNames().values().stream().allMatch(n -> n.matches("[A-Za-z_$][A-Za-z0-9_$]*")));
        assertEquals(2, region.trace().occurrences().stream()
                .filter(o -> JavaExpressions.operationOf(o.expression()).orElse(null) == NumericOperation.AND).count());
    }

    @Test void generatedMaskFactoringPreservesReadOrderAndExceptions() throws Exception {
        String source = source("int a = words[i] & mask; int b = other[j] & mask; return a ^ b;");
        String generated = rewrite(source);
        assertFalse(generated.contains("int a ="), generated);
        assertFalse(generated.contains("int b ="), generated);
        assertEquals(1, occurrences(generated, "words[i]"));
        assertEquals(1, occurrences(generated, "other[j]"));
        assertTrue(generated.indexOf("words[i]") < generated.indexOf("other[j]"), generated);
        var second = MathematicalAnalysis.analyze(MathTestSupport.parse(generated), generated, options(SafetyProfile.PRESERVE_JAVA),
                new NullProgressMonitor(), -1, 0);
        assertFalse(second.changed(), second.diagnostics().toString());
        try (var before = compile(source); var after = compile(generated)) {
            compare(before, after, new int[] {Integer.MIN_VALUE, 17}, new int[] {-1, 42}, 0, 1, 0x55555555, 0);
            assertEquals(NullPointerException.class, compare(before, after, null, new int[0], 0, 0, -1, 0));
            assertEquals(ArrayIndexOutOfBoundsException.class, compare(before, after, new int[0], null, 0, 0, -1, 0));
            assertEquals(ArrayIndexOutOfBoundsException.class, compare(before, after, new int[] {4}, new int[] {7}, -1, 0, -1, 0));
        }
    }

    @Test void eliminatedReadValuesStillExecuteExactlyOnce() throws Exception {
        String source = source("int unused = words[i] & mask; return (value + 1) - 1;");
        String generated = rewrite(source);
        assertEquals(1, occurrences(generated, "words[i]"), generated);
        assertFalse(generated.contains("int unused"), generated);
        try (var before = compile(source); var after = compile(generated)) {
            assertEquals(19L, compare(before, after, new int[] {5}, null, 0, 0, -1, 19));
            assertEquals(NullPointerException.class, compare(before, after, null, null, 0, 0, -1, 19));
            assertEquals(ArrayIndexOutOfBoundsException.class, compare(before, after, new int[0], null, 0, 0, -1, 19));
        }
    }

    @Test void repeatedReadsAndLocalAliasesStayDistinct() throws Exception {
        String source = source("int[] alias = words; int a = words[i] & mask; int b = alias[i] & mask;"
                + " int c = words[i] & mask; return (a ^ b) ^ c;");
        var extraction = extract(source);
        assertEquals(1, extraction.regions().size(), extraction.diagnostics().toString());
        assertEquals(4, extraction.regions().getFirst().plan().inputs().size());
        String generated = rewrite(source);
        assertEquals(2, occurrences(generated, "words[i]"), generated);
        assertEquals(1, occurrences(generated, "alias[i]"), generated);
        assertTrue(generated.indexOf("words[i]") < generated.indexOf("alias[i]"), generated);
        assertTrue(generated.indexOf("alias[i]") < generated.lastIndexOf("words[i]"), generated);
        try (var before = compile(source); var after = compile(generated)) {
            compare(before, after, new int[] {Integer.MIN_VALUE, -1}, null, 1, 0, 0x55555555, 0);
            assertEquals(NullPointerException.class, compare(before, after, null, null, 0, 0, -1, 0));
        }
    }

    @Test void localIndexUpdatesStayBeforeConnectedLongWordAndCarryOperations() throws Exception {
        String source = source("int index = i; index += j;\n"
                + "long left = words[index] & 0xffffffffL; long right = other[index] & 0xffffffffL;"
                + " long sum = left + right; long carry = sum >>> 32; return (carry + 1) - 1;");
        var extraction = extract(source);
        var region = extraction.regions().stream().filter(r -> r.start() == source.indexOf("long left")).findFirst().orElseThrow(
                () -> new AssertionError(extraction));
        assertEquals(2, region.plan().inputs().size());
        assertTrue(region.plan().inputs().values().stream().allMatch(t -> NumericKind.fromType(t) == NumericKind.INT));
        assertTrue(region.trace().occurrences().stream().anyMatch(o -> o.evaluatedKind() == NumericKind.LONG
                && JavaExpressions.castSourceKind(o.expression()).orElse(null) == NumericKind.INT));
        assertTrue(region.trace().occurrences().stream().anyMatch(o -> o.evaluatedKind() == NumericKind.LONG
                && JavaExpressions.operationOf(o.expression()).orElse(null) == NumericOperation.UNSIGNED_SHIFT_RIGHT));
        String generated = rewrite(source);
        assertTrue(generated.indexOf("index += j") < generated.indexOf("words[index]"), generated);
        try (var before = compile(source); var after = compile(generated)) {
            assertEquals(1L, compare(before, after, new int[] {0, -1}, new int[] {0, -1}, 0, 1, 0, 0));
            assertEquals(0L, compare(before, after, new int[] {0}, new int[] {-1}, 0, 0, 0, 0));
            compare(before, after, new int[] {0}, new int[] {0}, Integer.MAX_VALUE, 1, 0, 0);
        }
    }

    @Test void aliasWritesSeparateCapturesFromLaterReads() throws Exception {
        String source = source("int[] alias = words; int left = (words[i] & mask) ^ (other[j] & mask);"
                + " alias[i] = value; int right = (words[i] & mask) ^ (other[j] & mask); return left ^ right;");
        var extraction = extract(source);
        assertEquals(2, extraction.regions().size(), extraction.diagnostics().toString());
        int write = source.indexOf("alias[i] = value");
        assertTrue(extraction.regions().getFirst().start() + extraction.regions().getFirst().length() <= write);
        assertTrue(extraction.regions().getLast().start() > write);
        String generated = rewrite(source);
        assertEquals(2, occurrences(generated, "words[i]"));
        assertTrue(generated.indexOf("words[i]") < generated.indexOf("alias[i] = value"), generated);
        assertTrue(generated.lastIndexOf("words[i]") > generated.indexOf("alias[i] = value"), generated);
        try (var before = compile(source); var after = compile(generated)) {
            assertEquals(14L, compare(before, after, new int[] {5}, new int[] {7}, 0, 0, -1, 11));
            int[] shared = {5};
            assertEquals(0L, compare(before, after, shared, shared, 0, 0, -1, 11));
        }
    }

    @Test void externalLocalWriteRemainsVisibleWhenTheFollowingReadThrows() throws Exception {
        String source = source("int observed = value; try { observed = value + 1;"
                + " int left = words[i] & mask; int right = other[j] & mask; return left ^ right;"
                + " } catch (RuntimeException failure) { return observed; }");
        String generated = rewrite(source);
        assertTrue(generated.contains("observed = value + 1;"), generated);
        assertTrue(generated.indexOf("observed = value + 1;") < generated.indexOf("words[i]"), generated);
        try (var before = compile(source); var after = compile(generated)) {
            assertEquals(20L, compare(before, after, null, new int[0], 0, 0, -1, 19));
            assertEquals(20L, compare(before, after, new int[0], null, 0, 0, -1, 19));
        }
    }

    @Test void throwingArithmeticKeepsItsOrderRelativeToReads() throws Exception {
        for (boolean divideFirst : List.of(true, false)) {
            String division = "int quotient = value / mask; ";
            String reads = "int mixed = (words[i] & value) ^ (other[j] & value); ";
            String source = source((divideFirst ? division + reads : reads + division) + "return mixed ^ quotient;");
            String generated = rewrite(source);
            assertEquals(divideFirst, generated.indexOf("value / mask") < generated.indexOf("words[i]"), generated);
            try (var before = compile(source); var after = compile(generated)) {
                assertEquals(divideFirst ? ArithmeticException.class : NullPointerException.class,
                        compare(before, after, null, null, 0, 0, 0, 1));
            }
        }
    }

    @Test void effectfulOrUnrepresentedReadsAndIndicesRemainBoundaries() {
        for (String expression : List.of("words[i++]", "words[(i = j)]", "words[next(i)]", "words[INDEX]",
                "FIELD[i]", "get(words)[i]", "words[other[i]]", "words[i / mask]",
                "words[i] + value / mask", "value / mask + words[i]", "words[i] + Math.addExact(value, mask)")) {
            String source = source("return (" + expression + ") + 0;").replace("public class Calculation {",
                    "public class Calculation { static volatile int INDEX; static volatile int[] FIELD;"
                    + " private static int next(int value) { return value; } private static int[] get(int[] input) { return input; }");
            var result = MathematicalAnalysis.analyze(MathTestSupport.parse(source), source, options(SafetyProfile.PRESERVE_JAVA),
                    new NullProgressMonitor(), -1, 0);
            assertFalse(result.changed(), expression + ": " + result.diagnostics());
        }
    }

    @Test void checkedAndFallbackProfilesKeepArrayReadsAsBoundaries() {
        String source = source("return (words[i] + 1) - 1;");
        for (SafetyProfile profile : List.of(SafetyProfile.CHECKED_THROW, SafetyProfile.GUARDED_FALLBACK)) {
            var extraction = new JavaComputationExtractor().extractRuntime(MathTestSupport.parse(source), source, options(profile));
            assertTrue(extraction.regions().isEmpty(), profile + ": " + extraction);
        }
    }

    @Test void reportedCaptureCostsIncludeEveryEmittedReference() {
        String source = source("int word = words[i]; return ((word + 1) - 1) * word;");
        var result = analyze(source, OptimizationGoal.READABILITY);
        assertTrue(result.changed(), result.diagnostics().toString());
        var cost = result.evidence().getFirst().cost();
        // One multiplication, one widening cast, one capture store, two capture references.
        assertEquals(5, cost.candidateCost().operationWork());
        assertEquals(6, cost.candidateScore());
        assertFalse(cost.estimatedRuntimeImprovement());
        assertTrue(result.replacements().getFirst().description().contains("array capture work 3"));
    }

    @Test void captureCostsCanEraseSmallRuntimeAndAllocationBenefits() {
        String source = source("int a = words[i] & mask; int b = other[j] & mask; return a ^ b;");
        for (OptimizationGoal goal : List.of(OptimizationGoal.LOWER_ESTIMATED_RUNTIME, OptimizationGoal.LOWER_ALLOCATION)) {
            var result = analyze(source, goal);
            assertFalse(result.changed(), goal + ": " + result.diagnostics());
            assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("ARRAY_CAPTURE_COST_EXCEEDS_BENEFIT")),
                    result.diagnostics().toString());
        }
    }

    @Test void rejectedCapturesRetainUsefulScalarAlternativesWithoutOverlappingEdits() throws Exception {
        String source = source("int first = value ^ 0; int word = (words[i] & mask) ^ (other[j] & mask);"
                + " int last = word ^ 0; return first ^ last;");
        for (OptimizationGoal goal : List.of(OptimizationGoal.LOWER_ESTIMATED_RUNTIME, OptimizationGoal.LOWER_ALLOCATION)) {
            var result = analyze(source, goal);
            assertTrue(result.changed(), goal + ": " + result.diagnostics());
            assertEquals(2, result.replacements().size(), result.diagnostics().toString());
            assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("ARRAY_CAPTURE_COST_EXCEEDS_BENEFIT")));
            assertEquals(1, result.diagnostics().stream().filter(d -> d.code().equals("ARRAY_SCALAR_RETRY")).count());
            assertTrue(result.evidence().stream().noneMatch(e -> e.original().contains("words[i]")));
            Document document = new Document(source);
            var undo = result.newEdit().apply(document, TextEdit.CREATE_UNDO);
            String generated = document.get();
            assertTrue(generated.contains("int word = (words[i] & mask) ^ (other[j] & mask);"), generated);
            assertFalse(generated.contains("_mathRead"), generated);
            undo.apply(document);
            assertEquals(source, document.get());
            try (var before = compile(source); var after = compile(generated)) {
                compare(before, after, new int[] {Integer.MIN_VALUE}, new int[] {-1}, 0, 0, -1, 7);
                assertEquals(NullPointerException.class, compare(before, after, null, new int[0], 0, 0, -1, 7));
                assertEquals(ArrayIndexOutOfBoundsException.class, compare(before, after, new int[0], null, 0, 0, -1, 7));
            }
        }
    }

    @Test void substantialScalarSavingsCanPayForCapturesUnderBothPerformanceGoals() throws Exception {
        String source = source("int current = words[i]; " + "current += 1; current -= 1; ".repeat(12) + "return current;");
        for (OptimizationGoal goal : List.of(OptimizationGoal.LOWER_ESTIMATED_RUNTIME, OptimizationGoal.LOWER_ALLOCATION)) {
            var result = analyze(source, goal);
            assertTrue(result.changed(), goal + ": " + result.diagnostics());
            var cost = result.evidence().getFirst().cost();
            assertTrue(cost.candidateScore() < cost.sourceScore(), cost.toString());
            assertTrue(cost.estimatedRuntimeImprovement(), cost.toString());
            Document document = new Document(source);
            result.newEdit().apply(document);
            try (var before = compile(source); var after = compile(document.get())) {
                compare(before, after, new int[] {Integer.MAX_VALUE}, null, 0, 0, 0, 0);
                assertEquals(NullPointerException.class, compare(before, after, null, null, 0, 0, 0, 0));
            }
        }
    }

    @Test void everyPrimitiveIntegralArrayKindRetainsItsTypedReadAndPromotion() throws Exception {
        var options = JavaComputationExtractorTest.options(EnumSet.of(NumericKind.BYTE, NumericKind.SHORT, NumericKind.CHAR,
                NumericKind.INT, NumericKind.LONG), SafetyProfile.PRESERVE_JAVA);
        for (Object sample : List.of(new byte[] {Byte.MIN_VALUE, -1}, new short[] {Short.MIN_VALUE, -1},
                new char[] {0, Character.MAX_VALUE}, new int[] {Integer.MIN_VALUE, -1}, new long[] {Long.MIN_VALUE, -1})) {
            String type = sample.getClass().getComponentType().getName();
            String source = source("return (words[i] & mask) ^ (other[j] & mask);").replace("int[]", type + "[]");
            var extraction = new JavaComputationExtractor().extractRuntime(MathTestSupport.parse(source), source, options);
            assertEquals(1, extraction.regions().size(), extraction.diagnostics().toString());
            var reads = extraction.regions().getFirst().arrayReads();
            assertEquals(2, reads.size());
            assertTrue(reads.stream().allMatch(r -> r.kind().name().equalsIgnoreCase(type)));
            String generated = rewrite(source, options);
            try (var before = compile(source); var after = compile(generated)) {
                compare(before, after, sample, sample, 0, 1, -1, 0);
                compare(before, after, sample, sample, 1, 0, 0x55555555, 0);
            }
        }
    }

    @Test void totalLocalIndexExpressionsKeepTheirOriginalSpellingAndOverflow() throws Exception {
        String source = source("return (words[(int)((long)i + j)] & mask) ^ (other[(i >>> 1) + j] & mask);");
        String generated = rewrite(source);
        assertTrue(generated.contains("words[(int)((long)i + j)]"), generated);
        assertTrue(generated.contains("other[(i >>> 1) + j]"), generated);
        try (var before = compile(source); var after = compile(generated)) {
            compare(before, after, new int[] {8, -1}, new int[] {4, 17}, 0, 1, -1, 0);
            assertEquals(ArrayIndexOutOfBoundsException.class,
                    compare(before, after, new int[] {8}, null, Integer.MAX_VALUE, 1, -1, 0));
        }
    }

    @Test void aReadSupplyingALaterIndexRemainsBeforeThatCapture() throws Exception {
        String source = source("int slot = words[i]; return (other[slot] & mask) ^ (words[j] & mask);");
        var extraction = extract(source);
        assertEquals(1, extraction.regions().size(), extraction.diagnostics().toString());
        assertTrue(extraction.regions().getFirst().start() > source.indexOf("int slot"));
        String generated = rewrite(source);
        assertTrue(generated.indexOf("int slot = words[i]") < generated.indexOf("other[slot]"), generated);
        try (var before = compile(source); var after = compile(generated)) {
            compare(before, after, new int[] {1, -1}, new int[] {8, 17}, 0, 1, -1, 0);
            assertEquals(ArrayIndexOutOfBoundsException.class, compare(before, after, new int[0], null, 0, 0, -1, 0));
        }
    }

    @Test void sourceNamesCannotCollideWithCaptureNames() throws Exception {
        String source = source("int _mathRead999 = value; int result = (words[i] & mask) ^ (other[j] & mask);"
                + " if (_mathRead999 == 0) return result; return result + 1;");
        int readOffset = source.indexOf("words[i]");
        assertTrue(readOffset >= 100 && readOffset < 1000);
        source = source.replace("_mathRead999", "_mathRead" + readOffset);
        String generated = rewrite(source);
        assertTrue(generated.contains("_mathRead" + readOffset + "_"), generated);
        try (var before = compile(source); var after = compile(generated)) {
            compare(before, after, new int[] {5}, new int[] {7}, 0, 0, -1, 0);
            compare(before, after, new int[] {5}, new int[] {7}, 0, 0, -1, 1);
        }
    }

    @Test void straightLineLoopBodyCapturesStayInsideTheUnchangedLoop() throws Exception {
        String source = source("long total = 0; for (int k = 0; k < words.length; k++) {"
                + " int mixed = (words[k] & mask) ^ (other[k] & mask); total += mixed; words[k] ^= value; } return total;");
        String generated = rewrite(source);
        assertTrue(generated.contains("for (int k = 0; k < words.length; k++)"), generated);
        assertTrue(generated.contains("total += mixed; words[k] ^= value;"), generated);
        assertTrue(generated.indexOf("for (int k") < generated.indexOf(" = words[k]"), generated);
        try (var before = compile(source); var after = compile(generated)) {
            compare(before, after, new int[] {5, 9}, new int[] {7, -1}, 0, 0, -1, 11);
        }
    }

    private static String source(String body) {
        return "public class Calculation { public static long compute(int[] words,int[] other,int i,int j,int mask,int value) {\n"
                + body + "\n}}";
    }

    private static MathCleanUpOptions options(SafetyProfile safety) {
        return JavaComputationExtractorTest.options(Set.of(NumericKind.INT, NumericKind.LONG), safety);
    }

    private static JavaComputationExtractor.Extraction extract(String source) {
        return new JavaComputationExtractor().extractRuntime(MathTestSupport.parse(source), source, options(SafetyProfile.PRESERVE_JAVA));
    }

    private static MathematicalAnalysis.Analysis analyze(String source, OptimizationGoal goal) {
        var base = options(SafetyProfile.PRESERVE_JAVA);
        var options = new MathCleanUpOptions(base.enabled(), base.kinds(), base.safety(), goal, base.workBudget(),
                base.maxStates(), base.checkedOptIn(), base.targetJava(), base.exclusions());
        return MathematicalAnalysis.analyze(MathTestSupport.parse(source), source, options, new NullProgressMonitor(), -1, 0);
    }

    private String rewrite(String source) throws Exception {
        return rewrite(source, options(SafetyProfile.PRESERVE_JAVA));
    }

    private String rewrite(String source, MathCleanUpOptions options) throws Exception {
        var result = MathematicalAnalysis.analyze(MathTestSupport.parse(source), source, options,
                new NullProgressMonitor(), -1, 0);
        assertTrue(result.changed(), result.diagnostics().toString());
        Document document = new Document(source);
        var undo = result.newEdit().apply(document, TextEdit.CREATE_UNDO);
        String generated = document.get();
        undo.apply(document);
        assertEquals(source, document.get(), "Undo must restore the complete original source");
        return generated;
    }

    private static int occurrences(String source, String text) {
        return (source.length() - source.replace(text, "").length()) / text.length();
    }

    private Compiled compile(String source) throws Exception {
        Path folder = Files.createTempDirectory(temporary, "array-computation");
        Path file = folder.resolve("Calculation.java");
        Files.writeString(file, source, StandardCharsets.UTF_8);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "17", "-d", folder.toString(), file.toString()), source);
        var loader = new URLClassLoader(new java.net.URL[] {folder.toUri().toURL()}, getClass().getClassLoader());
        Method method = Arrays.stream(loader.loadClass("Calculation").getDeclaredMethods())
                .filter(m -> m.getName().equals("compute")).findFirst().orElseThrow();
        return new Compiled(loader, method);
    }

    private static Object compare(Compiled before, Compiled after, Object words, Object other,
            int i, int j, int mask, int value) throws Exception {
        Outcome expected = before.invoke(words, other, i, j, mask, value);
        assertEquals(expected, after.invoke(words, other, i, j, mask, value));
        return expected.value();
    }

    private record Outcome(Object value, List<Object> words, List<Object> other) { }

    private record Compiled(URLClassLoader loader, Method method) implements AutoCloseable {
        Outcome invoke(Object words, Object other, int i, int j, int mask, int value) throws Exception {
            Object first = copyArray(words);
            Object second = words == other ? first : copyArray(other);
            Object result;
            try {
                result = method.invoke(null, first, second, i, j, mask, value);
            } catch (InvocationTargetException failure) {
                result = failure.getCause().getClass();
            }
            return new Outcome(result, contents(first), contents(second));
        }
        private static List<Object> contents(Object array) {
            return array == null ? null : java.util.stream.IntStream.range(0, Array.getLength(array))
                    .mapToObj(i -> Array.get(array, i)).toList();
        }
        private static Object copyArray(Object array) {
            if (array == null) return null;
            Object copy = Array.newInstance(array.getClass().getComponentType(), Array.getLength(array));
            System.arraycopy(array, 0, copy, 0, Array.getLength(array));
            return copy;
        }
        @Override public void close() throws Exception { loader.close(); }
    }
}
