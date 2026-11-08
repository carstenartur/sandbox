/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.junit.jupiter.api.Test;
import org.sandbox.functional.core.model.StateIteration;
import org.sandbox.functional.core.model.UlrExpression;

class JavaTailRecursionReplayTest {
    private static final String BEFORE = "public class Sample { public static long step(int n, long a) { if (n <= 0) return a; return step(n - 1, a + n); } }";
    private static final String AFTER = """
        public class Sample { public static long step(int n, long a) {
            int count = n; long total = a;
            while (!(count <= 0)) {
                final int nextCount = count - 1;
                final long nextTotal = total + count;
                count = nextCount; total = nextTotal;
            }
            return total;
        } }
        """;

    @Test void acceptsAnIndependentlyWrittenLoopWithDifferentNames() {
        assertTrue(verify(AFTER));
    }

    @Test void requiresTheDescriptorToRetainRecursiveArgumentOrder() {
        MethodDeclaration before = method(BEFORE);
        var model = JavaTailRecursionInspector.inspect(before, BEFORE, 100_000, () -> false).iteration().orElseThrow();
        var reversed = new StateIteration(model.states(), model.stopCondition(), model.updates().reversed(),
                model.resultExpression(), model.resultType(), model.updateMode(), model.evaluationOrder(),
                model.termination(), model.obligations());
        assertFalse(JavaTailRecursionReplay.verify(before, method(AFTER), reversed, () -> { }));
    }

    @Test void rejectsDescriptorExpressionsThatDoNotMatchTheOriginalMethod() {
        MethodDeclaration before = method(BEFORE), after = method(AFTER);
        var model = JavaTailRecursionInspector.inspect(before, BEFORE, 100_000, () -> false).iteration().orElseThrow();
        var zero = new UlrExpression.Constant(BigInteger.ZERO, UlrExpression.Type.INT64);
        var stop = (UlrExpression.Binary) model.stopCondition();
        var differentStop = new UlrExpression.Binary(stop.operator(), stop.left(),
                new UlrExpression.Constant(BigInteger.ONE, stop.right().type()), stop.type(), stop.overflow());
        var differentUpdates = List.of(model.updates().getFirst(), new StateIteration.Update(model.states().get(1).bindingKey(), zero));
        for (var forged : List.of(withExpressions(model, differentStop, model.updates(), model.resultExpression()),
                withExpressions(model, model.stopCondition(), differentUpdates, model.resultExpression()),
                withExpressions(model, model.stopCondition(), model.updates(), zero))) {
            assertFalse(JavaTailRecursionReplay.verify(before, after, forged, () -> { }));
        }
    }

    private static StateIteration withExpressions(StateIteration model, UlrExpression stop,
            List<StateIteration.Update> updates, UlrExpression result) {
        return new StateIteration(model.states(), stop, updates, result, model.resultType(),
                model.updateMode(), model.evaluationOrder(), model.termination(), model.obligations());
    }

    @Test void acceptsCanonicalCountdownWithoutChangingSimultaneousState() {
        String counted=canonical();assertTrue(verify(counted));
        for(String altered:List.of(counted.replace("count > 0","count >= 0"),
                counted.replace("count = count - 1","count = count - 2"),
                counted.replace("count = count;","count = n;"),
                counted.replace("total + count","total + count - 1"))) assertFalse(verify(altered),altered);
    }
    @Test void replayChecksTheCanonicalUpdaterAcceptedByTheIterationInspector() {
        String counted=canonical().replace("count = count - 1", "count--");assertTrue(verify(counted));
        assertFalse(verify(counted.replace("count--", "count++")));
        assertFalse(verify(counted.replace("count--", "total--")));
        var model=JavaTailRecursionInspector.inspect(method(BEFORE),BEFORE,100_000,()->false).iteration().orElseThrow();
        assertFalse(JavaTailRecursionReplay.verify(method(BEFORE.replace("n - 1","n - 2")),method(counted),model,()->{}),
            "The emitted decrement must match the actual original argument, not only the supplied model");
    }
    @Test void relocatedCounterInitializationMustReadItsExactOriginalParameter() {
        String relocated=canonical().replace("int count = n;","int count;").replace("count = count;","count = n;");
        assertTrue(verify(relocated));
        for(String altered:List.of(relocated.replace("count = n;","count = 0;"),
                relocated.replace("count = n;","count = n + 1;"),
                relocated.replace("count = n;","count = (int)a;"),
                relocated.replace("count = n;","count = n, total = a;"),
                relocated.replace("int count;","int count = 0;"))) assertFalse(verify(altered),altered);
    }
    private static String canonical() {
        return """
            public class Sample { public static long step(int n, long a) {
                int count = n; long total = a;
                for (count = count; count > 0; count = count - 1) {
                    final long nextTotal = total + count;
                    total = nextTotal;
                }
                return total;
            } }
            """;
    }

    @Test void rejectsWrongArithmeticConditionAndReturnValue() {
        for (String altered : List.of(AFTER.replace("total + count", "total - count"),
                AFTER.replace("count <= 0", "count < 0"), AFTER.replace("return total", "return count"))) {
            assertFalse(verify(altered), altered);
        }
    }

    @Test void rejectsEarlyStateWritesAndSwappedCommitOrder() {
        assertFalse(verify(AFTER.replace("final long nextTotal = total + count;\n        count = nextCount;",
                "count = nextCount;\n        final long nextTotal = total + count;")));
        assertFalse(verify(AFTER.replace("count = nextCount; total = nextTotal;", "total = nextTotal; count = nextCount;")));
    }

    @Test void rejectsChangedInputAndReturnTypesEvenWhenBodyStillCompiles() {
        assertFalse(verify(AFTER.replace("static long step(int n, long a)", "static long step(int n, int a)")));
        assertFalse(verify(AFTER.replace("static long step", "static double step")));
    }

    @Test void rejectsAddedSynchronizationOrChangedDispatch() {
        assertFalse(verify(AFTER.replace("public static long", "public static synchronized long")));
        assertFalse(verify(AFTER.replace("public static long", "public long")));
        assertFalse(verify(AFTER.replace("long step(", "long renamed(")));
    }

    @Test void rejectsExtraStatementsAndAlteredTemporaries() {
        assertFalse(verify(AFTER.replace("return total;", "total++; return total;")));
        assertFalse(verify(AFTER.replace("final int nextCount", "int nextCount")));
        assertFalse(verify(AFTER.replace("final int nextCount", "final long nextCount").replace("count = nextCount", "count = (int) nextCount")));
    }

    @Test void checkpointFailuresAreNotSwallowed() {
        MethodDeclaration before = method(BEFORE), after = method(AFTER);
        var model = JavaTailRecursionInspector.inspect(before, BEFORE, 100_000, () -> false).iteration().orElseThrow();
        class Stopped extends RuntimeException { private static final long serialVersionUID = 1L; }
        assertThrows(Stopped.class, () -> JavaTailRecursionReplay.verify(before, after, model, () -> { throw new Stopped(); }));
    }

    private static boolean verify(String source) {
        MethodDeclaration before = method(BEFORE), after = method(source);
        var model = JavaTailRecursionInspector.inspect(before, BEFORE, 100_000, () -> false).iteration().orElseThrow();
        return JavaTailRecursionReplay.verify(before, after, model, () -> { });
    }

    private static MethodDeclaration method(String source) {
        var parser = ASTParser.newParser(AST.getJLSLatest());
        var options = new HashMap<String, String>(); JavaCore.setComplianceOptions(JavaCore.VERSION_25, options);
        parser.setCompilerOptions(options); parser.setSource(source.toCharArray()); parser.setUnitName("Sample.java");
        parser.setEnvironment(null, null, null, true); parser.setResolveBindings(true);
        var unit = (CompilationUnit) parser.createAST(null);
        for (var problem : unit.getProblems()) assertFalse(problem.isError(), problem.toString());
        var result = new ArrayList<MethodDeclaration>();
        unit.accept(new ASTVisitor() {
            @Override public boolean visit(MethodDeclaration declaration) { result.add(declaration); return false; }
        });
        assertEquals(1, result.size());
        return result.getFirst();
    }
}
