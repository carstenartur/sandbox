/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import de.regelsuche.ast.Expr;
import de.regelsuche.ast.VariableExpr;
import de.regelsuche.sdk.optimization.JavaExpressions;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.SourceEvaluationTrace;
import de.regelsuche.search.program.JointComputationPlan;

/** Independent exact affine-domain check, not a search engine or a source of SDK assumptions. */
final class IntegralMathematicalDomain {
    private record Affine(BigInteger coefficient, BigInteger constant) {
        Affine add(Affine other) {
            return new Affine(coefficient.add(other.coefficient), constant.add(other.constant));
        }
        Affine scale(BigInteger factor) {
            return new Affine(coefficient.multiply(factor), constant.multiply(factor));
        }
    }
    record Range(BigInteger minimum, BigInteger maximum) {
        Range intersect(Range other) {
            return new Range(minimum.max(other.minimum), maximum.min(other.maximum));
        }
        boolean includes(Range other) {
            return minimum.compareTo(other.minimum) <= 0 && maximum.compareTo(other.maximum) >= 0;
        }
        @Override public String toString() { return "[" + minimum + ", " + maximum + "]"; }
    }
    private final JointComputationPlan plan;
    private final String input;
    private final Map<Expr, Affine> expressions = new HashMap<>();
    private Range range;

    private IntegralMathematicalDomain(JointComputationPlan plan, SourceEvaluationTrace trace) {
        if (plan.inputs().size() != 1) throw unsupported();
        this.plan = plan;
        input = plan.inputs().keySet().iterator().next();
        range = limits(NumericKind.fromType(plan.inputs().get(input)));
        for (var occurrence : trace.occurrences()) {
            constrain(value(occurrence.expression()), limits(occurrence.evaluatedKind()));
        }
        for (var output : plan.outputs()) constrain(value(output.expression()), limits(NumericKind.fromType(output.type())));
        if (range.minimum.compareTo(range.maximum) > 0) throw unsupported();
    }

    static boolean supports(JavaComputationRegion region) {
        try {
            new IntegralMathematicalDomain(region.plan(), region.trace());
            return true;
        } catch (IllegalArgumentException unsupported) { return false; }
    }

    static String verify(JavaComputationRegion original, JointComputationPlan replacement) {
        var before = new IntegralMathematicalDomain(original.plan(), original.trace());
        var after = new IntegralMathematicalDomain(replacement, SourceEvaluationTrace.fromPlan(replacement));
        if (!before.input.equals(after.input) || !after.range.includes(before.range)
                || before.plan.outputs().size() != after.plan.outputs().size()) throw unsupported();
        for (int index = 0; index < before.plan.outputs().size(); index++) {
            if (!before.value(before.plan.outputExpressions().get(index))
                    .equals(after.value(after.plan.outputExpressions().get(index)))) throw unsupported();
        }
        return "Local calculation input {@code " + original.inputNames().get(before.input)
                + "}: exact no-overflow range before " + before.range + "; after " + after.range
                + ". Mathematical mode intentionally permits different Java overflow behavior. "
                + "These are local calculation ranges, not a proof of the whole method's preconditions.";
    }

    private Affine value(Expr expression) {
        Affine cached = expressions.get(expression);
        if (cached != null) return cached;
        Affine result;
        if (expression instanceof VariableExpr variable) {
            if (!variable.name().equals(input)) throw unsupported();
            result = new Affine(BigInteger.ONE, BigInteger.ZERO);
        } else if (JavaExpressions.isLiteral(expression)) {
            Object literal = JavaExpressions.literalValue(expression);
            if (!(literal instanceof Byte || literal instanceof Short || literal instanceof Character
                    || literal instanceof Integer || literal instanceof Long)) throw unsupported();
            result = new Affine(BigInteger.ZERO, BigInteger.valueOf(literal instanceof Character c ? c : ((Number) literal).longValue()));
        } else {
            // Restrict to integral arithmetic with exact division. In particular, no bit operations,
            // floating point, nonlinear products, variable divisors or rounding assumptions.
            limits(JavaExpressions.resultKind(expression));
            var operands = JavaExpressions.operands(expression);
            Affine left = value(operands.getFirst());
            if (JavaExpressions.castSourceKind(expression).isPresent()) {
                limits(JavaExpressions.castSourceKind(expression).orElseThrow());
                result = left;
            } else {
                result = switch (JavaExpressions.operationOf(expression).orElseThrow()) {
                    case ADD, ADD_EXACT -> left.add(value(operands.get(1)));
                    case SUBTRACT, SUBTRACT_EXACT -> left.add(value(operands.get(1)).scale(BigInteger.ONE.negate()));
                    case NEGATE, NEGATE_EXACT -> left.scale(BigInteger.ONE.negate());
                    case MULTIPLY, MULTIPLY_EXACT -> {
                        Affine right = value(operands.get(1));
                        if (left.coefficient.signum() == 0) yield right.scale(left.constant);
                        if (right.coefficient.signum() == 0) yield left.scale(right.constant);
                        throw unsupported();
                    }
                    case DIVIDE -> {
                        Affine right = value(operands.get(1));
                        if (right.coefficient.signum() != 0 || right.constant.signum() == 0
                                || left.coefficient.remainder(right.constant).signum() != 0
                                || left.constant.remainder(right.constant).signum() != 0) throw unsupported();
                        yield new Affine(left.coefficient.divide(right.constant), left.constant.divide(right.constant));
                    }
                    default -> throw unsupported();
                };
            }
        }
        expressions.put(expression, result);
        return result;
    }

    private void constrain(Affine affine, Range limits) {
        BigInteger a = affine.coefficient, b = affine.constant;
        if (a.signum() == 0) {
            if (b.compareTo(limits.minimum) < 0 || b.compareTo(limits.maximum) > 0) throw unsupported();
            return;
        }
        BigInteger low = limits.minimum.subtract(b), high = limits.maximum.subtract(b);
        if (a.signum() < 0) {
            BigInteger previousLow = low;
            low = high.negate(); high = previousLow.negate(); a = a.negate();
        }
        range = range.intersect(new Range(floor(low.negate(), a).negate(), floor(high, a)));
    }

    private static BigInteger floor(BigInteger numerator, BigInteger positiveDenominator) {
        BigInteger[] parts = numerator.divideAndRemainder(positiveDenominator);
        return parts[1].signum() < 0 ? parts[0].subtract(BigInteger.ONE) : parts[0];
    }

    private static Range limits(NumericKind kind) {
        int bits = switch (kind) { case BYTE -> 8; case SHORT, CHAR -> 16; case INT -> 32; case LONG -> 64; default -> throw unsupported(); };
        return kind == NumericKind.CHAR ? new Range(BigInteger.ZERO, BigInteger.valueOf(65535))
                : new Range(BigInteger.ONE.shiftLeft(bits - 1).negate(), BigInteger.ONE.shiftLeft(bits - 1).subtract(BigInteger.ONE));
    }

    private static IllegalArgumentException unsupported() {
        return new IllegalArgumentException("MATHEMATICAL_DOMAIN_NOT_PROVED");
    }
}
