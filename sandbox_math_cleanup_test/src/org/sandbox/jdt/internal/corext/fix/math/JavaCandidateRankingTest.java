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
        var first = ranking.assess(plan, ComputationOptimizer.prepare(plan));
        var cached = ranking.assess(plan, ComputationOptimizer.prepare(plan));
        assertEquals(first.score(), cached.score()); assertTrue(cached.work() > 0); assertTrue(cached.work() < first.work());
        return first.score();
    }
}
