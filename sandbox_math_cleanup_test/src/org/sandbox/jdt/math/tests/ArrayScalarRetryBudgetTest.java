/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;

import de.regelsuche.sdk.optimization.CancellationToken;
import de.regelsuche.sdk.optimization.CheckedPolicy;
import de.regelsuche.sdk.optimization.ComputationExplanations;
import de.regelsuche.sdk.optimization.ComputationOptimizer;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationBudget;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.OptimizationRequest;
import de.regelsuche.sdk.optimization.OptimizationResult;
import de.regelsuche.sdk.optimization.SafetyProfile;
import java.util.List;
import java.util.Set;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.corext.fix.math.JavaComputationExtractor;
import org.sandbox.jdt.internal.corext.fix.math.JavaComputationRegion;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;

class ArrayScalarRetryBudgetTest {
    @Test void captureRejectionChargesSearchAndExplanationBeforeRetry() {
        String source = "class Calculation { int compute(int[] words, int i) {"
                + " int word = words[i]; return word ^ 0; }}";
        var ast = MathTestSupport.parse(source);
        var options = options(100_000L);
        var region = new JavaComputationExtractor().extractRuntime(ast, source, options).regions().getFirst();
        var request = request(region, options, options.workBudget());
        var candidate = assertInstanceOf(OptimizationResult.Candidate.class,
                new ComputationOptimizer().optimize(request, CancellationToken.NONE));
        long presentation = ComputationExplanations.describe(request, candidate, CancellationToken.NONE)
                .explanation().orElseThrow().presentationWork();
        assertTrue(presentation > 0);
        long spent = candidate.work() + presentation;

        var result = MathematicalAnalysis.analyze(ast, source, options, new NullProgressMonitor(), -1, 0);
        assertEquals(1, result.evidence().size(), result.diagnostics().toString());
        assertEquals("return word ^ 0;", result.evidence().getFirst().original());
        assertEquals(options.workBudget() - spent,
                result.evidence().getFirst().explanation().verified().derivation().orElseThrow().generationBudget());

        var exhausted = MathematicalAnalysis.analyze(ast, source, options(1L), new NullProgressMonitor(), -1, 0);
        assertFalse(exhausted.changed(), exhausted.diagnostics().toString());
        assertTrue(exhausted.diagnostics().stream().anyMatch(d -> d.code().equals("BUDGETEXCEEDED")));
        assertTrue(exhausted.diagnostics().stream().noneMatch(d -> d.code().equals("ARRAY_SCALAR_RETRY")));
    }

    @Test void boundedArrayAttemptLeavesOnlyUnspentWorkForScalarRetryAndLaterMethod() {
        String source = "class Calculation { int compute(int[] words, int i) { int word = "
                + "words[i] + ".repeat(31) + "words[i]; return word ^ 0; } int later(int value) { return value"
                + " + 0".repeat(32) + "; }}";
        var ast = MathTestSupport.parse(source);
        var options = options(100_000L);
        var region = new JavaComputationExtractor().extractRuntime(ast, source, options).regions().getFirst();
        var complete = assertInstanceOf(OptimizationResult.Candidate.class,
                new ComputationOptimizer().optimize(request(region, options, options.workBudget()), CancellationToken.NONE));
        // The anytime search may use less work at a smaller allowance. Locate its
        // transition to a bounded failure instead of assuming a fixed work ratio.
        long allowance = 1, upper = complete.work();
        OptimizationResult.BudgetExceeded bounded = null;
        while (allowance + 1 < upper) {
            long probe = (allowance + upper) / 2;
            var result = new ComputationOptimizer().optimize(request(region, options, probe), CancellationToken.NONE);
            if (result instanceof OptimizationResult.BudgetExceeded exceeded) {
                allowance = probe;
                bounded = exceeded;
            } else upper = probe;
        }
        assertNotNull(bounded);
        // Both original regions contain thirty-two operations and receive equal shares.
        long budget = allowance * 2;
        long remaining = budget - Math.max(allowance, bounded.work());
        assertTrue(remaining > 0);

        var result = MathematicalAnalysis.analyze(ast, source, options(budget), new NullProgressMonitor(), -1, 0);
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("BUDGETEXCEEDED") && d.offset() == region.start()));
        var retried = result.evidence().stream().filter(e -> e.original().equals("return word ^ 0;")).findFirst()
                .orElseThrow(() -> new AssertionError(result.diagnostics()));
        // The scalar retry shares the remainder with the thirty-two-operation later method.
        assertEquals(remaining / 33, retried.explanation().verified().derivation().orElseThrow().generationBudget());
        assertEquals(1, result.diagnostics().stream().filter(d -> d.code().equals("ARRAY_SCALAR_RETRY")).count());
    }

    private static MathCleanUpOptions options(long work) {
        return new MathCleanUpOptions(true, Set.of(NumericKind.INT), SafetyProfile.PRESERVE_JAVA,
                OptimizationGoal.LOWER_ESTIMATED_RUNTIME, work, 2000, false, 17, List.of());
    }

    private static OptimizationRequest request(JavaComputationRegion region,
            MathCleanUpOptions options, long work) {
        return new OptimizationRequest(region.plan(), region.trace(), options.kinds(), ComputationOptimizer.SEMANTICS_REVISION,
                region.assumptions(), options.safety(), options.goal(), new OptimizationBudget(work, options.maxStates(), 64, 5000L),
                CheckedPolicy.NONE);
    }
}
