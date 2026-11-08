/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import static org.sandbox.functional.core.model.UlrExpression.Overflow.*;
import static org.sandbox.functional.core.model.UlrExpression.Type.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.SourceRange;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jface.text.Document;
import org.junit.jupiter.api.Test;
import org.sandbox.functional.core.model.FunctionalExpression.TypedBody;
import org.sandbox.functional.core.model.NumericIteration.CountedSum;
import org.sandbox.functional.core.model.UlrExpression;
import de.regelsuche.sdk.optimization.OptimizationBudget;
import de.regelsuche.sdk.optimization.iteration.PolynomialSumVerifier;
import de.regelsuche.symbol.SymbolId;

class UlrSumProjectionTest extends JavaContextTestSupport {
    private static final SymbolId INDEX = new SymbolId(new UUID(0, 0), 1);
    private static final UlrExpression.Read READ = new UlrExpression.Read(INDEX.canonicalText(), INT32);
    private static final UlrExpression.Constant ZERO = new UlrExpression.Constant(BigInteger.ZERO, INT32);
    private static final OptimizationBudget BUDGET = new OptimizationBudget(200_000, 10, 10, 5_000);

    @Test void checkedTermCannotEnterWrappingProof() {
        var term = new UlrExpression.Binary(UlrExpression.BinaryOperator.ADD, READ, READ, INT32, CHECKED);
        var sum = new CountedSum(new TypedBody(READ, term), ZERO, BigInteger.ONE, ZERO, WRAP);
        assertThrows(IllegalArgumentException.class, () -> UlrSumProjection.project(sum, Set.of(), () -> {}));
    }

    @Test void productionJavaFormsUseTheSameSeriesVerifier() throws Exception {
        String method = "public class Sample { public static int run(int n) { %s } }";
        for (String body : List.of("int sum=0; for(int i=0;i<n;i++) sum+=i; return sum;",
                "return java.util.stream.IntStream.range(0,n).map(i->i).sum();")) {
            String source = method.formatted(body);
            compare(source, applyVerified(source, analyze(source)), -5, 0, 1, 32, 65_537);
        }

        String tail = """
                public class Sample {
                    public static int run(int n) { return sum(n,0); }
                    private static int sum(int n,int s) {
                        if(n<=0) return s;
                        return sum(n-1,s+(n-1));
                    }
                }
                """;
        var values = new HashMap<>(options().toMap());
        values.put(MathCleanUpOptions.TAIL_RECURSION, "true");
        var options = MathCleanUpOptions.parse(values, 17);
        var transfer = JavaTailRecursionRewrite.prepare(JavaEarlyExitGuardFactsTest.parse(tail), tail,
                tail.indexOf("private static"), options, new NullProgressMonitor());
        String source = transfer.plan().orElseThrow(() -> new AssertionError(transfer.reason())).applyTo(tail);
        var ast = JavaEarlyExitGuardFactsTest.parse(source);
        var loops = new ArrayList<SourceRange>();
        ast.accept(new ASTVisitor() {
            @Override public boolean visit(ForStatement node) {
                loops.add(new SourceRange(node.getStartPosition(), node.getLength()));
                return true;
            }
        });
        assertEquals(1, loops.size());
        String accelerated = applyVerified(source,
                MathematicalAnalysis.analyzeTailLoops(ast, source, options, new NullProgressMonitor(), loops));
        compare(tail, accelerated, -5, 0, 1, 32, 200);
        try (var compiled = compile(accelerated)) {
            assertEquals(-2_147_450_880, compiled.loadClass("Sample").getMethod("run", int.class).invoke(null, 65_537));
        }
    }

    private static String applyVerified(String source, MathematicalAnalysis.Analysis analysis) throws Exception {
        var proof = analysis.evidence().stream().map(MathematicalAnalysis.VerifiedRegion::runtimeIteration)
                .filter(java.util.Objects::nonNull).findFirst()
                .orElseThrow(() -> new AssertionError(analysis.diagnostics().toString()));
        var plan = proof.sums().getFirst();
        assertTrue(new PolynomialSumVerifier().verify(plan.source(), plan, BUDGET, () -> false).verified());
        var document = new Document(source);
        var undo = analysis.newEdit().apply(document);
        String rewritten = document.get();
        assertNotEquals(source, rewritten);
        undo.apply(document);
        assertEquals(source, document.get());
        return rewritten;
    }

    private void compare(String original, String rewritten, int... inputs) throws Exception {
        try (var before = compile(original); var after = compile(rewritten)) {
            var first = before.loadClass("Sample").getMethod("run", int.class);
            var second = after.loadClass("Sample").getMethod("run", int.class);
            for (int n : inputs) assertEquals(first.invoke(null,n), second.invoke(null,n), "n=" + n);
        }
    }
}
