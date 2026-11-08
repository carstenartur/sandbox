/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.math.BigInteger;
import java.util.Set;
import org.sandbox.functional.core.model.NumericIteration.CountedSum;
import org.sandbox.functional.core.model.UlrExpression;
import org.sandbox.functional.core.model.UlrExpression.Overflow;
import org.sandbox.functional.core.model.UlrExpression.Type;
import org.sandbox.functional.core.model.UlrPolynomial;
import de.regelsuche.ast.BinaryExpr;
import de.regelsuche.ast.BinaryOperator;
import de.regelsuche.ast.Expr;
import de.regelsuche.ast.NumberExpr;
import de.regelsuche.ast.VariableExpr;
import de.regelsuche.ast.iteration.IntegerArithmetic;
import de.regelsuche.scalar.ExactRational;
import de.regelsuche.sdk.optimization.iteration.PolynomialSum;
import de.regelsuche.symbol.SymbolId;

/** Projects an admitted ULR reduction onto the existing Regelsuche series model. */
final class UlrSumProjection {
    private static final UlrPolynomial.Projection<Expr> REGELSUCHE = new UlrPolynomial.Projection<>() {
        @Override public Expr constant(BigInteger value) {
            return new NumberExpr(ExactRational.integer(value));
        }

        @Override public Expr read(String binding) {
            return VariableExpr.scoped(SymbolId.fromCanonicalText(binding));
        }

        @Override public Expr binary(UlrExpression.BinaryOperator operator, Expr left, Expr right) {
            var operation = switch (operator) {
                case ADD -> BinaryOperator.ADD;
                case SUBTRACT -> BinaryOperator.SUB;
                case MULTIPLY -> BinaryOperator.MUL;
                default -> throw unsupported();
            };
            return new BinaryExpr(left, operation, right);
        }
    };

    private UlrSumProjection() { }

    /** A checked sum is proved over exact integers. Source adapters still owe the prefix overflow proof. */
    static PolynomialSum project(CountedSum sum, Set<SymbolId> parameters, Runnable checkpoint) {
        Type type = sum.initial().type();
        var index = SymbolId.fromCanonicalText(sum.index().binding());
        return new PolynomialSum(index, parameters,
                expression(sum.start(), sum.index().type(), mode(sum.index().type(), sum.overflow()), checkpoint),
                sum.stride(), expression(sum.summand().result(), type, sum.overflow(), checkpoint),
                expression(sum.initial(), type, sum.overflow(), checkpoint), arithmetic(type, sum.overflow()));
    }

    static IntegerArithmetic arithmetic(Type type, Overflow overflow) {
        return overflow == Overflow.WRAP ? switch (type) {
            case INT32 -> IntegerArithmetic.JAVA_INT;
            case INT64 -> IntegerArithmetic.JAVA_LONG;
            default -> throw unsupported();
        } : IntegerArithmetic.EXACT;
    }

    static Expr expression(UlrExpression value, Type type, Overflow overflow, Runnable checkpoint) {
        return UlrPolynomial.project(value, type, overflow, checkpoint, REGELSUCHE);
    }

    private static Overflow mode(Type type, Overflow overflow) {
        return type == Type.INTEGER ? Overflow.EXACT : overflow == Overflow.CHECKED ? Overflow.CHECKED : Overflow.WRAP;
    }

    private static IllegalArgumentException unsupported() {
        return new IllegalArgumentException("ULR_SUM_OUTSIDE_POLYNOMIAL_FRAGMENT"); //$NON-NLS-1$
    }
}
