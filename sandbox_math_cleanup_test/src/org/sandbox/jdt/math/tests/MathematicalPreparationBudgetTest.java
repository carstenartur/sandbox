/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;

import de.regelsuche.sdk.optimization.CancellationToken;
import de.regelsuche.sdk.optimization.CheckedPolicy;
import de.regelsuche.sdk.optimization.ComputationOptimizer;
import de.regelsuche.sdk.optimization.OptimizationBudget;
import de.regelsuche.sdk.optimization.OptimizationRequest;
import de.regelsuche.sdk.optimization.OptimizationResult;
import de.regelsuche.search.program.JointComputationPlan;
import java.util.HashMap;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.corext.fix.math.JavaComputationExtractor;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;

class MathematicalPreparationBudgetTest {
    // Preparation can reach its structural cap before the SDK can report request work.
    private static final long BUDGET = 2L * JointComputationPlan.MAX_NODES - 1;

    @Test void preparationFailureWithTwoToFourStatementsCannotSpendItsCapAgainOnRetries() {
        for (int statements = 2; statements <= 4; statements++) {
            var fixture = fixture(statements);
            var analysis = MathematicalAnalysis.analyze(fixture.ast(), fixture.source(), fixture.options(),
                    new NullProgressMonitor(), -1, 0);
            assertFalse(analysis.diagnostics().stream().anyMatch(d -> d.code().equals("MODULAR_STATEMENT_RETRY")),
                    "The preparation cap exhausts this file budget before statement retries: " + analysis.diagnostics());
            assertBudgetExhaustedBeforeLaterMethod(fixture, analysis);
        }
    }

    @Test void terminalPreparationFailureStillChargesItsCapBeforeTheNextMethod() {
        var fixture = fixture(1);
        var analysis = MathematicalAnalysis.analyze(fixture.ast(), fixture.source(), fixture.options(),
                new NullProgressMonitor(), -1, 0);
        assertBudgetExhaustedBeforeLaterMethod(fixture, analysis);
    }

    private static void assertBudgetExhaustedBeforeLaterMethod(Fixture fixture, MathematicalAnalysis.Analysis analysis) {
        int later = fixture.source().indexOf("return base.modPow");
        assertTrue(analysis.diagnostics().stream().anyMatch(d -> d.code().equals("BUDGET_EXCEEDED") && d.offset() == later),
                "The later method must receive no work after the failed preparation cap: " + analysis.diagnostics());
        assertTrue(analysis.replacements().isEmpty(), analysis.diagnostics().toString());
        assertTrue(analysis.evidence().isEmpty());
    }

    private static Fixture fixture(int statements) {
        StringBuilder source = new StringBuilder("""
                import java.math.BigInteger;
                public class Calculation {
                    private static final BigInteger MODULUS = new BigInteger("65537");
                    static BigInteger[] first(long seed) {
                        BigInteger base = BigInteger.valueOf(seed);
                        BigInteger large =
                """);
        source.append(sum(0, 256)).append(";\n");
        for (int index = 1; index < statements; index++) {
            source.append("BigInteger square").append(index)
                    .append(" = base.modPow(BigInteger.TWO, MODULUS);\n");
        }
        source.append("return new BigInteger[] {large");
        for (int index = 1; index < statements; index++) source.append(", square").append(index);
        source.append("""
                }; }
                    static BigInteger later(long seed) {
                        BigInteger base = BigInteger.valueOf(seed);
                        return base.modPow(BigInteger.TWO, MODULUS);
                    }
                }
                """);
        var values = new HashMap<>(MathCleanUpOptions.defaults(17).toMap());
        values.put(MathCleanUpOptions.CLEANUP, "true");
        values.put(MathCleanUpOptions.KINDS, "BIG_INTEGER");
        values.put(MathCleanUpOptions.MATHEMATICAL_OPT_IN, "true");
        values.put(MathCleanUpOptions.WORK_BUDGET, Long.toString(BUDGET));
        var options = MathCleanUpOptions.parse(values, 17);
        var ast = MathTestSupport.parse(source.toString());
        var regions = new JavaComputationExtractor().extractRuntime(ast, source.toString(), options).regions();
        assertEquals(2, regions.size());
        var first = regions.getFirst();
        assertEquals(statements, first.outputs().size() + first.internalBindings().size(),
                "Four-output fragment extraction cannot subdivide this source region");
        long regionAllowance = BUDGET / regions.size();
        long requestAllowance = statements > 1 ? regionAllowance / 2 : regionAllowance;
        var request = new OptimizationRequest(first.plan(), first.trace(), options.kinds(),
                "java25-numeric/v1", first.assumptions(), options.safety(), options.goal(),
                new OptimizationBudget(requestAllowance, options.maxStates(), 64, 5000L), CheckedPolicy.NONE);
        var result = new ComputationOptimizer().optimize(request, CancellationToken.NONE);
        assertInstanceOf(OptimizationResult.Unsupported.class, result,
                "Exercise unreported preparation work, not an ordinary request-budget failure");
        assertTrue(result.toString().contains("plan preparation work bound exceeded"), result.toString());
        return new Fixture(source.toString(), ast, options);
    }

    /** A shallow expression with enough distinct nodes to exceed preparation's work cap. */
    private static String sum(int first, int count) {
        if (count == 1) return "base.add(BigInteger.valueOf(" + first + "))";
        int left = count / 2;
        return sum(first, left) + ".add(" + sum(first + left, count - left) + ")";
    }

    private record Fixture(String source, org.eclipse.jdt.core.dom.CompilationUnit ast, MathCleanUpOptions options) { }
}
