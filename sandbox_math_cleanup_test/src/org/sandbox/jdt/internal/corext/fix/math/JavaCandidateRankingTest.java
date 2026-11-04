/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import de.regelsuche.ast.VariableExpr;
import de.regelsuche.sdk.optimization.*;
import de.regelsuche.search.program.JointComputationPlan;
import java.util.*;
import org.junit.jupiter.api.Test;

class JavaCandidateRankingTest {
    @Test void sourceSpecificBoundsInfluenceTheConsumerScoreAndSafetyCeilingsRemainConservative() {
        assertTrue(score(2048) > score(32) * 100);
        assertEquals(score(-1), score(4096), "A generic receiver safety ceiling is not a measured operand magnitude");
    }
    @Test void postponedModularReductionCannotHideLargerSquaringOperands() {
        var x = new VariableExpr("x"); var p = new VariableExpr("p");
        var square = big(NumericOperation.MULTIPLY, x, x);
        var reduced = big(NumericOperation.MOD, square, p);
        var original = big(NumericOperation.MOD, big(NumericOperation.MULTIPLY, reduced, reduced), p);
        var candidate = big(NumericOperation.MOD, big(NumericOperation.MULTIPLY, square, square), p);
        var scores = scores(original, candidate);
        assertTrue(scores[1] >= scores[0], "Fewer calls must not conceal larger multiplication and division operands: " + Arrays.toString(scores));
    }
    @Test void smallModularPowerStillAllowsAnImmediatelyReducedSquare() {
        var x = new VariableExpr("x"); var p = new VariableExpr("p");
        var original = big(NumericOperation.MOD_POW, x, JavaExpressions.literal(java.math.BigInteger.TWO), p);
        var candidate = big(NumericOperation.MOD, big(NumericOperation.MULTIPLY, x, x), p);
        var scores = scores(original, candidate, Set.of("x", "p"));
        assertTrue(scores[1] < scores[0], "The relative model must retain a cheaper directly reduced square including its receiver guards: " + Arrays.toString(scores));
    }
    @Test void directlyReducedConstantModulusSquareRetainsItsEstablishedAdmission() {
        var x = new VariableExpr("x"); var p = JavaExpressions.literal(java.math.BigInteger.valueOf(101));
        var original = big(NumericOperation.MOD_POW, x, JavaExpressions.literal(java.math.BigInteger.TWO), p);
        var candidate = big(NumericOperation.MOD_MULTIPLY, x, x, p);
        var scores = scores(original, candidate, Set.of("x"));
        assertTrue(scores[1] < scores[0], "One modular result remains one boundary, including guarded constant-modulus support: " + Arrays.toString(scores));
    }
    @Test void sourceCallbackInvalidatesAnEarlierStructuralCandidateCache() {
        var x = new VariableExpr("x"); var p = new VariableExpr("p");
        var square = big(NumericOperation.MULTIPLY, x, x);
        var reduced = big(NumericOperation.MOD, square, p);
        var scores = scores(big(NumericOperation.MOD, big(NumericOperation.MULTIPLY, reduced, reduced), p),
                big(NumericOperation.MOD, big(NumericOperation.MULTIPLY, square, square), p));
        assertTrue(scores[1] > scores[2], "A candidate cached before the source callback must be reassessed against its envelope");
    }
    @Test void relativeNormalizationRoundsUpAndSaturatesWithoutOverflow() {
        assertEquals(11, JavaCandidateRanking.relativeOperationWork(7, 3, 2));
        assertEquals(Long.MAX_VALUE / 160, JavaCandidateRanking.relativeOperationWork(Long.MAX_VALUE, Long.MAX_VALUE, 1));
        assertEquals(7, JavaCandidateRanking.relativeOperationWork(7, Long.MAX_VALUE, Long.MAX_VALUE));
    }
    private static de.regelsuche.ast.Expr big(NumericOperation operation, de.regelsuche.ast.Expr... operands) {
        return JavaExpressions.operation(NumericKind.BIG_INTEGER, operation, operands);
    }
    private static long[] scores(de.regelsuche.ast.Expr original, de.regelsuche.ast.Expr candidate) {
        return scores(original, candidate, Set.of());
    }
    private static long[] scores(de.regelsuche.ast.Expr original, de.regelsuche.ast.Expr candidate, Set<String> guards) {
        var inputs = Map.of("x", NumericKind.BIG_INTEGER.type(), "p", NumericKind.BIG_INTEGER.type());
        var sourcePlan = new JointComputationPlan(inputs, Map.of(), List.of(new JointComputationPlan.Output("out", NumericKind.BIG_INTEGER.type(), original)));
        var candidatePlan = new JointComputationPlan(inputs, Map.of(), List.of(new JointComputationPlan.Output("out", NumericKind.BIG_INTEGER.type(), candidate)));
        var assumptions = Set.of(new SemanticAssumption(SemanticAssumption.Kind.BIG_INTEGER_BIT_LENGTH_BOUND, "x", "4096", "receiver safety ceiling"),
                new SemanticAssumption(SemanticAssumption.Kind.BIG_INTEGER_BIT_LENGTH_BOUND, "p", "4096", "receiver safety ceiling"),
                new SemanticAssumption(SemanticAssumption.Kind.POSITIVE, "p", "", "positive modulus"));
        // The authored temporary stores each intermediate once; it is not a repeated source call.
        var trace = SourceEvaluationTrace.fromPrepared(ComputationOptimizer.prepare(sourcePlan));
        var options = new MathCleanUpOptions(true, Set.of(NumericKind.BIG_INTEGER), SafetyProfile.PRESERVE_JAVA,
                OptimizationGoal.LOWER_ESTIMATED_RUNTIME, 100_000, 2000, false, 17, List.of(), true);
        var request = new OptimizationRequest(sourcePlan, trace, options.kinds(), ComputationOptimizer.SEMANTICS_REVISION,
                assumptions, options.safety(), options.goal(), options.optimizationBudget(100_000), CheckedPolicy.NONE);
        String source = "BigInteger t = x.multiply(x).mod(p); return t.multiply(t).mod(p);";
        var region = new JavaComputationRegion(sourcePlan, trace, List.of(), Map.of("x", "x", "p", "p"), assumptions, guards, Set.of(), 0, source.length());
        var ranking = new JavaCandidateRanking(request, region, options, source);
        var early = ranking.assess(candidatePlan, ComputationOptimizer.prepare(candidatePlan));
        var before = ranking.assessSource(sourcePlan, ComputationOptimizer.prepare(sourcePlan));
        var after = ranking.assess(candidatePlan, ComputationOptimizer.prepare(candidatePlan));
        var cached = ranking.assess(candidatePlan, ComputationOptimizer.prepare(candidatePlan));
        assertEquals(after.score(), cached.score()); assertTrue(cached.work() > 0);
        assertEquals(before.score(), ranking.assessSource(sourcePlan, ComputationOptimizer.prepare(sourcePlan)).score(), "Source score stays structural and stable");
        return new long[] {before.score(), after.score(), early.score()};
    }
    private long score(int bits) {
        var expression = JavaExpressions.operation(NumericKind.BIG_INTEGER, NumericOperation.MULTIPLY, new VariableExpr("x"), new VariableExpr("x"));
        var plan = new JointComputationPlan(Map.of("x", NumericKind.BIG_INTEGER.type()), Map.of(),
                List.of(new JointComputationPlan.Output("out", NumericKind.BIG_INTEGER.type(), expression)));
        var assumptions = bits < 0 ? Set.<SemanticAssumption>of() : Set.of(new SemanticAssumption(
                SemanticAssumption.Kind.BIG_INTEGER_BIT_LENGTH_BOUND, "x", Integer.toString(bits), "test source bound"));
        var trace = SourceEvaluationTrace.fromPlan(plan);
        var options = new MathCleanUpOptions(true, Set.of(NumericKind.BIG_INTEGER), SafetyProfile.PRESERVE_JAVA,
                OptimizationGoal.LOWER_ESTIMATED_RUNTIME, 100_000, 2000, false, 17, List.of(), true);
        var request = new OptimizationRequest(plan, trace, options.kinds(), ComputationOptimizer.SEMANTICS_REVISION,
                assumptions, options.safety(), options.goal(), options.optimizationBudget(100_000), CheckedPolicy.NONE);
        String source = "return x.multiply(x);";
        var region = new JavaComputationRegion(plan, trace, List.of(), Map.of("x", "x"), assumptions, Set.of(), Set.of(), 0, source.length());
        var ranking = new JavaCandidateRanking(request, region, options, source);
        ranking.assessSource(plan, ComputationOptimizer.prepare(plan));
        var first = ranking.assess(plan, ComputationOptimizer.prepare(plan));
        var cached = ranking.assess(plan, ComputationOptimizer.prepare(plan));
        assertEquals(first.score(), cached.score()); assertTrue(cached.work() > 0); assertTrue(cached.work() < first.work());
        return first.score();
    }
}
