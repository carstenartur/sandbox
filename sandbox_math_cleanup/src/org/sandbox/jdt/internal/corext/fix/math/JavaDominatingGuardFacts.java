/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TypeLiteral;

/**
 * Bounded, binding-based facts from dominating branches and early exits. Never infers facts
 * from a name, comment, unrelated branch, field or a different execution frame.
 * This proves the complete existing receiver guard; it does not weaken one.
 */
final class JavaDominatingGuardFacts {
    private static final String BIG_INTEGER = "java.math.BigInteger"; //$NON-NLS-1$
    private static final int NON_NULL = 1, EXACT = 2, BOUNDED = 4, POSITIVE = 8;
    private static final int MAX_NODES = 4096, MAX_ANCESTORS = 128;

    private JavaDominatingGuardFacts() { }

    static Set<String> provenReceivers(CompilationUnit unit, int start, int length,
            Map<String, Boolean> requirePositive) {
        if (requirePositive.isEmpty() || start < 0 || length <= 0 || Thread.currentThread().isInterrupted()) return Set.of();
        long end = (long) start + length;
        if (end > (long) unit.getStartPosition() + unit.getLength()) return Set.of();
        ASTNode selected = NodeFinder.perform(unit, start, length);
        if (selected == null) return Set.of();
        var bindings = new HashMap<String, IVariableBinding>();
        var rejectedNames = new HashSet<String>();
        int[] inspected = {0};
        selected.accept(new ASTVisitor() {
            @Override public boolean preVisit2(ASTNode node) {
                return ++inspected[0] <= MAX_NODES && !Thread.currentThread().isInterrupted()
                        && (long) node.getStartPosition() + node.getLength() > start && node.getStartPosition() < end;
            }
            @Override public boolean visit(SimpleName name) {
                if (name.isDeclaration() || !requirePositive.containsKey(name.getIdentifier())) return true;
                if (!(name.resolveBinding() instanceof IVariableBinding binding) || binding.isRecovered()
                        || binding.isField() || !(Modifier.isFinal(binding.getModifiers()) || binding.isEffectivelyFinal())
                        || binding.getType().isRecovered()
                        || !BIG_INTEGER.equals(binding.getType().getQualifiedName())) {
                    rejectedNames.add(name.getIdentifier());
                    return true;
                }
                IVariableBinding previous = bindings.putIfAbsent(name.getIdentifier(), binding);
                if (previous != null && !previous.isEqualTo(binding)) rejectedNames.add(name.getIdentifier());
                return true;
            }
        });
        if (inspected[0] > MAX_NODES || Thread.currentThread().isInterrupted()) return Set.of();
        rejectedNames.forEach(bindings::remove);
        if (bindings.isEmpty()) return Set.of();
        var facts = new HashMap<String, Integer>();
        int ancestors = 0;
        for (ASTNode node = selected; node != null; node = node.getParent()) {
            if (++ancestors > MAX_ANCESTORS || Thread.currentThread().isInterrupted()) return Set.of();
            if (node instanceof MethodDeclaration || node instanceof LambdaExpression || node instanceof AnonymousClassDeclaration) break;
            if (node instanceof IfStatement condition) {
                var branch = condition.getThenStatement();
                if (start >= branch.getStartPosition() && end <= (long) branch.getStartPosition() + branch.getLength()) {
                    if (!collectFacts(condition.getExpression(), true, bindings, facts, inspected)) return Set.of();
                }
            }
            if (node instanceof Block block) {
                for (Object value : block.statements()) {
                    if (++inspected[0] > MAX_NODES || Thread.currentThread().isInterrupted()) return Set.of();
                    Statement previous = (Statement) value;
                    if ((long) previous.getStartPosition() + previous.getLength() > start) break;
                    // Only a directly preceding guard without else proves its false
                    // condition on every path to the remainder of this block.
                    if (previous instanceof IfStatement guard && guard.getElseStatement() == null
                            && exits(guard.getThenStatement(), inspected)
                            && !collectFacts(guard.getExpression(), false, bindings, facts, inspected)) return Set.of();
                }
                if (inspected[0] > MAX_NODES || Thread.currentThread().isInterrupted()) return Set.of();
            }
        }
        var proven = new HashSet<String>();
        bindings.keySet().forEach(name -> {
            int needed = NON_NULL | EXACT | BOUNDED | (Boolean.TRUE.equals(requirePositive.get(name)) ? POSITIVE : 0);
            if ((facts.getOrDefault(name, 0) & needed) == needed) proven.add(name);
        });
        return Set.copyOf(proven);
    }

    /** Do not guess whether calls, loops, try/finally or nested conditions terminate. */
    private static boolean exits(Statement statement, int[] inspected) {
        while (statement instanceof Block block) {
            if (++inspected[0] > MAX_NODES || Thread.currentThread().isInterrupted()) return false;
            if (block.statements().size() != 1) return false;
            statement = (Statement) block.statements().getFirst();
        }
        return statement instanceof ReturnStatement || statement instanceof ThrowStatement;
    }

    private record KnownCondition(Expression expression, boolean truth) { }

    private static boolean collectFacts(Expression condition, boolean truth, Map<String, IVariableBinding> bindings,
            Map<String, Integer> facts, int[] inspected) {
        var pending = new ArrayDeque<KnownCondition>();
        pending.push(new KnownCondition(condition, truth));
        while (!pending.isEmpty()) {
            if (++inspected[0] > MAX_NODES || Thread.currentThread().isInterrupted()) return false;
            var known = pending.pop();
            Expression expression = known.expression();
            if (expression instanceof ParenthesizedExpression parentheses) {
                pending.push(new KnownCondition(parentheses.getExpression(), known.truth()));
                continue;
            }
            if (expression instanceof PrefixExpression prefix && prefix.getOperator() == PrefixExpression.Operator.NOT) {
                pending.push(new KnownCondition(prefix.getOperand(), !known.truth()));
                continue;
            }
            if (!(expression instanceof InfixExpression infix)) continue;
            var operator = infix.getOperator();
            if (known.truth() && operator == InfixExpression.Operator.CONDITIONAL_AND
                    || !known.truth() && operator == InfixExpression.Operator.CONDITIONAL_OR) {
                pending.push(new KnownCondition(infix.getLeftOperand(), known.truth()));
                pending.push(new KnownCondition(infix.getRightOperand(), known.truth()));
                for (Object operand : infix.extendedOperands()) pending.push(new KnownCondition((Expression) operand, known.truth()));
                continue;
            }
            // False conjunctions and true disjunctions establish no individual term.
            if (!infix.extendedOperands().isEmpty()) continue;
            if (!known.truth()) operator = opposite(operator);
            if (operator == null) continue;
            Expression left = unwrap(infix.getLeftOperand()), right = unwrap(infix.getRightOperand());
            if (operator == InfixExpression.Operator.NOT_EQUALS && right instanceof NullLiteral) {
                record(left, NON_NULL, bindings, facts);
            } else if (operator == InfixExpression.Operator.NOT_EQUALS && left instanceof NullLiteral) {
                record(right, NON_NULL, bindings, facts);
            } else if (operator == InfixExpression.Operator.EQUALS && exactClass(right) && method(left, "getClass", "java.lang.Object")) { //$NON-NLS-1$ //$NON-NLS-2$
                record(((MethodInvocation) left).getExpression(), NON_NULL | EXACT, bindings, facts);
            } else if (operator == InfixExpression.Operator.EQUALS && exactClass(left) && method(right, "getClass", "java.lang.Object")) { //$NON-NLS-1$ //$NON-NLS-2$
                record(((MethodInvocation) right).getExpression(), NON_NULL | EXACT, bindings, facts);
            } else if (right instanceof NumberLiteral literal && literal.resolveConstantExpressionValue() instanceof Number number
                    && (number instanceof Integer || number instanceof Long)) {
                long value = number.longValue();
                if (method(left, "bitLength", BIG_INTEGER) && value >= 0 //$NON-NLS-1$
                        && ((operator == InfixExpression.Operator.LESS && value <= 4096)
                            || (operator == InfixExpression.Operator.LESS_EQUALS && value < 4096))) {
                    record(((MethodInvocation) left).getExpression(), BOUNDED, bindings, facts);
                }
                if (method(left, "signum", BIG_INTEGER) //$NON-NLS-1$
                        && ((operator == InfixExpression.Operator.GREATER && value == 0)
                            || (operator == InfixExpression.Operator.GREATER_EQUALS && value == 1)
                            || (operator == InfixExpression.Operator.EQUALS && value == 1))) {
                    record(((MethodInvocation) left).getExpression(), POSITIVE, bindings, facts);
                }
            }
        }
        return true;
    }

    private static InfixExpression.Operator opposite(InfixExpression.Operator operator) {
        if (operator == InfixExpression.Operator.EQUALS) return InfixExpression.Operator.NOT_EQUALS;
        if (operator == InfixExpression.Operator.NOT_EQUALS) return InfixExpression.Operator.EQUALS;
        if (operator == InfixExpression.Operator.LESS) return InfixExpression.Operator.GREATER_EQUALS;
        if (operator == InfixExpression.Operator.LESS_EQUALS) return InfixExpression.Operator.GREATER;
        if (operator == InfixExpression.Operator.GREATER) return InfixExpression.Operator.LESS_EQUALS;
        if (operator == InfixExpression.Operator.GREATER_EQUALS) return InfixExpression.Operator.LESS;
        return null;
    }

    private static Expression unwrap(Expression expression) {
        int depth = 0;
        while (expression instanceof ParenthesizedExpression parentheses && depth++ < MAX_ANCESTORS)
            expression = parentheses.getExpression();
        return expression;
    }

    private static boolean exactClass(Expression expression) {
        return expression instanceof TypeLiteral literal && literal.getType().resolveBinding() != null
                && !literal.getType().resolveBinding().isRecovered()
                && BIG_INTEGER.equals(literal.getType().resolveBinding().getQualifiedName());
    }

    private static boolean method(Expression expression, String name, String owner) {
        if (!(expression instanceof MethodInvocation call) || !call.arguments().isEmpty() || call.getExpression() == null) return false;
        var binding = call.resolveMethodBinding();
        return binding != null && !binding.isRecovered() && name.equals(binding.getName())
                && binding.getDeclaringClass() != null && owner.equals(binding.getDeclaringClass().getQualifiedName());
    }

    private static void record(Expression expression, int fact, Map<String, IVariableBinding> bindings,
            Map<String, Integer> facts) {
        if (!(unwrap(expression) instanceof SimpleName name)) return;
        var expected = bindings.get(name.getIdentifier());
        if (expected != null && name.resolveBinding() instanceof IVariableBinding actual && expected.isEqualTo(actual))
            facts.merge(name.getIdentifier(), fact, (left, right) -> left | right);
    }
}
