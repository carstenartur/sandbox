/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;

import de.regelsuche.ast.Expr;
import de.regelsuche.ast.VariableExpr;
import de.regelsuche.sdk.optimization.*;
import de.regelsuche.search.program.JointComputationPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.core.runtime.OperationCanceledException;
import org.junit.jupiter.api.Test;

/** Exercises presentation decisions with actual optimized and reverified plans. */
class MathExplanationReviewTest {
    @Test void preservingSingleOutputBelowThresholdIsNotNontrivial() {
        var context = context(7, 1, SafetyProfile.PRESERVE_JAVA, false);
        assertEquals(7, context.candidate().cost().sourceCost().operationWork());
        assertFalse(context.render(CancellationToken.NONE).nontrivial());
    }

    @Test void thresholdIsInclusiveAtEightOriginalOperations() {
        var context = context(8, 1, SafetyProfile.PRESERVE_JAVA, false);
        assertEquals(8, context.candidate().cost().sourceCost().operationWork());
        assertTrue(context.render(CancellationToken.NONE).nontrivial());
    }

    @Test void multipleOutputsAreNontrivialBelowTheCostThreshold() {
        var context = context(1, 2, SafetyProfile.PRESERVE_JAVA, false);
        assertTrue(context.candidate().cost().sourceCost().operationWork() < 8);
        assertTrue(context.render(CancellationToken.NONE).nontrivial());
    }

    @Test void recordedPremisesAreNontrivialBelowTheCostThreshold() {
        var context = context(1, 1, SafetyProfile.PRESERVE_JAVA, true);
        assertEquals(1, context.candidate().cost().sourceCost().operationWork());
        assertTrue(context.render(CancellationToken.NONE).nontrivial());
    }

    @Test void bothNonpreservingContractsAreNontrivialBelowTheCostThreshold() {
        for (var safety : List.of(SafetyProfile.GUARDED_FALLBACK, SafetyProfile.CHECKED_THROW)) {
            var context = context(1, 1, safety, false);
            assertEquals(1, context.candidate().cost().sourceCost().operationWork());
            assertTrue(context.render(CancellationToken.NONE).nontrivial(), safety.toString());
        }
    }

    @Test void cancellationAtRendererEntryIsNotAnUnsupportedCandidate() {
        var context = context(1, 1, SafetyProfile.PRESERVE_JAVA, false);
        assertThrows(OperationCanceledException.class, () -> context.render(() -> true));
    }

    @Test void cancellationAfterFormattingDoesNotPublishPartialText() {
        var context = context(1, 1, SafetyProfile.PRESERVE_JAVA, false);
        AtomicInteger checks = new AtomicInteger();
        assertThrows(OperationCanceledException.class, () -> context.render(() -> checks.incrementAndGet() >= 2));
        assertEquals(2, checks.get());
    }

    @Test void threadInterruptionIsPropagatedWithoutClearingTheFlag() {
        var context = context(1, 1, SafetyProfile.PRESERVE_JAVA, false);
        try {
            Thread.currentThread().interrupt();
            assertThrows(OperationCanceledException.class, () -> context.render(CancellationToken.NONE));
        } finally {
            assertTrue(Thread.interrupted(), "The renderer must preserve the flag; only this test clears it");
        }
    }

    private static Context context(int operations, int outputCount, SafetyProfile safety, boolean premise) {
        Expr expression = new VariableExpr("input");
        for (int i = 0; i < operations; i++) {
            expression = JavaExpressions.operation(NumericKind.INT, NumericOperation.ADD,
                    expression, JavaExpressions.literal(0));
        }
        var outputs = new ArrayList<JointComputationPlan.Output>();
        var bindings = new ArrayList<JavaComputationRegion.OutputBinding>();
        for (int i = 0; i < outputCount; i++) {
            String id = "out" + i;
            outputs.add(new JointComputationPlan.Output(id, NumericKind.INT.type(), expression));
            bindings.add(new JavaComputationRegion.OutputBinding(id, "result" + i, NumericKind.INT,
                    0, 0, 0, 0, true, "binding" + i));
        }
        var plan = new JointComputationPlan(Map.of("input", NumericKind.INT.type()), Map.of(), outputs);
        Set<SemanticAssumption> assumptions = premise
                ? Set.of(new SemanticAssumption(SemanticAssumption.Kind.POSITIVE, "input", "", "Explicit test caller contract"))
                : Set.of();
        var options = new MathCleanUpOptions(true, Set.of(NumericKind.INT), safety,
                OptimizationGoal.READABILITY, 1_000_000L, 2000, safety == SafetyProfile.CHECKED_THROW, 17, List.of());
        var trace = SourceEvaluationTrace.fromPlan(plan);
        var request = new OptimizationRequest(plan, trace, options.kinds(), ComputationOptimizer.SEMANTICS_REVISION,
                assumptions, safety, options.goal(), new OptimizationBudget(1_000_000L, 2000, 64, 5000L),
                safety == SafetyProfile.CHECKED_THROW ? CheckedPolicy.EXPLICIT_DEFAULT : CheckedPolicy.NONE);
        var result = new ComputationOptimizer().optimize(request, CancellationToken.NONE);
        var candidate = assertInstanceOf(OptimizationResult.Candidate.class, result, result.toString());
        var explanation = ComputationExplanations.describe(request, candidate, CancellationToken.NONE);
        assertInstanceOf(VerificationResult.Verified.class, explanation.verification());
        var region = new JavaComputationRegion(plan, trace, bindings, Map.of("input", "x"), assumptions,
                Set.of("x"), Set.of(), 0, 0);
        var emission = new JavaComputationEmitter().emit(candidate.prepared(), region.inputNames(), region.reservedNames(), 17);
        return new Context(explanation.explanation().orElseThrow(), request, candidate, region, emission, options);
    }

    private record Context(ComputationExplanations.Explanation explanation, OptimizationRequest request,
            OptimizationResult.Candidate candidate, JavaComputationRegion region,
            JavaComputationEmitter.Emission emission, MathCleanUpOptions options) {
        MathExplanation render(CancellationToken token) {
            return MathExplanation.render(explanation, request, candidate, region, emission, options, token);
        }
    }
}
