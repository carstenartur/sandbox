/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;

import de.regelsuche.sdk.optimization.CancellationToken;
import de.regelsuche.sdk.optimization.CheckedPolicy;
import de.regelsuche.sdk.optimization.ComputationOptimizer;
import de.regelsuche.sdk.optimization.ComputationExplanations;
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
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;

class MathematicalPresentationBudgetTest {
    @Test void presentationWorkReducesBudgetAvailableToFollowingRegions() {
        String source = "public class Calculation { public static int first(int x) {return x+0;}"
                + " public static int second(int y) {return y+0;} }";
        var options = new MathCleanUpOptions(true, Set.of(NumericKind.INT), SafetyProfile.PRESERVE_JAVA,
                OptimizationGoal.LOWER_ESTIMATED_RUNTIME, 100_000L, 20_000, false, 17, List.of());
        var ast = MathTestSupport.parse(source);
        var result = MathematicalAnalysis.analyze(ast, source, options, new NullProgressMonitor(), -1, 0);
        assertEquals(2, result.evidence().size(), result.diagnostics().toString());

        var first = result.evidence().getFirst().explanation().verified();
        var second = result.evidence().getLast().explanation().verified();
        assertTrue(first.presentationWork() > 0);
        long firstAllowance = first.derivation().orElseThrow().generationBudget();
        assertEquals(options.workBudget() / 2, firstAllowance);
        // Independently obtain the SDK's measured search work; the report records
        // the allowance actually passed to the following region's search.
        var region = new JavaComputationExtractor().extractRuntime(ast, source, options).regions().getFirst();
        var request = new OptimizationRequest(region.plan(), region.trace(), options.kinds(),
                ComputationOptimizer.SEMANTICS_REVISION, region.assumptions(), options.safety(), options.goal(),
                new OptimizationBudget(firstAllowance, options.maxStates(), 64, 5000L), CheckedPolicy.NONE);
        var candidate = assertInstanceOf(OptimizationResult.Candidate.class,
                new ComputationOptimizer().optimize(request, CancellationToken.NONE));
        var described = ComputationExplanations.describe(request, candidate, options.workBudget() - candidate.work(), CancellationToken.NONE);
        assertTrue(described.work() > first.presentationWork(), "Independent verification and replay must also consume file budget");
        assertEquals(options.workBudget() - candidate.work() - described.work(),
                second.derivation().orElseThrow().generationBudget());
    }
}
