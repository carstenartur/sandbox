/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.functional.core.model;

import java.math.BigInteger;
import java.util.Optional;
import java.util.Set;
import org.sandbox.functional.core.model.UlrExpression.Binary;
import org.sandbox.functional.core.model.UlrExpression.BinaryOperator;
import org.sandbox.functional.core.model.UlrExpression.Overflow;
import org.sandbox.functional.core.model.UlrExpression.Type;

/** Recognizes independent reductions and projects the bounded polynomial fragment of the ULR. */
public final class UlrPolynomial {
    private static final int MAX_DEPTH = 64;

    private UlrPolynomial() { }

    /** Builds an expression in the consumer's existing mathematical representation. */
    public interface Projection<T> {
        T constant(BigInteger value);
        T read(String binding);
        T binary(BinaryOperator operator, T left, T right);
    }

    /**
     * Recognizes an additive update in a bounded, admitted body.
     * The right operand must be independent of every incoming iteration state.
     */
    public static Optional<Binary> additiveUpdate(UlrExpression previous, UlrExpression next,
            Set<String> stateBindings) {
        if (next instanceof Binary update
                && (update.operator() == BinaryOperator.ADD || update.operator() == BinaryOperator.SUBTRACT)
                && update.left().equals(previous) && !readsState(update.right(), stateBindings)) {
            return Optional.of(update);
        }
        return Optional.empty();
    }

    private static boolean readsState(UlrExpression expression, Set<String> stateBindings) {
        return switch (expression) {
            case UlrExpression.Read read -> stateBindings.contains(read.binding());
            case UlrExpression.Constant ignored -> false;
            case UlrExpression.Unary unary -> readsState(unary.operand(), stateBindings);
            case Binary binary -> readsState(binary.left(), stateBindings)
                    || readsState(binary.right(), stateBindings);
        };
    }

    /**
     * Projects constants, binding reads and arithmetic at a single execution width.
     * Leaf reads and constants can widen without moving an operation across a width boundary.
     * The checkpoint accounts for each visited node.
     */
    public static <T> T project(UlrExpression expression, Type type, Overflow overflow, Runnable checkpoint,
            Projection<T> projection) {
        return project(expression, type, overflow, checkpoint, projection, 0);
    }

    private static <T> T project(UlrExpression expression, Type type, Overflow overflow, Runnable checkpoint,
            Projection<T> projection, int depth) {
        checkpoint.run();
        if (depth > MAX_DEPTH || !expression.type().integral()
                || type != Type.INTEGER && expression.type() != type
                        && !(type == Type.INT64 && expression.type() == Type.INT32)) {
            throw unsupported();
        }
        return switch (expression) {
            case UlrExpression.Constant constant -> projection.constant(constant.value());
            case UlrExpression.Read read -> projection.read(read.binding());
            case UlrExpression.Unary ignored -> throw unsupported();
            case Binary binary -> {
                if (binary.type() != type || binary.overflow() != overflow) throw unsupported();
                yield switch (binary.operator()) {
                    case ADD, SUBTRACT, MULTIPLY -> projection.binary(binary.operator(),
                            project(binary.left(), type, overflow, checkpoint, projection, depth + 1),
                            project(binary.right(), type, overflow, checkpoint, projection, depth + 1));
                    default -> throw unsupported();
                };
            }
        };
    }

    private static IllegalArgumentException unsupported() {
        return new IllegalArgumentException("ULR_SUM_OUTSIDE_POLYNOMIAL_FRAGMENT");
    }
}
