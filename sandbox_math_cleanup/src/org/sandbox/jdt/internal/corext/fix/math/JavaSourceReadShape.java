/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.Set;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.ThisExpression;

/**
 * Source-shape admission only. Every admitted occurrence still executes once,
 * in order, even if its scalar value becomes dead. Neither final fields nor
 * virtual buffer calls are assumed pure, equal, non-null or in bounds.
 */
final class JavaSourceReadShape {
    private static final Set<String> INTEGRAL = Set.of("byte", "short", "char", "int", "long"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
    private static final Set<String> BUFFERS = Set.of("java.nio.ByteBuffer", "java.nio.ShortBuffer", //$NON-NLS-1$ //$NON-NLS-2$
            "java.nio.CharBuffer", "java.nio.IntBuffer", "java.nio.LongBuffer"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    private static final Set<InfixExpression.Operator> TOTAL_INDEX_OPERATORS = Set.of(
            InfixExpression.Operator.PLUS, InfixExpression.Operator.MINUS, InfixExpression.Operator.TIMES,
            InfixExpression.Operator.AND, InfixExpression.Operator.OR, InfixExpression.Operator.XOR,
            InfixExpression.Operator.LEFT_SHIFT, InfixExpression.Operator.RIGHT_SHIFT_SIGNED,
            InfixExpression.Operator.RIGHT_SHIFT_UNSIGNED);

    private JavaSourceReadShape() { }

    static boolean additionalRead(Expression expression) {
        return bufferMethod(expression) != null || finalField(expression) != null;
    }

    static boolean read(Expression expression) {
        return expression instanceof ArrayAccess || additionalRead(expression);
    }

    static boolean supported(Expression expression, Set<String> regionalLocals) {
        if (expression instanceof ArrayAccess array) {
            return arrayReceiver(array.getArray(), regionalLocals)
                    && stableIndex(array.getIndex(), regionalLocals, 0);
        }
        if (bufferMethod(expression) != null && expression instanceof MethodInvocation call) {
            return localReceiver(call.getExpression(), regionalLocals)
                    && stableIndex((Expression) call.arguments().getFirst(), regionalLocals, 0);
        }
        IVariableBinding field = finalField(expression);
        if (field == null) return false;
        if (expression instanceof SimpleName) return true; // Original lexical field lookup is retained verbatim.
        Expression receiver = expression instanceof FieldAccess access ? access.getExpression()
                : ((QualifiedName) expression).getQualifier();
        receiver = unparenthesized(receiver);
        if (receiver instanceof Name name && name.resolveBinding() instanceof ITypeBinding)
            return Modifier.isStatic(field.getModifiers());
        return receiver instanceof ThisExpression self && self.getQualifier() == null
                || localReceiver(receiver, regionalLocals);
    }

    /** Every field receiver is reread in its original expression; no alias or stability claim. */
    private static boolean arrayReceiver(Expression expression, Set<String> regionalLocals) {
        Expression receiver = unparenthesized(expression);
        IVariableBinding binding = receiver instanceof Name name && name.resolveBinding() instanceof IVariableBinding variable
                ? variable : receiver instanceof FieldAccess access ? access.resolveFieldBinding() : null;
        if (binding == null || binding.isRecovered()) return false;
        ITypeBinding type = binding.getType();
        if (type == null || type.isRecovered() || !type.isArray() || type.getDimensions() != 1
                || !integral(type.getElementType())) return false;
        if (!binding.isField())
            return receiver instanceof SimpleName && !regionalLocals.contains(binding.getKey());
        if (Modifier.isVolatile(binding.getModifiers())) return false;
        // Preserve lexical, this-qualified and type-qualified source, including
        // static initialization. Object chains and effectful qualifiers stay out.
        if (receiver instanceof SimpleName) return true;
        if (receiver instanceof FieldAccess access) {
            Expression qualifier = unparenthesized(access.getExpression());
            return qualifier instanceof ThisExpression self && self.getQualifier() == null;
        }
        return receiver instanceof QualifiedName qualified
                && qualified.getQualifier().resolveBinding() instanceof ITypeBinding
                && Modifier.isStatic(binding.getModifiers());
    }

    /** Produced locals and throwing prefixes may not move after a following source read. */
    static boolean requiresBoundary(Statement statement, Set<String> regionalLocals, boolean totalPrefix) {
        boolean[] boundary = {false};
        statement.accept(new ASTVisitor() {
            @Override public boolean preVisit2(ASTNode node) {
                if (boundary[0]) return false;
                if (!(node instanceof Expression expression) || !read(expression)) return true;
                if (!totalPrefix) { boundary[0] = true; return false; }
                expression.accept(new ASTVisitor() {
                    @Override public boolean visit(SimpleName name) {
                        if (name.resolveBinding() instanceof IVariableBinding binding
                                && regionalLocals.contains(binding.getKey())) boundary[0] = true;
                        return !boundary[0];
                    }
                });
                return false;
            }
        });
        return boundary[0];
    }

    private static IVariableBinding finalField(Expression expression) {
        IVariableBinding binding = expression instanceof Name name && name.resolveBinding() instanceof IVariableBinding variable
                ? variable : expression instanceof FieldAccess access ? access.resolveFieldBinding() : null;
        return binding != null && !binding.isRecovered() && binding.isField()
                && Modifier.isFinal(binding.getModifiers()) && !Modifier.isVolatile(binding.getModifiers())
                && binding.getConstantValue() == null && integral(binding.getType()) ? binding : null;
    }

    private static IMethodBinding bufferMethod(Expression expression) {
        if (!(expression instanceof MethodInvocation call) || call.arguments().size() != 1
                || !call.typeArguments().isEmpty() || !"get".equals(call.getName().getIdentifier())) return null; //$NON-NLS-1$
        IMethodBinding method = call.resolveMethodBinding();
        if (method == null || method.isRecovered() || Modifier.isStatic(method.getModifiers())
                || method.isVarargs() || method.getDeclaringClass() == null
                || !BUFFERS.contains(method.getDeclaringClass().getErasure().getQualifiedName())
                || method.getParameterTypes().length != 1 || !integral(method.getReturnType())
                || !"int".equals(method.getParameterTypes()[0].getQualifiedName())) return null; //$NON-NLS-1$
        return method;
    }

    private static boolean localReceiver(Expression receiver, Set<String> regionalLocals) {
        receiver = unparenthesized(receiver);
        return receiver instanceof SimpleName name && name.resolveBinding() instanceof IVariableBinding binding
                && !binding.isRecovered() && !binding.isField() && !regionalLocals.contains(binding.getKey());
    }

    private static Expression unparenthesized(Expression expression) {
        while (expression instanceof ParenthesizedExpression parenthesized) expression = parenthesized.getExpression();
        return expression;
    }

    private static boolean integral(ITypeBinding binding) {
        return binding != null && !binding.isRecovered() && binding.isPrimitive() && INTEGRAL.contains(binding.getName());
    }

    private static boolean stableIndex(Expression index, Set<String> regionalLocals, int depth) {
        if (depth > 64 || !integral(index.resolveTypeBinding())) return false;
        if (index instanceof NumberLiteral || index instanceof CharacterLiteral) return true;
        if (index instanceof Name name && name.resolveBinding() instanceof IVariableBinding binding) {
            if (binding.isRecovered() || regionalLocals.contains(binding.getKey())) return false;
            if (!binding.isField()) return name instanceof SimpleName;
            // Constant variables cannot trigger class initialization. An object-qualified
            // constant is deliberately excluded: do not silently omit its receiver evaluation.
            return binding.getConstantValue() != null && (name instanceof SimpleName
                    || name instanceof QualifiedName qualified && qualified.getQualifier().resolveBinding() instanceof ITypeBinding);
        }
        if (index instanceof ParenthesizedExpression parentheses)
            return stableIndex(parentheses.getExpression(), regionalLocals, depth + 1);
        if (index instanceof CastExpression cast)
            return stableIndex(cast.getExpression(), regionalLocals, depth + 1);
        if (index instanceof PrefixExpression prefix)
            return (prefix.getOperator() == PrefixExpression.Operator.PLUS || prefix.getOperator() == PrefixExpression.Operator.MINUS
                    || prefix.getOperator() == PrefixExpression.Operator.COMPLEMENT)
                    && stableIndex(prefix.getOperand(), regionalLocals, depth + 1);
        if (index instanceof InfixExpression infix) {
            if (!TOTAL_INDEX_OPERATORS.contains(infix.getOperator())
                    || !stableIndex(infix.getLeftOperand(), regionalLocals, depth + 1)
                    || !stableIndex(infix.getRightOperand(), regionalLocals, depth + 1)) return false;
            for (Object operand : infix.extendedOperands())
                if (!stableIndex((Expression) operand, regionalLocals, depth + 1)) return false;
            return true;
        }
        return false;
    }
}
