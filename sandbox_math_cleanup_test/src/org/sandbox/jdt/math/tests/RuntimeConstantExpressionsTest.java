/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Set;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;

class RuntimeConstantExpressionsTest {
    @Test void rotationWidthsAndComputedMasksAreNotConstantFoldingDemos() {
        for (String expression : List.of("(x << 9) | (x >>> (32 - 9))", "x & ((1 << 4) - 1)",
                "x + (5 * 7 * 11)", "x ^ (int) 42L")) {
            String source = "class Calculation { int compute(int x) { return " + expression + "; }}";
            var result = MathematicalAnalysis.analyze(MathTestSupport.parse(source), source,
                    new MathCleanUpOptions(true, Set.of(NumericKind.INT, NumericKind.LONG),
                            SafetyProfile.PRESERVE_JAVA, OptimizationGoal.LOWER_ESTIMATED_RUNTIME,
                            1_000_000L, 20_000, false, 17, List.of()), new NullProgressMonitor(), -1, 0);
            assertFalse(result.changed(), expression + ": " + result.diagnostics());
            assertTrue(result.evidence().isEmpty(), expression);
        }
    }
}
