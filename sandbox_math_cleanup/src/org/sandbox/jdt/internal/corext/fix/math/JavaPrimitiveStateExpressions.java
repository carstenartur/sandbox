/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.sandbox.functional.core.model.UlrExpression.BinaryOperator.*;
import static org.sandbox.functional.core.model.UlrExpression.Type.*;

import java.math.BigInteger;
import java.util.Map;
import java.util.Set;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.sandbox.functional.core.model.UlrExpression;
import org.sandbox.functional.core.model.UlrExpression.BinaryOperator;
import org.sandbox.functional.core.model.UlrExpression.Overflow;
import org.sandbox.functional.core.model.UlrExpression.Type;
import org.sandbox.functional.core.model.UlrExpression.UnaryOperator;

/** Lowers the source-bound, non-throwing Java primitive state expression fragment. */
final class JavaPrimitiveStateExpressions {
    private static final Map<InfixExpression.Operator, BinaryOperator> INFIX = Map.of(
            InfixExpression.Operator.PLUS, ADD, InfixExpression.Operator.MINUS, SUBTRACT,
            InfixExpression.Operator.TIMES, MULTIPLY, InfixExpression.Operator.AND, BIT_AND,
            InfixExpression.Operator.OR, BIT_OR, InfixExpression.Operator.XOR, BIT_XOR,
            InfixExpression.Operator.LEFT_SHIFT, SHIFT_LEFT_MASKED,
            InfixExpression.Operator.RIGHT_SHIFT_SIGNED, SHIFT_RIGHT_MASKED,
            InfixExpression.Operator.RIGHT_SHIFT_UNSIGNED, SHIFT_RIGHT_UNSIGNED_MASKED,
            InfixExpression.Operator.LESS_EQUALS, LESS_OR_EQUAL);
    private static final Map<PrefixExpression.Operator, UnaryOperator> PREFIX = Map.of(
            PrefixExpression.Operator.PLUS, UnaryOperator.PLUS,
            PrefixExpression.Operator.MINUS, UnaryOperator.NEGATE,
            PrefixExpression.Operator.COMPLEMENT, UnaryOperator.COMPLEMENT);

    private JavaPrimitiveStateExpressions() { }

    static Type type(ITypeBinding binding) {
        if (binding == null || binding.isRecovered() || !binding.isPrimitive()) return null;
        return switch (binding.getName()) {
            case "int" -> INT32; //$NON-NLS-1$
            case "long" -> INT64; //$NON-NLS-1$
            case "boolean" -> BOOLEAN; //$NON-NLS-1$
            default -> null;
        };
    }

    static boolean integral(ITypeBinding type) {
        return integral(type(type));
    }

    private static boolean integral(Type type) {
        return type == INT32 || type == INT64;
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

    static UlrExpression lower(Expression expression, Set<String> parameters, Runnable checkpoint) {
        return lower(expression, parameters, checkpoint, 0);
    }

    private static UlrExpression lower(Expression expression, Set<String> parameters,
            Runnable checkpoint, int depth) {
        checkpoint.run();
        if (expression == null || depth > 64) return null;
        Type type = type(expression.resolveTypeBinding());
        if (type == null) return null;
        if (expression instanceof ParenthesizedExpression parens) {
            return lower(parens.getExpression(), parameters, checkpoint, depth + 1);
        }
        if (expression instanceof NumberLiteral && integral(type)
                && expression.resolveConstantExpressionValue() instanceof Number value) {
            return new UlrExpression.Constant(BigInteger.valueOf(value.longValue()), type);
        }
        if (expression instanceof SimpleName && integral(type)) {
            String key = parameterKey(expression);
            if (key == null || !parameters.contains(key)) return null;
            try {
                return new UlrExpression.Read(key, type);
            } catch (IllegalArgumentException unsupportedIdentity) {
                return null;
            }
        }
        if (expression instanceof PrefixExpression prefix && integral(type)) {
            UnaryOperator operator = PREFIX.get(prefix.getOperator());
            if (operator == null) return null;
            var operand = lower(prefix.getOperand(), parameters, checkpoint, depth + 1);
            return operand != null && operand.type() == type
                    ? new UlrExpression.Unary(operator, operand, type, Overflow.WRAP) : null;
        }
        if (!(expression instanceof InfixExpression infix)) return null;
        BinaryOperator operator = INFIX.get(infix.getOperator());
        if (operator == null) return null;
        var left = lower(infix.getLeftOperand(), parameters, checkpoint, depth + 1);
        if (left == null || !integral(left.type())) return null;
        var right = lower(infix.getRightOperand(), parameters, checkpoint, depth + 1);
        if (right == null || !integral(right.type())) return null;
        if (operator == LESS_OR_EQUAL) {
            return type == BOOLEAN && !infix.hasExtendedOperands()
                    ? new UlrExpression.Binary(operator, left, right, BOOLEAN, Overflow.EXACT) : null;
        }
        if (!integral(type)) return null;
        // JDT flattens repeated operators. Each pair promotes separately before the next
        // operand is evaluated, so an early int addition can wrap before a later long widens it.
        UlrExpression result = pair(operator, left, right);
        for (Object operand : infix.extendedOperands()) {
            var next = lower((Expression) operand, parameters, checkpoint, depth + 1);
            if (next == null || !integral(next.type())) return null;
            result = pair(operator, result, next);
        }
        return result.type() == type ? result : null;
    }

    private static UlrExpression pair(BinaryOperator operator, UlrExpression left, UlrExpression right) {
        Type result = switch (operator) {
            // The distance is independently promoted; its type does not determine the mask.
            case SHIFT_LEFT_MASKED, SHIFT_RIGHT_MASKED, SHIFT_RIGHT_UNSIGNED_MASKED -> left.type();
            default -> left.type() == INT64 || right.type() == INT64 ? INT64 : INT32;
        };
        return new UlrExpression.Binary(operator, left, right, result, Overflow.WRAP);
    }
}
