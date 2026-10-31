/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
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
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.JavaComputationExtractor;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;

/** Field-array reads remain ordered effects, including class initialization and dead values. */
class FieldArrayOrderingTest {
    @TempDir Path temporary;

    @Test void classInitializationCannotMoveBeforeAnEarlierReadOrHideAChangedReceiver() throws Exception {
        String source = "public class Calculation { static int[] words, other; public static int initializations; "
                + "static class Trigger { static int[] VALUES = init(); static int[] init() { "
                + "initializations++; words = other; return new int[] {7}; }} "
                + "public static int compute(int[] left, int[] right, int mask) { words = left; other = right; "
                + "int a = words[0] & mask; int b = Trigger.VALUES[0] & mask; "
                + "int c = words[0] & mask; return a ^ b ^ c; }}";
        String generated = rewrite(source);
        assertEquals(2, count(generated, "words[0]"), generated);
        assertEquals(1, count(generated, "Trigger.VALUES[0]"), generated);
        assertTrue(generated.indexOf("words[0]") < generated.indexOf("Trigger.VALUES[0]"), generated);
        assertTrue(generated.indexOf("Trigger.VALUES[0]") < generated.lastIndexOf("words[0]"), generated);
        try (var before = compile(source); var after = compile(generated)) {
            assertEquals(NullPointerException.class, invoke(before, null, new int[] {-7}, -1));
            assertEquals(NullPointerException.class, invoke(after, null, new int[] {-7}, -1));
            assertEquals(0, before.loadClass("Calculation").getField("initializations").getInt(null));
            assertEquals(0, after.loadClass("Calculation").getField("initializations").getInt(null));
            assertEquals(31 ^ 7 ^ -7, invoke(before, new int[] {31}, new int[] {-7}, -1));
            assertEquals(31 ^ 7 ^ -7, invoke(after, new int[] {31}, new int[] {-7}, -1));
            assertEquals(1, before.loadClass("Calculation").getField("initializations").getInt(null));
            assertEquals(1, after.loadClass("Calculation").getField("initializations").getInt(null));
        }
    }

    @Test void reassigningAFieldSeparatesTheTwoSetsOfReads() throws Exception {
        String source = "public class Calculation { static int[] words; "
                + "public static int compute(int[] left, int[] right, int mask) { words = left; "
                + "int first = (words[0] & mask) ^ (right[0] & mask); words = right; "
                + "int second = (words[0] & mask) ^ (left[0] & mask); return first ^ second; }}";
        String generated = rewrite(source);
        int write = generated.indexOf("words = right;");
        assertTrue(generated.indexOf("words[0]") < write, generated);
        assertTrue(write < generated.lastIndexOf("words[0]"), generated);
        assertEquals(2, count(generated, "words[0]"), generated);
        try (var before = compile(source); var after = compile(generated)) {
            for (int mask : new int[] {0, -1, Integer.MIN_VALUE, 0x55555555}) {
                assertEquals(0, invoke(before, new int[] {31}, new int[] {-7}, mask));
                assertEquals(0, invoke(after, new int[] {31}, new int[] {-7}, mask));
            }
            int[] shared = {19};
            assertEquals(invoke(before, shared, shared, -1), invoke(after, shared, shared, -1));
            assertEquals(invoke(before, null, new int[0], -1), invoke(after, null, new int[0], -1));
        }
    }

    @Test void anArrayWriteRemainsObservableWhenTheFollowingReadThrows() throws Exception {
        String source = "public class Calculation { static int[] words; "
                + "public static int compute(int[] left, int[] right, int mask) { words = left; "
                + "try { words[0] = mask; int a = words[0] & mask; int b = right[0] & mask; return a ^ b; } "
                + "catch (RuntimeException failure) { return words[0]; } }}";
        String generated = rewrite(source);
        assertTrue(generated.contains("words[0] = mask;"), generated);
        try (var before = compile(source); var after = compile(generated)) {
            assertEquals(23, invoke(before, new int[] {1}, new int[0], 23));
            assertEquals(23, invoke(after, new int[] {1}, new int[0], 23));
            assertEquals(23, invoke(before, new int[] {1}, null, 23));
            assertEquals(23, invoke(after, new int[] {1}, null, 23));
            int[] shared = {3};
            assertEquals(invoke(before, shared, shared, 23), invoke(after, shared, shared, 23));
        }
    }

    @Test void deadFieldArrayValuesStillReadAndThrow() throws Exception {
        String source = "public class Calculation { static int[] words; "
                + "public static int compute(int[] left, int[] right, int mask) { words = left; "
                + "int used = words[0] & mask; int dead = words[1] & mask; return (used + 1) - 1; }}";
        String generated = rewrite(source);
        assertEquals(1, count(generated, "words[0]"), generated);
        assertEquals(1, count(generated, "words[1]"), generated);
        try (var before = compile(source); var after = compile(generated)) {
            assertEquals(invoke(before, new int[] {19, 7}, null, -1), invoke(after, new int[] {19, 7}, null, -1));
            assertEquals(ArrayIndexOutOfBoundsException.class, invoke(before, new int[] {19}, null, -1));
            assertEquals(ArrayIndexOutOfBoundsException.class, invoke(after, new int[] {19}, null, -1));
            assertEquals(NullPointerException.class, invoke(after, null, null, -1));
        }
    }

    @Test void repeatedReadsRemainIndependentInputsInsteadOfOneFieldValue() {
        String source = "class Calculation { int[] words; int compute(int mask) { "
                + "int first = words[0] & mask; int second = words[0] & mask; return first ^ second; }}";
        var extraction = new JavaComputationExtractor().extractRuntime(MathTestSupport.parse(source), source, options());
        var region = extraction.regions().stream().filter(r -> r.arrayReads().size() == 2).findFirst()
                .orElseThrow(() -> new AssertionError(extraction));
        assertNotEquals(region.arrayReads().get(0).inputId(), region.arrayReads().get(1).inputId());
        assertNotEquals(region.arrayReads().get(0).javaName(), region.arrayReads().get(1).javaName());
    }

    @Test void objectQualifiedArrayReceiversRetainReadOrderNullAndBoundsChecks() throws Exception {
        for (String read : List.of("receiver.words[0]", "other.words[0]", "this.other.words[0]")) {
            String source="public class Calculation {int[] words; Calculation other;"
                    +"public static int compute(int[] left,int[] right,int mask){Calculation c=new Calculation(); c.words=left; c.other=c; return c.calculate(c,right,mask);}"
                    +"private int calculate(Calculation receiver,int[] data,int mask){int a="+read+" & mask; int b=data[0] & mask; return a ^ b;} }";
            String generated=rewrite(source);
            assertEquals(1,count(generated,read));
            assertTrue(generated.indexOf(read)<generated.indexOf("data[0]"));
            try(var before=compile(source);var after=compile(generated)) {
                for(int[] left:new int[][]{null,new int[0],new int[]{31}})
                    for(int[] right:new int[][]{null,new int[0],new int[]{-7}})
                        assertEquals(invoke(before,left,right,-1),invoke(after,left,right,-1));
            }
        }
    }

    @Test void effectfulAndVolatileReceiversRemainOutsideTheContract() {
        for (String read : List.of("volatileWords[0]", "next().words[0]", "receiver.volatileWords[0]",
                "volatileOther.words[0]", "this.volatileOther.words[0]", "words[index++]", "words[index / mask]")) {
            String source = "class Calculation { volatile int[] volatileWords; int[] words; Calculation other; volatile Calculation volatileOther; "
                    + "Calculation next() {return this;} int compute(Calculation receiver, int[] data, int index, int mask) { "
                    + "return (" + read + " & mask) ^ (data[0] & mask); }}";
            var extraction = new JavaComputationExtractor().extractRuntime(MathTestSupport.parse(source), source, options());
            assertTrue(extraction.regions().stream().allMatch(r -> r.arrayReads().isEmpty()), read + ": " + extraction);
        }
    }

    private static MathCleanUpOptions options() {
        return new MathCleanUpOptions(true, EnumSet.of(NumericKind.INT), SafetyProfile.PRESERVE_JAVA,
                OptimizationGoal.READABILITY, 1_000_000, 2000, false, 17, List.of());
    }
    private static String rewrite(String source) throws Exception {
        var result = MathematicalAnalysis.analyze(MathTestSupport.parse(source), source, options(), new NullProgressMonitor(), -1, 0);
        assertTrue(result.changed(), result.diagnostics().toString());
        assertEquals(result.replacements().size(), result.evidence().size());
        for (var evidence : result.evidence()) assertNotNull(evidence.proof());
        var document = new Document(source);
        var undo = result.newEdit().apply(document, TextEdit.CREATE_UNDO);
        String generated = document.get();
        undo.apply(document);
        assertEquals(source, document.get());
        MathTestSupport.parse(generated);
        return generated;
    }
    private URLClassLoader compile(String source) throws Exception {
        Path directory = Files.createTempDirectory(temporary, "field-order-");
        Path file = directory.resolve("Calculation.java");
        Files.writeString(file, source, StandardCharsets.UTF_8);
        var compiler = Objects.requireNonNull(ToolProvider.getSystemJavaCompiler(), "A JDK is required");
        assertEquals(0, compiler.run(null, null, null, "--release", "17", "-proc:none", "-d", directory.toString(), file.toString()));
        return new URLClassLoader(new URL[] {directory.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    }
    private static Object invoke(ClassLoader loader, int[] left, int[] right, int mask) throws Exception {
        int[] first = left == null ? null : left.clone();
        int[] second = right == left ? first : right == null ? null : right.clone();
        try {
            return loader.loadClass("Calculation").getMethod("compute", int[].class, int[].class, int.class)
                    .invoke(null, first, second, mask);
        } catch (InvocationTargetException failure) {
            return Objects.requireNonNull(failure.getCause()).getClass();
        }
    }
    private static int count(String source, String text) {
        int result = 0;
        for (int at = 0; (at = source.indexOf(text, at)) >= 0; at += text.length()) result++;
        return result;
    }
}
