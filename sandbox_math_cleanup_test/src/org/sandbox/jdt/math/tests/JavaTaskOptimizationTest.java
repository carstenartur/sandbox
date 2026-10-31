/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;
import de.regelsuche.sdk.optimization.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.eclipse.jface.text.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.*;

class JavaTaskOptimizationTest {
    @TempDir Path directory;
    static MathCleanUpOptions options() {
        return new MathCleanUpOptions(true, Set.of(NumericKind.INT, NumericKind.LONG), SafetyProfile.PRESERVE_JAVA,
                OptimizationGoal.LOWER_ESTIMATED_RUNTIME, 1_000_000, 20_000, false, 17, List.of());
    }
    private static String source(String extra, String result) {
        return "public class Calculation { public static long run(int[] x, int[] z, long v) { long high=17; try { "
                + "high=v>>>32; z[0]=(int)high; long low=v&0xffffffffL; z[1]=(int)low; "
                + extra + " return " + result + "; } catch (RuntimeException e) { return high; } } }";
    }
    @Test void wholeTaskOptimizesAcrossStoresAndPreservesPartialStateAndUndo() throws Exception {
        String original = source("", "(high<<32)+low");
        var analysis = MathematicalAnalysis.analyze(MathTestSupport.parse(original), original, options(), null, -1, 0);
        assertTrue(analysis.diagnostics().stream().anyMatch(d -> d.code().equals("VERIFIED_SOURCE_TASK")), analysis.diagnostics().toString());
        var document = new Document(original);
        var undo = analysis.newEdit().apply(document);
        String generated = document.get();
        assertTrue(generated.contains("return v;"), generated);
        assertTrue(generated.contains("z[0]=(int)high;"), generated);
        assertTrue(generated.contains("z[1]=(int)low;"), generated);
        undo.apply(document); assertEquals(original, document.get());
        compare(original, generated);
    }
    @Test void changedInputsAndAuthoredCommentsCannotBeReplacedByStaleValues() throws Exception {
        for (String original : List.of(source("v=0;", "(high<<32)+low"), source("", "(high<<32) /* carry */ +low"))) {
            var analysis = MathematicalAnalysis.analyze(MathTestSupport.parse(original), original, options(), null, -1, 0);
            var document = new Document(original); analysis.newEdit().apply(document);
            if (original.contains("/* carry */")) assertTrue(document.get().contains("/* carry */"));
            compare(original, document.get());
        }
    }
    @Test void aliasedReadAfterStoreStillReadsTheUpdatedArray() throws Exception {
        String original = source("long read=x[0];", "((high<<32)+low)^read");
        var analysis = MathematicalAnalysis.analyze(MathTestSupport.parse(original), original, options(), null, -1, 0);
        var document = new Document(original); analysis.newEdit().apply(document);
        assertTrue(analysis.diagnostics().stream().anyMatch(d -> d.code().equals("VERIFIED_SOURCE_TASK")), analysis.diagnostics().toString());
        assertTrue(document.get().contains("long read=x[0];"));
        compare(original, document.get());
    }
    private void compare(String original, String generated) throws Exception {
        try (var before = compile(original, "before"); var after = compile(generated, "after")) {
            var a = before.loadClass("Calculation").getMethod("run", int[].class, int[].class, long.class);
            var b = after.loadClass("Calculation").getMethod("run", int[].class, int[].class, long.class);
            for (long value : new long[]{0, -1, Long.MIN_VALUE, Long.MAX_VALUE, 0x12345678abcdef01L})
                for (int size = -1; size <= 3; size++) for (boolean alias : new boolean[]{false, true}) {
                    int[] left = size < 0 ? null : new int[size], right = size < 0 ? null : new int[size];
                    int[] x = alias ? left : new int[]{-1}, y = alias ? right : new int[]{-1};
                    assertEquals(a.invoke(null, x, left, value), b.invoke(null, y, right, value));
                    assertArrayEquals(left, right); assertArrayEquals(x, y);
                }
        }
    }
    private java.net.URLClassLoader compile(String source, String label) throws Exception {
        Path output = Files.createDirectories(directory.resolve(label));
        Path file = output.resolve("Calculation.java"); Files.writeString(file, source, StandardCharsets.UTF_8);
        assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "17", "-d", output.toString(), file.toString()));
        return new java.net.URLClassLoader(new java.net.URL[]{output.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    }
}
