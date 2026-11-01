/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.eclipse.jdt.core.dom.*;

/** Bounded source proofs for immutable primitive locals; never infers a helper contract from its name. */
final class JavaPrimitiveGuardFacts {
    private final CompilationUnit unit;
    private final Set<String> helperBindings = new HashSet<>();
    private int work;
    private JavaPrimitiveGuardFacts(CompilationUnit unit) { this.unit = unit; }

    static Map<String,String> nonnegative(CompilationUnit unit, JavaComputationRegion region) {
        var proof = new JavaPrimitiveGuardFacts(unit);
        try { return proof.prove(region); }
        catch (Unavailable unavailable) { return Map.of(); }
    }
    private Map<String,String> prove(JavaComputationRegion region) {
        ASTNode[] found = {null};
        unit.accept(new ASTVisitor() {
            @Override public boolean preVisit2(ASTNode node) {
                charge();
                if (node.getStartPosition() > region.start()
                        || (long)node.getStartPosition()+node.getLength() < (long)region.start()+region.length()) return false;
                found[0] = node;
                return true;
            }
        });
        ASTNode selected = found[0];
        if (selected == null) return Map.of();
        var bindings = new HashMap<String,IVariableBinding>();
        var rejected = new HashSet<String>();
        selected.accept(new ASTVisitor() {
            @Override public boolean preVisit2(ASTNode node) {
                charge();
                return node.getStartPosition() < (long)region.start()+region.length()
                        && (long)node.getStartPosition()+node.getLength() > region.start();
            }
            @Override public boolean visit(SimpleName name) {
                if (name.isDeclaration() || !region.inputNames().containsValue(name.getIdentifier())) return true;
                if (!(name.resolveBinding() instanceof IVariableBinding binding) || binding.isRecovered() || binding.isField()
                        || !binding.isEffectivelyFinal() && !Modifier.isFinal(binding.getModifiers())
                        || binding.getType().isRecovered() || !binding.getType().isPrimitive()
                        || !Set.of("byte", "short", "char", "int", "long").contains(binding.getType().getName())) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    rejected.add(name.getIdentifier()); return true;
                }
                var previous = bindings.putIfAbsent(name.getIdentifier(), binding);
                if (previous != null && !previous.isEqualTo(binding)) rejected.add(name.getIdentifier());
                return true;
            }
        });
        rejected.forEach(bindings::remove);
        var proven = new HashSet<String>();
        bindings.forEach((name, binding) -> { if ("char".equals(binding.getType().getName())) proven.add(name); }); //$NON-NLS-1$
        for (ASTNode node = selected; node != null; node = node.getParent()) {
            charge();
            if (node instanceof MethodDeclaration || node instanceof LambdaExpression || node instanceof AnonymousClassDeclaration) break;
            if (node instanceof IfStatement condition) {
                if (contains(condition.getThenStatement(), region)) collect(condition.getExpression(), true, bindings, proven, 0);
                else if (condition.getElseStatement() != null && contains(condition.getElseStatement(), region))
                    collect(condition.getExpression(), false, bindings, proven, 0);
            }
            if (node instanceof Block block) for (Object value : block.statements()) {
                charge();
                Statement previous = (Statement)value;
                if ((long)previous.getStartPosition()+previous.getLength() > region.start()) break;
                if (previous instanceof IfStatement guard && guard.getElseStatement() == null && exits(guard.getThenStatement(), new HashSet<>(), 0))
                    collect(guard.getExpression(), false, bindings, proven, 0);
            }
        }
        var facts = new HashMap<String,String>();
        for (String name : proven) facts.put(name, "binding=" + bindings.get(name).getKey() //$NON-NLS-1$
                + "; throwing helper bindings=" + helperBindings.stream().sorted().toList()); //$NON-NLS-1$
        return Map.copyOf(facts);
    }
    private static boolean contains(ASTNode node, JavaComputationRegion region) {
        return region.start() >= node.getStartPosition() && (long)region.start()+region.length() <= (long)node.getStartPosition()+node.getLength();
    }
    private void collect(Expression expression, boolean truth, Map<String,IVariableBinding> bindings, Set<String> proven, int depth) {
        charge(); if (depth > 64) throw new Unavailable();
        expression = unwrap(expression);
        if (expression instanceof PrefixExpression prefix && prefix.getOperator() == PrefixExpression.Operator.NOT) {
            collect(prefix.getOperand(), !truth, bindings, proven, depth+1); return;
        }
        if (!(expression instanceof InfixExpression infix)) return;
        var op = infix.getOperator();
        if (truth && op == InfixExpression.Operator.CONDITIONAL_AND || !truth && op == InfixExpression.Operator.CONDITIONAL_OR) {
            collect(infix.getLeftOperand(), truth, bindings, proven, depth+1);
            collect(infix.getRightOperand(), truth, bindings, proven, depth+1);
            for (Object operand : infix.extendedOperands()) collect((Expression)operand, truth, bindings, proven, depth+1);
            return;
        }
        if (!infix.extendedOperands().isEmpty()) return;
        Expression left = unwrap(infix.getLeftOperand()), right = unwrap(infix.getRightOperand());
        boolean swapped = !(left instanceof SimpleName);
        if (swapped) { Expression temp = left; left = right; right = temp; }
        if (!(left instanceof SimpleName name) || !(name.resolveBinding() instanceof IVariableBinding binding)
                || !bindings.containsKey(name.getIdentifier()) || !bindings.get(name.getIdentifier()).isEqualTo(binding)) return;
        Object value = right.resolveConstantExpressionValue();
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long || value instanceof Character)) return;
        long bound = value instanceof Character character ? character : ((Number)value).longValue();
        boolean atLeast = !swapped && (truth && op == InfixExpression.Operator.GREATER_EQUALS || !truth && op == InfixExpression.Operator.LESS)
                || swapped && (truth && op == InfixExpression.Operator.LESS_EQUALS || !truth && op == InfixExpression.Operator.GREATER);
        boolean greater = !swapped && (truth && op == InfixExpression.Operator.GREATER || !truth && op == InfixExpression.Operator.LESS_EQUALS)
                || swapped && (truth && op == InfixExpression.Operator.LESS || !truth && op == InfixExpression.Operator.GREATER_EQUALS);
        if (atLeast && bound >= 0 || greater && bound >= -1 || (truth && op == InfixExpression.Operator.EQUALS
                || !truth && op == InfixExpression.Operator.NOT_EQUALS) && bound >= 0) proven.add(name.getIdentifier());
    }
    private boolean exits(Statement statement, Set<String> active, int depth) {
        charge(); if (depth > 32) return false;
        if (statement instanceof ThrowStatement || statement instanceof ReturnStatement) return true;
        if (statement instanceof Block block) {
            if (block.statements().isEmpty()) return false;
            // A block can return from this method, but a helper must never return to its caller.
            return exits((Statement)block.statements().getLast(), active, depth+1);
        }
        if (!(statement instanceof ExpressionStatement expression) || !(expression.getExpression() instanceof MethodInvocation call)) return false;
        var binding = call.resolveMethodBinding();
        if (binding == null || binding.isRecovered() || !Modifier.isStatic(binding.getModifiers())
                || binding.getDeclaringClass() == null || binding.getDeclaringClass().isRecovered()) return false;
        String key = binding.getMethodDeclaration().getKey();
        if (key == null || !active.add(key)) return false;
        try {
            ASTNode local = unit.findDeclaringNode(key);
            MethodDeclaration helper = local instanceof MethodDeclaration method ? method : JavaSourceContext.method(unit, binding);
            if (helper == null || helper.getBody() == null || helper.resolveBinding() == null
                    || !helper.resolveBinding().getMethodDeclaration().getKey().equals(key)
                    || !Modifier.isStatic(helper.getModifiers())) return false;
            boolean[] returns = {false};
            helper.getBody().accept(new ASTVisitor() {
                @Override public boolean preVisit2(ASTNode node) { charge(); return !returns[0]; }
                @Override public boolean visit(ReturnStatement returned) { returns[0] = true; return false; }
                @Override public boolean visit(LambdaExpression lambda) { return false; }
                @Override public boolean visit(AnonymousClassDeclaration anonymous) { return false; }
                @Override public boolean visit(TypeDeclaration nested) { return false; }
            });
            boolean throwing = !returns[0] && exits(helper.getBody(), active, depth+1);
            if (throwing) helperBindings.add(key);
            return throwing;
        } finally { active.remove(key); }
    }
    private Expression unwrap(Expression expression) {
        while (expression instanceof ParenthesizedExpression parentheses) { charge(); expression = parentheses.getExpression(); }
        return expression;
    }
    private void charge() {
        if (++work > 4096 || Thread.currentThread().isInterrupted() || !JavaSourceContext.chargeSourceWork(unit, 1)) throw new Unavailable();
    }
    private static final class Unavailable extends RuntimeException { private static final long serialVersionUID = 1L; }
}
