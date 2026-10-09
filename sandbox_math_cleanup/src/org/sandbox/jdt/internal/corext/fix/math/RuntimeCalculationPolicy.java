/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

/** Source-selection policy, not an alternative evaluator or equivalence checker. */
final class RuntimeCalculationPolicy {
    private RuntimeCalculationPolicy() { }

    /** Constants form boundaries: their spelling, declarations and comments stay in the source. */
    static boolean preserve(Statement statement, Map<String, VariableDeclarationFragment> declarations) {
        if (statement instanceof VariableDeclarationStatement declaration) {
            if (declaration.fragments().isEmpty()) return false;
            // Multiple declarators are one replacement unit: never discard the
            // constant formula of one declarator while optimizing another.
            for (Object value : declaration.fragments()) {
                var fragment = (VariableDeclarationFragment) value;
                if (preserveExpression(fragment.getInitializer(), declarations)) return true;
            }
            return false;
        }
        if (statement instanceof ReturnStatement returned) {
            return preserveExpression(returned.getExpression(), declarations);
        }
        if (statement instanceof ExpressionStatement expression
                && expression.getExpression() instanceof Assignment assignment
                && assignment.getOperator() == Assignment.Operator.ASSIGN
                && assignment.getLeftHandSide() instanceof SimpleName name
                && name.resolveBinding() instanceof IVariableBinding binding && !binding.isField()) {
            return preserveExpression(assignment.getRightHandSide(), declarations);
        }
        return false;
    }

    private static boolean preserveExpression(Expression expression,
            Map<String, VariableDeclarationFragment> declarations) {
        if (!primitiveNumber(expression)) return false;
        if (constant(expression, declarations, new HashSet<>(), 0)) return true;
        // Until the emitter can preserve original subexpression spelling, keep
        // the entire containing statement. Other runtime statements remain
        // eligible. In particular, do not turn rotate distances (32 - 9) into
        // literals and advertise that compiler work as a runtime optimization.
        boolean[] found = {false};
        expression.accept(new ASTVisitor() {
            @Override public boolean preVisit2(ASTNode node) {
                if (found[0]) return false;
                if (node instanceof Expression child
                        && (child instanceof InfixExpression || child instanceof CastExpression
                                || child instanceof PrefixExpression || child instanceof QualifiedName)
                        && constant(child, declarations, new HashSet<>(), 0)) {
                    found[0] = true;
                    return false;
                }
                return true;
            }
        });
        return found[0];
    }

    private static boolean primitiveNumber(Expression expression) {
        if (expression == null) return false;
        ITypeBinding type = expression.resolveTypeBinding();
        return type != null && type.isPrimitive() && !"boolean".equals(type.getName()); //$NON-NLS-1$
    }

    private static boolean constant(Expression expression, Map<String, VariableDeclarationFragment> declarations,
            Set<String> visiting, int depth) {
        if (depth > 64 || !primitiveNumber(expression)) return false;
        if (expression instanceof NumberLiteral || expression instanceof CharacterLiteral) return true;
        if (expression instanceof ParenthesizedExpression parentheses)
            return constant(parentheses.getExpression(), declarations, visiting, depth + 1);
        if (expression instanceof CastExpression cast)
            return constant(cast.getExpression(), declarations, visiting, depth + 1);
        if (expression instanceof PrefixExpression prefix) {
            var operator = prefix.getOperator();
            return (operator == PrefixExpression.Operator.PLUS || operator == PrefixExpression.Operator.MINUS
                    || operator == PrefixExpression.Operator.COMPLEMENT)
                    && constant(prefix.getOperand(), declarations, visiting, depth + 1);
        }
        if (expression instanceof InfixExpression infix) {
            if (!constant(infix.getLeftOperand(), declarations, visiting, depth + 1)
                    || !constant(infix.getRightOperand(), declarations, visiting, depth + 1)) return false;
            for (Object operand : infix.extendedOperands())
                if (!constant((Expression) operand, declarations, visiting, depth + 1)) return false;
            return true;
        }
        if (!(expression instanceof Name name)
                || !(name.resolveBinding() instanceof IVariableBinding binding) || binding.isRecovered()) return false;
        if (name instanceof QualifiedName qualified
                && !(qualified.getQualifier().resolveBinding() instanceof ITypeBinding)) return false;
        if (binding.getConstantValue() != null) return true;
        if (!(name instanceof SimpleName) || binding.isField() || !binding.isEffectivelyFinal()) return false;
        VariableDeclarationFragment declaration = declarations.get(binding.getKey());
        if (declaration == null || declaration.getStartPosition() >= expression.getStartPosition()
                || !visiting.add(binding.getKey())) return false;
        try {
            return constant(declaration.getInitializer(), declarations, visiting, depth + 1);
        } finally {
            visiting.remove(binding.getKey());
        }
    }
}
