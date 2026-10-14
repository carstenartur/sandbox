/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.math.BigInteger;
import java.util.Map;
import org.eclipse.jdt.core.dom.*;

/**
 * Consumes a private parsed generated expression. Original compilation-unit nodes
 * supplied as literal forms are copied, never detached or modified. Unchanged
 * generated nodes are reused so a chain does not trigger repeated subtree copies.
 * The caller must still run the independent emitted-Java verifier.
 */
final class JavaExpressionNormalizer {
    private JavaExpressionNormalizer() { }

    static Expression normalize(Expression expression, Map<BigInteger, Expression> literals) {
        return (Expression) normalizeNode(expression, literals, 0);
    }
    @SuppressWarnings("unchecked")
    private static ASTNode normalizeNode(ASTNode input, Map<BigInteger, Expression> literals, int depth) {
        if (depth > 128) throw new IllegalArgumentException("SOURCE_RENDERING_BOUND"); //$NON-NLS-1$
        AST ast = input.getAST();
        if (input instanceof ClassInstanceCreation creation && creation.getAnonymousClassDeclaration() == null
                && "java.math.BigInteger".equals(creation.getType().toString()) && creation.arguments().size() == 1 //$NON-NLS-1$
                && creation.arguments().getFirst() instanceof StringLiteral literal) {
            try {
                Expression original = literals.get(new BigInteger(literal.getLiteralValue()));
                if (original != null) return ASTNode.copySubtree(ast, original);
            } catch (NumberFormatException unsupported) { /* Keep the original emitted node. */ }
        }
        for (Object property : input.structuralPropertiesForType()) {
            StructuralPropertyDescriptor descriptor = (StructuralPropertyDescriptor)property;
            if (descriptor instanceof ChildPropertyDescriptor) {
                var child = (ASTNode)input.getStructuralProperty(descriptor);
                if (child != null) {
                    ASTNode normalized = normalizeNode(child, literals, depth+1);
                    // JDT rejects attaching a child that still has a parent.
                    if (normalized != child) input.setStructuralProperty(descriptor, normalized);
                }
            } else if (descriptor instanceof ChildListPropertyDescriptor) {
                var children = (java.util.List<ASTNode>)input.getStructuralProperty(descriptor);
                for (int index = 0; index < children.size(); index++) {
                    ASTNode child = children.get(index);
                    ASTNode normalized = normalizeNode(child, literals, depth+1);
                    if (normalized != child) children.set(index, normalized);
                }
            }
        }
        if (input instanceof ParenthesizedExpression parentheses) {
            Expression inner = parentheses.getExpression();
            if (inner instanceof MethodInvocation || inner instanceof Name || inner instanceof ClassInstanceCreation
                    || inner instanceof NumberLiteral || inner instanceof StringLiteral) {
                // A mandatory child cannot be set to null. Detach it using a
                // constant-size placeholder in the discarded generated wrapper.
                parentheses.setExpression(ast.newNullLiteral());
                return inner;
            }
        }
        return input;
    }
}
