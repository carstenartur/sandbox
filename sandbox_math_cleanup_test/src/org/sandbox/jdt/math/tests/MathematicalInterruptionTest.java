/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;

import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;

class MathematicalInterruptionTest {
    private static final String SOURCE = """
            public class Calculation {
                public static int first(int x) { return x + 0; }
                public static int second(int y) { return y + 0; }
            }
            """;

    @Test void interruptAfterACompletedRegionDiscardsEarlierReplacements() {
        verifyInterruption(false);
    }

    @Test void interruptAtCompletionCannotPublishCompletedReplacements() {
        verifyInterruption(true);
    }

    @Test void monitorCancellationStillReturnsAnEmptyCancelledAnalysis() {
        var ast = MathTestSupport.parse(SOURCE);
        var monitor = new NullProgressMonitor() {
            @Override public void worked(int work) { setCanceled(true); }
        };
        var result = MathematicalAnalysis.analyze(ast, SOURCE, options("ALL"), monitor, -1, 0);
        assertFalse(result.changed());
        assertTrue(result.evidence().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().contains("CANCEL")), result.toString());
    }

    private static void verifyInterruption(boolean duringDone) {
        for (String mode : List.of("NONE", "NONTRIVIAL", "ALL")) {
            var ast = MathTestSupport.parse(SOURCE);
            var options = options(mode);
            var baseline = MathematicalAnalysis.analyze(ast, SOURCE, options, new NullProgressMonitor(), -1, 0);
            assertEquals(2, baseline.replacements().size(), baseline.diagnostics().toString());
            var monitor = new NullProgressMonitor() {
                @Override public void worked(int work) {
                    if (!duringDone) Thread.currentThread().interrupt();
                }
                @Override public void done() {
                    if (duringDone) Thread.currentThread().interrupt();
                }
            };
            try {
                assertThrows(OperationCanceledException.class,
                        () -> MathematicalAnalysis.analyze(ast, SOURCE, options, monitor, -1, 0), mode);
            } finally {
                assertTrue(Thread.interrupted(), "Analysis must not consume the interrupt flag");
            }
            assertFalse(monitor.isCanceled(), "This is an interrupt, not progress-monitor cancellation");
        }
    }

    private static MathCleanUpOptions options(String mode) {
        var values = new HashMap<>(new MathCleanUpOptions(true, Set.of(NumericKind.INT), SafetyProfile.PRESERVE_JAVA,
                OptimizationGoal.READABILITY, 1_000_000L, 2000, false, 17, List.of()).toMap());
        values.put(MathCleanUpOptions.EXPLANATIONS, mode);
        return MathCleanUpOptions.parse(values, 17);
    }
}
