/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;

import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.tools.ToolProvider;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;

/** Observes state after normal and exceptional execution, not only an exception's class. */
class FieldArrayStateVisibilityTest {
    @TempDir Path temporary;

    @Test void rebindingAndStoresBetweenReadGroupsRemainObservableAfterLaterFailures() throws Exception {
        String source = "public class Calculation { public static int[] words; "
                + "public static int compute(int[] left, int[] rebound, int[] tail, int mask) { words = left; "
                + "int first = (words[0] & mask) ^ (left[1] & mask); "
                + "words = rebound; words[0] = mask; "
                + "int second = (words[0] & mask) ^ (tail[0] & mask); return first ^ second; }}";
        var options = new MathCleanUpOptions(true, Set.of(NumericKind.INT), SafetyProfile.PRESERVE_JAVA,
                OptimizationGoal.READABILITY, 1_000_000, 20_000, false, 17, List.of());
        var analysis = MathematicalAnalysis.analyze(MathTestSupport.parse(source), source, options, new NullProgressMonitor(), -1, 0);
        assertTrue(analysis.changed(), analysis.diagnostics().toString());
        assertEquals(analysis.replacements().size(), analysis.evidence().size());
        analysis.evidence().forEach(e -> assertNotNull(e.proof()));
        var document = new Document(source);
        var undo = analysis.newEdit().apply(document, TextEdit.CREATE_UNDO);
        String generated = document.get();
        undo.apply(document);
        assertEquals(source, document.get());
        MathTestSupport.parse(generated);
        try (var before = compile(source); var after = compile(generated)) {
            for (int mask : new int[] {0, -1, 23, Integer.MIN_VALUE}) {
                Outcome normal = compare(before, after, new int[] {5, 9}, new int[] {8}, new int[] {1}, mask);
                assertNull(normal.exception());
                assertTrue(normal.fieldIsRebound());
                assertEquals(List.of(mask), normal.rebound());
                for (int[] tail : new int[][] {null, new int[0]}) {
                    Outcome failed = compare(before, after, new int[] {5, 9}, new int[] {8}, tail, mask);
                    assertEquals(tail == null ? NullPointerException.class.getName()
                            : ArrayIndexOutOfBoundsException.class.getName(), failed.exception());
                    assertTrue(failed.fieldIsRebound());
                    assertFalse(failed.fieldIsLeft());
                    assertEquals(List.of(5, 9), failed.left());
                    assertEquals(List.of(mask), failed.rebound());
                    assertEquals(List.of(mask), failed.field());
                }
                Outcome early = compare(before, after, new int[0], new int[] {8}, null, mask);
                assertEquals(ArrayIndexOutOfBoundsException.class.getName(), early.exception());
                assertTrue(early.fieldIsLeft());
                assertFalse(early.fieldIsRebound());
                assertEquals(List.of(8), early.rebound());
                int[] shared = {5, 9};
                compare(before, after, shared, shared, shared, mask);
            }
        }
    }

    private static Outcome compare(ClassLoader before, ClassLoader after, int[] left, int[] rebound, int[] tail, int mask) throws Exception {
        Outcome expected = invoke(before, left, rebound, tail, mask);
        assertEquals(expected, invoke(after, left, rebound, tail, mask));
        return expected;
    }
    private static Outcome invoke(ClassLoader loader, int[] left, int[] rebound, int[] tail, int mask) throws Exception {
        Map<int[], int[]> copies = new IdentityHashMap<>();
        int[] first = copy(left, copies), second = copy(rebound, copies), third = copy(tail, copies);
        Class<?> type = loader.loadClass("Calculation");
        Integer value = null;
        String exception = null;
        try {
            value = (Integer) type.getMethod("compute", int[].class, int[].class, int[].class, int.class)
                    .invoke(null, first, second, third, mask);
        } catch (InvocationTargetException thrown) {
            exception = Objects.requireNonNull(thrown.getCause()).getClass().getName();
        }
        int[] field = (int[]) type.getField("words").get(null);
        return new Outcome(value, exception, contents(first), contents(second), contents(third), contents(field),
                field == first, field == second, field == third);
    }
    private static int[] copy(int[] source, Map<int[], int[]> copies) {
        return source == null ? null : copies.computeIfAbsent(source, value -> value.clone());
    }
    private static List<Integer> contents(int[] values) {
        return values == null ? null : Arrays.stream(values).boxed().toList();
    }
    private URLClassLoader compile(String source) throws Exception {
        Path directory = Files.createTempDirectory(temporary, "field-observable-state-");
        Path file = directory.resolve("Calculation.java");
        Files.writeString(file, source, StandardCharsets.UTF_8);
        var compiler = Objects.requireNonNull(ToolProvider.getSystemJavaCompiler(), "A JDK is required");
        assertEquals(0, compiler.run(null, null, null, "--release", "17", "-proc:none", "-d", directory.toString(), file.toString()));
        return new URLClassLoader(new URL[] {directory.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    }
    private record Outcome(Integer value, String exception, List<Integer> left, List<Integer> rebound,
            List<Integer> tail, List<Integer> field, boolean fieldIsLeft, boolean fieldIsRebound, boolean fieldIsTail) { }
}
