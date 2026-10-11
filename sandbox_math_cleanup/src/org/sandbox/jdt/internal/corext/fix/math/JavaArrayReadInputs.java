/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.math;

import de.regelsuche.ast.Expr;
import de.regelsuche.sdk.optimization.JavaExpressions;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.NumericOperation;
import de.regelsuche.sdk.optimization.SourceEvaluationTrace.Occurrence;
import java.util.List;
import java.util.Set;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;

/** Admission of ordered read captures; this does not establish memory equivalence between reads. */
final class JavaArrayReadInputs {
    private static final Set<NumericOperation> TOTAL_OPERATIONS = Set.of(NumericOperation.ADD,
            NumericOperation.SUBTRACT, NumericOperation.MULTIPLY, NumericOperation.NEGATE, NumericOperation.NOT,
            NumericOperation.AND, NumericOperation.OR, NumericOperation.XOR, NumericOperation.SHIFT_LEFT,
            NumericOperation.SHIFT_RIGHT, NumericOperation.UNSIGNED_SHIFT_RIGHT);

    private JavaArrayReadInputs() { }

    static boolean integral(NumericKind kind) {
        return kind != null && kind.integral() && kind != NumericKind.BIG_INTEGER;
    }

    static boolean total(NumericKind kind, Expr expression) {
        if (!integral(kind)) return false;
        var cast = JavaExpressions.castSourceKind(expression);
        return cast.isPresent() ? integral(cast.get())
                : TOTAL_OPERATIONS.contains(JavaExpressions.operationOf(expression).orElse(null));
    }

    static boolean totalPrefix(List<Occurrence> trace, List<JavaComputationRegion.OutputBinding> outputs) {
        return outputs.stream().allMatch(o -> integral(o.declaredKind()))
                && trace.stream().allMatch(o -> total(o.evaluatedKind(), o.expression()));
    }

    /** An index computed or changed in this region must keep its declaration before the capture. */
    static boolean requiresBoundary(Statement statement, Set<String> regionalLocals, boolean totalPrefix) {
        boolean[] boundary = {false};
        statement.accept(new ASTVisitor() {
            @Override public boolean visit(ArrayAccess read) {
                if (!totalPrefix) boundary[0] = true;
                read.accept(new ASTVisitor() {
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

    static boolean supported(ArrayAccess read, Set<String> regionalLocals) {
        Expression receiver = read.getArray();
        while (receiver instanceof ParenthesizedExpression parenthesized) receiver = parenthesized.getExpression();
        return receiver instanceof SimpleName name && name.resolveBinding() instanceof IVariableBinding binding
                && !binding.isRecovered() && !binding.isField() && binding.getType().isArray()
                && binding.getType().getDimensions() == 1
                && integral(JavaOperationRegistry.kind(binding.getType().getElementType()).orElse(null))
                && stableIndex(read.getIndex(), regionalLocals, 0);
    }

    private static boolean stableIndex(Expression index, Set<String> regionalLocals, int depth) {
        if (depth > 64 || !integral(JavaOperationRegistry.kind(index.resolveTypeBinding()).orElse(null))) return false;
        if (index instanceof NumberLiteral || index instanceof CharacterLiteral) return true;
        if (index instanceof SimpleName name && name.resolveBinding() instanceof IVariableBinding binding)
            return !binding.isRecovered() && !binding.isField() && !regionalLocals.contains(binding.getKey());
        if (index instanceof ParenthesizedExpression parentheses)
            return stableIndex(parentheses.getExpression(), regionalLocals, depth + 1);
        if (index instanceof CastExpression cast)
            return stableIndex(cast.getExpression(), regionalLocals, depth + 1);
        if (index instanceof PrefixExpression prefix)
            return (prefix.getOperator() == PrefixExpression.Operator.PLUS || prefix.getOperator() == PrefixExpression.Operator.MINUS
                    || prefix.getOperator() == PrefixExpression.Operator.COMPLEMENT)
                    && stableIndex(prefix.getOperand(), regionalLocals, depth + 1);
        if (index instanceof InfixExpression infix) {
            var operation = JavaOperationRegistry.infix(infix.getOperator());
            if (operation.isEmpty() || !TOTAL_OPERATIONS.contains(operation.get())
                    || !stableIndex(infix.getLeftOperand(), regionalLocals, depth + 1)
                    || !stableIndex(infix.getRightOperand(), regionalLocals, depth + 1)) return false;
            for (Object operand : infix.extendedOperands())
                if (!stableIndex((Expression) operand, regionalLocals, depth + 1)) return false;
            return true;
        }
        return false;
    }
}
