/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;

class MathematicalUnsupportedRetryTest {
    private static final String SOURCE = """
            import java.math.BigInteger;
            public class Calculation {
                public static long[] compute(long seed) {
                    BigInteger base = BigInteger.valueOf(seed);
                    BigInteger modulus = BigInteger.valueOf(65537);
                    BigInteger square = base.modPow(BigInteger.TWO, modulus);
                    BigInteger shifted = base.modPow(modulus.add(BigInteger.ONE).shiftRight(2), modulus);
                    return new long[] {square.longValue(), shifted.longValue()};
                }
            }
            """;

    @Test void unsupportedNeighbourDoesNotHideAnIndependentRuntimeImprovement() {
        var analysis = analyze(1_000_000);
        assertTrue(analysis.diagnostics().stream().anyMatch(d -> d.code().equals("MODULAR_STATEMENT_RETRY")),
                analysis.diagnostics().toString());
        assertEquals(1, analysis.replacements().size(), analysis.diagnostics().toString());
        assertEquals(1, analysis.evidence().size());
        assertTrue(analysis.evidence().getFirst().cost().estimatedRuntimeImprovement());
        var edit = analysis.replacements().getFirst();
        assertTrue(SOURCE.substring(edit.offset(), edit.offset() + edit.length()).contains("BigInteger square"));
        assertFalse(SOURCE.substring(edit.offset(), edit.offset() + edit.length()).contains("BigInteger shifted"));
    }

    @Test void anExhaustedFailedAttemptCannotStartAStatementRetry() {
        var analysis = analyze(1);
        assertTrue(analysis.replacements().isEmpty());
        assertFalse(analysis.diagnostics().stream().anyMatch(d -> d.code().equals("MODULAR_STATEMENT_RETRY")),
                analysis.diagnostics().toString());
    }

    private static MathematicalAnalysis.Analysis analyze(long budget) {
        var values = new HashMap<>(MathCleanUpOptions.defaults(17).toMap());
        values.put(MathCleanUpOptions.CLEANUP, "true");
        values.put(MathCleanUpOptions.KINDS, "BIG_INTEGER");
        values.put(MathCleanUpOptions.MATHEMATICAL_OPT_IN, "true");
        values.put(MathCleanUpOptions.WORK_BUDGET, Long.toString(budget));
        values.put(MathCleanUpOptions.MAX_STATES, "20000");
        return MathematicalAnalysis.analyze(MathTestSupport.parse(SOURCE), SOURCE,
                MathCleanUpOptions.parse(values, 17), new NullProgressMonitor(), -1, 0);
    }
}
