/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;

class MathematicalExplanationIntegrationTest {
    @Test void mathematicalIntegralExplanationsDescribeTheVerifiedDomainInEveryMode() throws Exception {
        String source = "public class Calculation { public static int compute(int x) {return 2*x/2;} }";
        for (var mode : MathCleanUpOptions.ExplanationMode.values()) {
            var result = analyze(source, mode);
            String generated = applyAndUndo(source, result);
            assertEquals(mode != MathCleanUpOptions.ExplanationMode.NONE,
                    generated.contains("// Verified mathematics (MATHEMATICAL)."), generated);
            assertTrue(generated.contains("exact no-overflow range before [-1073741824, 1073741823]"), generated);
            assertFalse(generated.contains("Math.multiplyExact"), generated);
            String details = result.replacements().getFirst().description();
            assertTrue(details.contains("Verified mathematics (MATHEMATICAL)."), details);
            assertTrue(details.contains("Mathematical mode intentionally permits different Java overflow behavior."), details);
            assertFalse(details.contains(MathCleanUpOptions.CHECKED_WARNING), details);
            assertFalse(details.contains("Java integral wraparound and explicit conversions are preserved."), details);
            assertNotNull(result.evidence().getFirst().explanation());
            assertTrue(result.evidence().getFirst().explanation().verified().derivation().isPresent());
            MathTestSupport.parse(generated);
        }
    }

    @Test void bigIntegerValueContractIsExplainedAsNontrivial() throws Exception {
        String source = "public class Calculation { public static java.math.BigInteger compute(java.math.BigInteger x) {"
                + "return x.add(java.math.BigInteger.ONE).subtract(java.math.BigInteger.ONE);} }";
        var result = analyze(source, MathCleanUpOptions.ExplanationMode.NONTRIVIAL);
        String generated = applyAndUndo(source, result);
        assertTrue(generated.contains("// Verified mathematics (MATHEMATICAL)."), generated);
        assertTrue(generated.contains("result reference identity may change"), generated);
        assertTrue(result.replacements().getFirst().description().contains("with bitLength() below 4096"));
        MathTestSupport.parse(generated);
    }

    private static MathematicalAnalysis.Analysis analyze(String source, MathCleanUpOptions.ExplanationMode mode) {
        var values = new HashMap<>(MathCleanUpOptions.defaults(17).toMap());
        values.put(MathCleanUpOptions.CLEANUP, "true");
        values.put(MathCleanUpOptions.KINDS, "INT,BIG_INTEGER");
        values.put(MathCleanUpOptions.WORK_BUDGET, "1000000");
        values.put(MathCleanUpOptions.MAX_STATES, "20000");
        values.put(MathCleanUpOptions.MATHEMATICAL_OPT_IN, "true");
        values.put(MathCleanUpOptions.EXPLANATIONS, mode.name());
        return MathematicalAnalysis.analyze(MathTestSupport.parse(source), source,
                MathCleanUpOptions.parse(values, 17), new NullProgressMonitor(), -1, 0);
    }

    private static String applyAndUndo(String source, MathematicalAnalysis.Analysis analysis) throws Exception {
        assertTrue(analysis.changed(), analysis.diagnostics().toString());
        assertFalse(analysis.documentation().isEmpty());
        var document = new Document(source);
        var undo = analysis.newEdit().apply(document, TextEdit.CREATE_UNDO);
        String generated = document.get();
        undo.apply(document);
        assertEquals(source, document.get());
        return generated;
    }
}
