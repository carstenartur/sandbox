/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Both ordinary whole-file and explicit selection must retain the actual source-proved improvement. */
class PrimitiveSourceGuardSchedulingTest extends JavaContextTestSupport {
    @ParameterizedTest @ValueSource(booleans={false,true})
    void originalGuardsAndSwitchContextSurviveBothSchedules(boolean selection) throws Exception {
        for (String calculation : List.of("return 3-(x%4);",
                "switch(x&1) { case 0: int shift=3-(x%4); return shift; default: return 0; }")) {
            String source = "public class Sample { public static int calls; static void error() { calls++; "
                    + "throw new IllegalArgumentException(); } public static int run(int x) { if(x<0) error(); " + calculation + " } }";
            int offset = source.indexOf("3-(x%4)");
            var analysis = MathematicalAnalysis.analyze(JavaEarlyExitGuardFactsTest.parse(source), source, options(), null,
                    selection ? offset : -1, selection ? "3-(x%4)".length() : 0);
            assertTrue(analysis.changed(), analysis.diagnostics().toString());
            assertTrue(analysis.replacements().stream().anyMatch(edit -> edit.description().contains("NON_NEGATIVE")),
                    "The new source-proved family must remain active in both schedules");
            var document = new Document(source); var undo = analysis.newEdit().apply(document, TextEdit.CREATE_UNDO);
            String generated = document.get(); undo.apply(document); assertEquals(source, document.get());
            try (var before = compile(source); var after = compile(generated)) {
                var original = before.loadClass("Sample"); var optimized = after.loadClass("Sample");
                Method reference = original.getMethod("run", int.class), actual = optimized.getMethod("run", int.class);
                for (int input : new int[] {Integer.MIN_VALUE, -1, 0, 1, 2, 3, 4, Integer.MAX_VALUE}) {
                    assertEquals(outcome(reference, input), outcome(actual, input), "input=" + input);
                    assertEquals(original.getField("calls").get(null), optimized.getField("calls").get(null));
                }
            }
        }
    }
    private static Object outcome(Method method, int input) throws Exception {
        try { return method.invoke(null, input); }
        catch (InvocationTargetException failure) { return failure.getCause().getClass(); }
    }
}
