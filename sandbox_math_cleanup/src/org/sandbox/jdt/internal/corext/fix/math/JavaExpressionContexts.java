/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.LinkedHashMap;
import java.util.List;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.YieldStatement;

/** Structural boundaries for branch-local arithmetic and deferred callable bodies. */
final class JavaExpressionContexts {
    private JavaExpressionContexts() { }

    static boolean inLambda(ASTNode node) {
        for (node = node.getParent(); node != null; node = node.getParent()) {
            if (node instanceof LambdaExpression) return true;
            if (node instanceof MethodDeclaration || node instanceof Initializer || node instanceof AnonymousClassDeclaration) return false;
        }
        return false;
    }

    static IMethodBinding callable(ASTNode node) {
        for (node = node.getParent(); node != null; node = node.getParent()) {
            if (node instanceof LambdaExpression lambda) return lambda.resolveMethodBinding();
            if (node instanceof MethodDeclaration method) return method.resolveBinding();
            if (node instanceof Initializer || node instanceof AnonymousClassDeclaration) return null;
        }
        return null;
    }

    static boolean executable(ASTNode node) {
        IMethodBinding method = callable(node);
        return method != null && !method.isRecovered();
    }

    static Block enclosingBlock(ASTNode node) {
        for (node = node.getParent(); node != null; node = node.getParent())
            if (node instanceof Block block) return block;
        return null;
    }

    static boolean expressionAnchor(Expression expression) {
        ASTNode parent = expression.getParent();
        if (parent instanceof LambdaExpression lambda && lambda.getBody() == expression)
            return lambda.resolveMethodBinding() != null && !lambda.resolveMethodBinding().getReturnType().getName().equals("void"); //$NON-NLS-1$
        if (parent instanceof ConditionalExpression conditional)
            return conditional.getThenExpression() == expression || conditional.getElseExpression() == expression;
        if (parent instanceof YieldStatement yield) return yield.getExpression() == expression;
        if (parent instanceof ReturnStatement returned)
            return returned.getExpression() == expression && !(returned.getParent() instanceof Block);
        if (parent instanceof Assignment assignment && assignment.getRightHandSide() == expression
                && assignment.getOperator() == Assignment.Operator.ASSIGN
                && assignment.getLeftHandSide() instanceof SimpleName name
                && name.resolveBinding() instanceof IVariableBinding binding && !binding.isField()
                && assignment.getParent() instanceof ExpressionStatement statement)
            return !(statement.getParent() instanceof Block);
        return false;
    }

    /** The AST node, not a synthetic output name, distinguishes expression-only edits. */
    static boolean expressionOnly(CompilationUnit unit, JavaComputationRegion region) {
        if (region.outputs().size() != 1 || !region.internalBindings().isEmpty()) return false;
        var output = region.outputs().getFirst();
        return output.returnValue() && output.statementStart() == region.start() && output.statementLength() == region.length()
                && NodeFinder.perform(unit, region.start(), region.length()) instanceof Expression expression
                && expression.getStartPosition() == region.start() && expression.getLength() == region.length()
                && expressionAnchor(expression);
    }

    static JavaComputationEmitter.Emission typedExpression(JavaComputationEmitter.Emission emission, JavaComputationRegion region) {
        if (!emission.statements().isBlank() || !region.arrayReads().isEmpty() || !region.receiverGuards().isEmpty())
            throw new IllegalArgumentException("EXPRESSION_CONTEXT_REQUIRES_STATEMENTS"); //$NON-NLS-1$
        var values = new LinkedHashMap<String, String>();
        for (var output : region.outputs()) values.put(output.id(), "(" + MathematicalAnalysis.javaType(output.declaredKind()) //$NON-NLS-1$
                + ") (" + emission.outputValues().get(output.id()) + ")"); //$NON-NLS-1$ //$NON-NLS-2$
        return new JavaComputationEmitter.Emission(emission.statements(), values);
    }

    /** Compilation alone cannot detect a silently changed overload, SAM or conditional type. */
    static void verifyBoundary(CompilationUnit before, CompilationUnit after, JavaComputationRegion region, int replacementLength) {
        verifyBoundaryChanges(before, after, region, List.of(new Change(region.start(), region.length(), replacementLength)));
    }

    static void verifyBoundary(CompilationUnit before, CompilationUnit after, JavaComputationRegion region,
            List<MathematicalAnalysis.Replacement> replacements) {
        verifyBoundaryChanges(before, after, region, replacements.stream()
                .map(edit -> new Change(edit.offset(), edit.length(), edit.replacement().length())).toList());
    }

    private record Change(int start, int length, int replacementLength) {
        int delta() { return replacementLength - length; }
    }

    private static void verifyBoundaryChanges(CompilationUnit before, CompilationUnit after, JavaComputationRegion region, List<Change> changes) {
        ASTNode anchor = NodeFinder.perform(before, region.start(), region.length());
        for (ASTNode node = anchor; node != null; node = node.getParent()) {
            int start = node.getStartPosition(), length = node.getLength();
            for (var change : changes) {
                if ((long)change.start() + change.length() <= node.getStartPosition()) start += change.delta();
                else if (change.start() >= node.getStartPosition()
                        && (long)change.start() + change.length() <= (long)node.getStartPosition() + node.getLength()) length += change.delta();
            }
            ASTNode generated = NodeFinder.perform(after, start, length);
            while (generated != null && generated.getNodeType() != node.getNodeType()
                    && generated.getParent() != null && generated.getParent().getStartPosition() == start
                    && generated.getParent().getLength() == length) generated = generated.getParent();
            if (generated == null || generated.getNodeType() != node.getNodeType()) {
                // The replaced expression itself may have a different concrete syntax.
                if (node == anchor && node instanceof Expression) {
                    if (!(generated instanceof Expression expression) || !same(((Expression) node).resolveTypeBinding(), expression.resolveTypeBinding()))
                        throw new IllegalArgumentException("EXPRESSION_CONTEXT_TYPE_CHANGED"); //$NON-NLS-1$
                    continue;
                }
                throw new IllegalArgumentException("CALLABLE_CONTEXT_SHAPE_CHANGED: " + node.getClass().getSimpleName()); //$NON-NLS-1$
            }
            if (node instanceof Expression expression && generated instanceof Expression other
                    && !same(expression.resolveTypeBinding(), other.resolveTypeBinding()))
                throw new IllegalArgumentException("EXPRESSION_CONTEXT_TYPE_CHANGED"); //$NON-NLS-1$
            if (node instanceof LambdaExpression lambda && generated instanceof LambdaExpression other
                    && !same(lambda.resolveMethodBinding(), other.resolveMethodBinding()))
                throw new IllegalArgumentException("LAMBDA_CONTEXT_SAM_CHANGED"); //$NON-NLS-1$
            IMethodBinding originalBinding = invocation(node), generatedBinding = invocation(generated);
            if (originalBinding != null && !same(originalBinding, generatedBinding))
                throw new IllegalArgumentException("CALLABLE_CONTEXT_OVERLOAD_CHANGED"); //$NON-NLS-1$
        }
    }

    private static IMethodBinding invocation(ASTNode node) {
        if (node instanceof MethodInvocation call) return call.resolveMethodBinding();
        if (node instanceof SuperMethodInvocation call) return call.resolveMethodBinding();
        if (node instanceof ClassInstanceCreation call) return call.resolveConstructorBinding();
        return null;
    }

    private static boolean same(IBinding left, IBinding right) {
        return left != null && right != null && !left.isRecovered() && !right.isRecovered() && left.getKey().equals(right.getKey());
    }
}
