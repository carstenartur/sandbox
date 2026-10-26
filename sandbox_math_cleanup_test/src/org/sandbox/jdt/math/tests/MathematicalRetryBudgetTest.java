/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.corext.fix.math.JavaComputationExtractor;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;

import de.regelsuche.sdk.optimization.CancellationToken;
import de.regelsuche.sdk.optimization.CheckedPolicy;
import de.regelsuche.sdk.optimization.ComputationOptimizer;
import de.regelsuche.sdk.optimization.OptimizationBudget;
import de.regelsuche.sdk.optimization.OptimizationRequest;
import de.regelsuche.sdk.optimization.OptimizationResult.BudgetExceeded;

class MathematicalRetryBudgetTest {
    @Test void failedAttemptPreservesOnlyTheActuallyUnspentBudgetForLaterMethods() {
        String source = "class Calculation { int first(int x) { return 2*x/2; }"
                + " int second(int y) { return y+0; }}";
        var ast = MathTestSupport.parse(source);
        int firstOffset = source.indexOf("return 2*x/2");
        int secondOffset = source.indexOf("return y+0");
        boolean admittedLaterMethod = false;
        boolean exhaustedLaterMethod = false;
        int overshoots = 0;
        // Search ordering can change the precise admission threshold. Compare with
        // a selected second method given the measured remaining budget in this JVM.
        for (long budget = 2800; budget <= 8000; budget += 20) {
            var options = options(budget);
            var first = new JavaComputationExtractor().extractRuntime(ast, source, options).regions().getFirst();
            // Two trace operations versus one receive two thirds; reserve half for the alternate domain.
            long ordinaryAllowance = budget / 3;
            var request = new OptimizationRequest(first.plan(), first.trace(), options.kinds(),
                    "java25-numeric/v1", first.assumptions(), options.safety(), options.goal(),
                    new OptimizationBudget(ordinaryAllowance, options.maxStates(), 64, 5000L), CheckedPolicy.NONE);
            var result = new ComputationOptimizer().optimize(request, CancellationToken.NONE);
            if (!(result instanceof BudgetExceeded exceeded) || exceeded.work() <= ordinaryAllowance) continue;
            overshoots++;

            var isolated = MathematicalAnalysis.analyze(ast, source, options(budget - exceeded.work()),
                    new NullProgressMonitor(), secondOffset, 0);
            var combined = MathematicalAnalysis.analyze(ast, source, options,
                    new NullProgressMonitor(), -1, 0);
            assertTrue(combined.diagnostics().stream().anyMatch(d -> d.code().equals("BUDGETEXCEEDED")
                    && d.offset() == firstOffset), combined.diagnostics().toString());
            assertEquals(isolated.evidence().stream().map(e -> e.original()).toList(),
                    combined.evidence().stream().map(e -> e.original()).toList(),
                    "Later search must receive exactly the unspent file budget at " + budget);
            admittedLaterMethod |= !isolated.evidence().isEmpty();
            exhaustedLaterMethod |= isolated.evidence().isEmpty();
            if (admittedLaterMethod && exhaustedLaterMethod) break;
        }
        assertTrue(overshoots > 0, "Exercise a failed attempt that reports more work than its allowance");
        assertTrue(admittedLaterMethod, "Exercise a later method that still fits in the remaining budget");
        assertTrue(exhaustedLaterMethod, "Exercise a later method that no longer fits in the remaining budget");
    }

    private static MathCleanUpOptions options(long budget) {
        var values = new HashMap<>(MathCleanUpOptions.defaults(17).toMap());
        values.put(MathCleanUpOptions.CLEANUP, "true");
        values.put(MathCleanUpOptions.KINDS, "INT");
        values.put(MathCleanUpOptions.MATHEMATICAL_OPT_IN, "true");
        values.put(MathCleanUpOptions.WORK_BUDGET, Long.toString(budget));
        return MathCleanUpOptions.parse(values, 17);
    }
}
