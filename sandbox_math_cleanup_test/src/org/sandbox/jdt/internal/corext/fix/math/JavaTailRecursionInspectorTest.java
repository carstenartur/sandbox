/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.junit.jupiter.api.Test;
import org.sandbox.functional.core.model.StateIteration;

class JavaTailRecursionInspectorTest {
    @Test void describesATerminatingPrimitiveSumAsSimultaneousStateUpdates() {
        var result = inspect("""
            static long accumulate(int n, long result) {
                if (n <= 0) return result;
                return accumulate(n - 1, result + n);
            }
            """);
        var model = result.iteration().orElseThrow();
        assertEquals(List.of("int", "long"), model.states().stream().map(StateIteration.State::type).toList());
        assertEquals(List.of("n - 1", "result + n"), model.updates().stream().map(StateIteration.Update::expression).toList());
        assertEquals("n <= 0", model.stopCondition());
        assertEquals("result", model.resultExpression());
        assertEquals(StateIteration.UpdateMode.SIMULTANEOUS, model.updateMode());
        assertEquals(StateIteration.EvaluationOrder.LEFT_TO_RIGHT, model.evaluationOrder());
        assertTrue(model.obligations().contains(StateIteration.Obligation.STACK_BEHAVIOR_CHANGE));
        assertTrue(model.obligations().contains(StateIteration.Obligation.JAVA_EVALUATION_REPLAY));
        assertTrue(result.work() > 0);
    }

    @Test void namesAndCounterPositionDoNotSelectTheAlgorithm() {
        var result = inspect("""
            static long advance(long west, long remaining, long east) {
                if ((remaining) <= 0L) { return west + east; }
                return advance(east, remaining - 1L, west + east);
            }
            """);
        var model = result.iteration().orElseThrow();
        assertEquals(model.states().get(1).bindingKey(), model.termination().counterBinding());
        assertEquals(List.of("east", "remaining - 1L", "west + east"),
                model.updates().stream().map(StateIteration.Update::expression).toList());
        assertEquals("west + east", model.resultExpression());
    }

    @Test void admitsAnExplicitSameTypeStaticReceiverButNotAnObjectReceiver() {
        assertTrue(inspect("""
            static int step(int n, int a) {
                if (n <= 0) return a;
                return Sample.step(n - 1, a * 2);
            }
            """).iteration().isPresent());
        assertTrue(inspect("""
            static Sample current;
            static int step(int n, int a) {
                if (n <= 0) return a;
                return current.step(n - 1, a * 2);
            }
            """).iteration().isEmpty());
    }

    @Test void rejectsNonTailAndMultipleRecursiveCalls() {
        for (String expression : List.of("n + step(n - 1, a)", "step(n - 1, a) + step(n - 1, a)")) {
            assertTrue(inspect(method(expression)).iteration().isEmpty(), expression);
        }
    }

    @Test void rejectsAnOverloadInsteadOfMatchingTheMethodName() {
        assertTrue(inspect("""
            static long step(int n, long a) {
                if (n <= 0) return a;
                return step((long)n - 1, a);
            }
            static long step(long n, long a) { return a; }
            """).iteration().isEmpty());
    }

    @Test void rejectsVirtualDispatchAndSynchronizedMethods() {
        assertTrue(inspect("""
            long step(int n, long a) {
                if (n <= 0) return a;
                return step(n - 1, a);
            }
            """).iteration().isEmpty());
        assertTrue(inspect("""
            static synchronized long step(int n, long a) {
                if (n <= 0) return a;
                return step(n - 1, a);
            }
            """).iteration().isEmpty());
    }

    @Test void rejectsEffectsAndUnprovedMethodCallsInArguments() {
        for (String expression : List.of("step(--n, a)", "step(n - 1, a++)",
                "step(n - 1, Math.abs(a))", "step(n - 1, a / n)")) {
            assertTrue(inspect(method(expression)).iteration().isEmpty(), expression);
        }
    }

    @Test void rejectsFieldReadsAndObjectNumericTypes() {
        assertTrue(inspect("static long external = 1; " + method("step(n - 1, external)"))
                .iteration().isEmpty());
        assertTrue(inspect("""
            static java.math.BigInteger step(int n, java.math.BigInteger a) {
                if (n <= 0) return a;
                return step(n - 1, a);
            }
            """).iteration().isEmpty());
    }

    @Test void refusesAnUnprovedCountdownOrTerminationPredicate() {
        for (String expression : List.of("step(n + 1, a)", "step(n, a)", "step(n - 2, a)")) {
            assertTrue(inspect(method(expression)).iteration().isEmpty(), expression);
        }
        assertTrue(inspect(method("step(n - 1, a)").replace("n <= 0", "n == 0"))
                .iteration().isEmpty());
    }

    @Test void doesNotLoseFinallyBlocksOrComments() {
        assertTrue(inspect("""
            static long step(int n, long a) {
                try {
                    if (n <= 0) return a;
                    return step(n - 1, a);
                } finally { System.gc(); }
            }
            """).iteration().isEmpty());
        assertTrue(inspect(method("step(n - 1, a)").replace("return a;", "return a; // retain this explanation\n"))
                .iteration().isEmpty());
    }

    @Test void admitsOnlyBoundPrimitiveArithmetic() {
        assertTrue(inspect(method("step(n - 1, (a << 1) ^ n)")) .iteration().isPresent());
        assertTrue(inspect("""
            static double step(int n, double a) {
                if (n <= 0) return a;
                return step(n - 1, a * 2);
            }
            """).iteration().isEmpty());
    }

    @Test void doesNotTreatUnresolvedOrMalformedBindingsAsProof() {
        String source = "class Sample { " + method("step(n - 1, a)") + " }";
        assertTrue(inspectSource(source, false, 20_000, () -> false).iteration().isEmpty());
        assertTrue(inspect(method("step(n - 1, missingName)")).iteration().isEmpty());
    }

    @Test void stopsWithoutAPartialDescriptorAtCancellationOrWorkExhaustion() {
        String source = "class Sample { " + method("step(n - 1, a)") + " }";
        var cancelled = inspectSource(source, true, 20_000, () -> true);
        assertTrue(cancelled.iteration().isEmpty());
        assertEquals("CANCELLED", cancelled.reason());
        var exhausted = inspectSource(source, true, 1, () -> false);
        assertTrue(exhausted.iteration().isEmpty());
        assertEquals("WORK_LIMIT", exhausted.reason());
    }

    private static String method(String expression) {
        return "static long step(int n, long a) { if (n <= 0) return a; return " + expression + "; }";
    }

    private static JavaTailRecursionInspector.Inspection inspect(String members) {
        return inspectSource("class Sample { " + members + " }", true, 20_000, () -> false);
    }

    private static JavaTailRecursionInspector.Inspection inspectSource(String source,
            boolean bindings, long maximum, BooleanSupplier cancelled) {
        var parser = ASTParser.newParser(AST.getJLSLatest());
        var options = new HashMap<String, String>();
        JavaCore.setComplianceOptions(JavaCore.VERSION_25, options);
        parser.setCompilerOptions(options);
        parser.setSource(source.toCharArray());
        parser.setUnitName("Sample.java");
        parser.setEnvironment(null, null, null, true);
        parser.setResolveBindings(bindings);
        parser.setBindingsRecovery(false);
        var unit = (CompilationUnit) parser.createAST(null);
        var methods = new ArrayList<MethodDeclaration>();
        unit.accept(new ASTVisitor() {
            @Override public boolean visit(MethodDeclaration method) {
                if (!method.isConstructor()) methods.add(method);
                return false;
            }
        });
        assertFalse(methods.isEmpty(), "Test fixture has no method");
        return JavaTailRecursionInspector.inspect(methods.getFirst(), source, maximum, cancelled);
    }
}
