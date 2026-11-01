/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.sandbox.jdt.internal.corext.fix.math.JavaPrimitiveStateExpressions.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.eclipse.jdt.core.dom.*;
import org.sandbox.functional.core.model.StateIteration;

/**
 * Source-bound direct tail recursion inspection; never authorizes an edit.
 * The caller supplies the compilation unit's matching source snapshot. This
 * narrow producer admits a static primitive method with a zero-exit countdown.
 * It does not infer stack equivalence, erase comments, or extend stream admission.
 */
final class JavaTailRecursionInspector {
    record Inspection(Optional<StateIteration> iteration, String reason, long work) { }
    private JavaTailRecursionInspector() { }

    static Inspection inspect(MethodDeclaration method, String source, long maximum, BooleanSupplier cancelled) {
        Objects.requireNonNull(method); Objects.requireNonNull(source); Objects.requireNonNull(cancelled);
        if (maximum < 0) throw new IllegalArgumentException("Negative inspection budget"); //$NON-NLS-1$
        var work = new Work(maximum, cancelled);
        try {
            work.tick();
            StateIteration result = inspectChecked(method, source, work);
            return new Inspection(Optional.ofNullable(result), result == null
                    ? "OUTSIDE_DIRECT_TAIL_FRAGMENT" : "INSPECTED_NOT_AUTHORIZED", work.used); //$NON-NLS-1$ //$NON-NLS-2$
        } catch (Stopped stopped) {
            return new Inspection(Optional.empty(), stopped.getMessage(), work.used);
        }
    }

    private static StateIteration inspectChecked(MethodDeclaration method, String source, Work work) {
        if (source.length() > 1_000_000 || method.getLength() > StateIteration.MAX_FRAGMENT_LENGTH
                || method.getBody() == null || method.isConstructor()) return null;
        IMethodBinding binding = method.resolveBinding();
        if (binding == null || binding.isRecovered() || !Modifier.isStatic(binding.getModifiers())
                || Modifier.isSynchronized(binding.getModifiers()) || binding.isVarargs()
                || binding.getTypeParameters().length != 0 || !integral(binding.getReturnType())) return null;
        if (!sourceAvailable(method, source, work)) return null;
        var statements = method.getBody().statements();
        if (statements.size() != 2 || !(statements.getFirst() instanceof IfStatement exit)
                || exit.getElseStatement() != null || !(statements.get(1) instanceof ReturnStatement tail)) return null;
        ReturnStatement base = returned(exit.getThenStatement());
        if (base == null || base.getExpression() == null || tail.getExpression() == null
                || !(unwrap(tail.getExpression()) instanceof MethodInvocation call)
                || !selfCall(call, binding)) return null;
        int count = method.parameters().size();
        if (count == 0 || count > StateIteration.MAX_STATES || call.arguments().size() != count) return null;
        var states = new ArrayList<StateIteration.State>();
        var parameters = new HashSet<String>();
        for (Object entry : method.parameters()) {
            work.tick();
            var parameter = (SingleVariableDeclaration) entry;
            IVariableBinding variable = parameter.resolveBinding();
            if (variable == null || variable.isRecovered() || !integral(variable.getType())) return null;
            String key = variable.getVariableDeclaration().getKey();
            if (key == null || !parameters.add(key)) return null;
            states.add(new StateIteration.State(key, parameter.getName().getIdentifier(), variable.getType().getName()));
        }
        String counter = counter(exit.getExpression());
        if (counter == null || !parameters.contains(counter)
                || !admitted(base.getExpression(), parameters, work::tick)) return null;
        var updates = new ArrayList<StateIteration.Update>();
        for (int i = 0; i < count; i++) {
            Expression argument = (Expression) call.arguments().get(i);
            if (!admitted(argument, parameters, work::tick)) return null;
            String key = states.get(i).bindingKey();
            if (key.equals(counter) && !decrementsOne(argument, counter)) return null;
            updates.add(new StateIteration.Update(key, text(argument, source, work)));
        }
        return new StateIteration(states, text(exit.getExpression(), source, work), updates,
                text(base.getExpression(), source, work), StateIteration.UpdateMode.SIMULTANEOUS,
                StateIteration.EvaluationOrder.LEFT_TO_RIGHT, new StateIteration.CountDown(counter, 1, 0),
                Set.of(StateIteration.Obligation.SOURCE_REVALIDATION,
                        StateIteration.Obligation.JAVA_EVALUATION_REPLAY,
                        StateIteration.Obligation.STACK_BEHAVIOR_CHANGE));
    }

    private static boolean selfCall(MethodInvocation call, IMethodBinding owner) {
        IMethodBinding target = call.resolveMethodBinding();
        if (target == null || target.isRecovered()
                || !target.getMethodDeclaration().isEqualTo(owner.getMethodDeclaration())) return false;
        if (call.getExpression() == null) return true;
        return call.getExpression() instanceof Name name && name.resolveBinding() instanceof ITypeBinding type
                && !type.isRecovered() && type.getTypeDeclaration().isEqualTo(owner.getDeclaringClass().getTypeDeclaration());
    }

    private static ReturnStatement returned(Statement statement) {
        if (statement instanceof ReturnStatement returned) return returned;
        if (statement instanceof Block block && block.statements().size() == 1
                && block.statements().getFirst() instanceof ReturnStatement returned) return returned;
        return null;
    }

    private static String counter(Expression condition) {
        if (!(unwrap(condition) instanceof InfixExpression test)
                || test.getOperator() != InfixExpression.Operator.LESS_EQUALS
                || !test.extendedOperands().isEmpty() || !literal(test.getRightOperand(), 0)) return null;
        return parameterKey(test.getLeftOperand());
    }

    private static boolean decrementsOne(Expression argument, String key) {
        return unwrap(argument) instanceof InfixExpression update
                && update.getOperator() == InfixExpression.Operator.MINUS && update.extendedOperands().isEmpty()
                && key.equals(parameterKey(update.getLeftOperand())) && literal(update.getRightOperand(), 1);
    }

    private static boolean sourceAvailable(MethodDeclaration method, String source, Work work) {
        if (!(method.getRoot() instanceof CompilationUnit unit)) return false;
        text(method, source, work);
        for (var problem : unit.getProblems()) {
            work.tick();
            if (problem.isError()) return false;
        }
        for (Object entry : unit.getCommentList()) {
            work.tick();
            var comment = (Comment) entry;
            int first = method.getBody().getStartPosition();
            int last = first + method.getBody().getLength();
            if (comment.getStartPosition() < last && comment.getStartPosition() + comment.getLength() > first) return false;
        }
        method.accept(new ASTVisitor() {
            private int depth;
            @Override public void preVisit(ASTNode node) {
                work.tick();
                if (++depth > 64 || (node.getFlags() & (ASTNode.MALFORMED | ASTNode.RECOVERED)) != 0) {
                    throw new Stopped("MALFORMED_OR_DEEP_SOURCE"); //$NON-NLS-1$
                }
            }
            @Override public void postVisit(ASTNode node) { depth--; }
        });
        return true;
    }

    private static String text(ASTNode node, String source, Work work) {
        int start = node.getStartPosition(), length = node.getLength();
        if (start < 0 || length < 1 || start > source.length() - length) {
            throw new Stopped("SOURCE_RANGE_MISMATCH"); //$NON-NLS-1$
        }
        work.charge(1L + length / 64L);
        return source.substring(start, start + length);
    }

    private static final class Work {
        private final long maximum;
        private final BooleanSupplier cancelled;
        private long used;
        Work(long maximum, BooleanSupplier cancelled) { this.maximum = maximum; this.cancelled = cancelled; }
        void tick() { charge(1); }
        void charge(long units) {
            if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new Stopped("CANCELLED"); //$NON-NLS-1$
            if (units > maximum - used) throw new Stopped("WORK_LIMIT"); //$NON-NLS-1$
            used += units;
        }
    }

    private static final class Stopped extends RuntimeException {
        private static final long serialVersionUID = 1L;
        Stopped(String reason) { super(reason, null, false, false); }
    }
}
