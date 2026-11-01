/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.Set;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.SimpleName;

/** Small, source-bound, non-throwing primitive expression fragment for state inspection. */
final class JavaPrimitiveStateExpressions {
    private static final Set<InfixExpression.Operator> INFIX = Set.of(
            InfixExpression.Operator.PLUS, InfixExpression.Operator.MINUS, InfixExpression.Operator.TIMES,
            InfixExpression.Operator.AND, InfixExpression.Operator.OR, InfixExpression.Operator.XOR,
            InfixExpression.Operator.LEFT_SHIFT, InfixExpression.Operator.RIGHT_SHIFT_SIGNED,
            InfixExpression.Operator.RIGHT_SHIFT_UNSIGNED);
    private static final Set<PrefixExpression.Operator> PREFIX = Set.of(
            PrefixExpression.Operator.PLUS, PrefixExpression.Operator.MINUS,
            PrefixExpression.Operator.COMPLEMENT);

    private JavaPrimitiveStateExpressions() { }

    static boolean integral(ITypeBinding type) {
        return type != null && !type.isRecovered() && type.isPrimitive()
                && (type.getName().equals("int") || type.getName().equals("long")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    static String parameterKey(Expression expression) {
        if (unwrap(expression) instanceof SimpleName name
                && name.resolveBinding() instanceof IVariableBinding variable
                && !variable.isRecovered() && variable.isParameter()) {
            return variable.getVariableDeclaration().getKey();
        }
        return null;
    }

    static Expression unwrap(Expression expression) {
        while (expression instanceof ParenthesizedExpression parens) expression = parens.getExpression();
        return expression;
    }

    static boolean literal(Expression expression, long expected) {
        Expression value = unwrap(expression);
        if (!(value instanceof NumberLiteral) || !integral(value.resolveTypeBinding())) return false;
        Object constant = value.resolveConstantExpressionValue();
        return constant instanceof Number number && number.longValue() == expected;
    }

    static boolean admitted(Expression expression, Set<String> parameters, Runnable checkpoint) {
        return admitted(expression, parameters, checkpoint, 0);
    }

    private static boolean admitted(Expression expression, Set<String> parameters,
            Runnable checkpoint, int depth) {
        checkpoint.run();
        if (expression == null || depth > 64 || !integral(expression.resolveTypeBinding())) return false;
        if (expression instanceof ParenthesizedExpression parens) {
            return admitted(parens.getExpression(), parameters, checkpoint, depth + 1);
        }
        if (expression instanceof NumberLiteral) return true;
        if (expression instanceof SimpleName) {
            String key = parameterKey(expression);
            return key != null && parameters.contains(key);
        }
        if (expression instanceof PrefixExpression prefix) {
            return PREFIX.contains(prefix.getOperator())
                    && admitted(prefix.getOperand(), parameters, checkpoint, depth + 1);
        }
        if (!(expression instanceof InfixExpression infix) || !INFIX.contains(infix.getOperator())) return false;
        if (!admitted(infix.getLeftOperand(), parameters, checkpoint, depth + 1)
                || !admitted(infix.getRightOperand(), parameters, checkpoint, depth + 1)) return false;
        for (Object operand : infix.extendedOperands()) {
            if (!admitted((Expression) operand, parameters, checkpoint, depth + 1)) return false;
        }
        return true;
    }
}
