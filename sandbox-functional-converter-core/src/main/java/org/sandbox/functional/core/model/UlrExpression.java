/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.functional.core.model;

import java.math.BigInteger;
import java.util.Objects;

/** A typed, source-independent expression in the ULR's integral fragment. */
public sealed interface UlrExpression {
    Type type();

    enum Type {
        INT32(32), INT64(64), INTEGER(0), BOOLEAN(0);

        private final int bits;

        Type(int bits) { this.bits = bits; }

        public int bits() { return bits; }

        public boolean integral() { return this != BOOLEAN; }
    }

    enum Overflow { EXACT, WRAP, CHECKED }

    enum UnaryOperator { PLUS, NEGATE, COMPLEMENT }

    enum BinaryOperator {
        ADD, SUBTRACT, MULTIPLY, BIT_AND, BIT_OR, BIT_XOR,
        SHIFT_LEFT_MASKED, SHIFT_RIGHT_MASKED, SHIFT_RIGHT_UNSIGNED_MASKED, LESS_OR_EQUAL
    }

    record Constant(BigInteger value, Type type) implements UlrExpression {
        public Constant {
            Objects.requireNonNull(value);
            if (!type.integral() || type.bits() != 0 && value.bitLength() >= type.bits()) {
                throw new IllegalArgumentException("ULR constant does not fit its type");
            }
        }
    }

    /** A resolved symbol identity supplied by the adapter, distinct from its spelling. */
    record Read(String binding, Type type) implements UlrExpression {
        public Read {
            Objects.requireNonNull(type);
            if (binding == null || binding.isBlank() || binding.length() > 4_096) {
                throw new IllegalArgumentException("Invalid ULR binding identity");
            }
        }
    }

    record Unary(UnaryOperator operator, UlrExpression operand, Type type, Overflow overflow)
            implements UlrExpression {
        public Unary {
            Objects.requireNonNull(operator);
            requireArithmetic(type, overflow);
            if (operand.type() != type) throw new IllegalArgumentException("ULR unary operand type");
        }
    }

    record Binary(BinaryOperator operator, UlrExpression left, UlrExpression right, Type type,
            Overflow overflow) implements UlrExpression {
        public Binary {
            Objects.requireNonNull(operator);
            if (!left.type().integral() || !right.type().integral()) {
                throw new IllegalArgumentException("ULR binary operands must be integral");
            }
            if (operator == BinaryOperator.LESS_OR_EQUAL) {
                if (type != Type.BOOLEAN || overflow != Overflow.EXACT) {
                    throw new IllegalArgumentException("ULR comparison result type");
                }
            } else {
                requireArithmetic(type, overflow);
                boolean shift = switch (operator) {
                    case SHIFT_LEFT_MASKED, SHIFT_RIGHT_MASKED, SHIFT_RIGHT_UNSIGNED_MASKED -> true;
                    default -> false;
                };
                if (shift ? type.bits() == 0 || left.type() != type || right.type().bits() == 0
                        : !fits(left.type(), type) || !fits(right.type(), type)) {
                    throw new IllegalArgumentException("ULR binary execution width");
                }
            }
        }
    }

    private static boolean fits(Type operand, Type type) {
        return operand == type || type == Type.INTEGER || operand == Type.INT32 && type == Type.INT64;
    }

    private static void requireArithmetic(Type type, Overflow overflow) {
        Objects.requireNonNull(overflow);
        if (!type.integral() || (type == Type.INTEGER) != (overflow == Overflow.EXACT)) {
            throw new IllegalArgumentException("ULR arithmetic mode does not match its type");
        }
    }
}
