/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix;

import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;

/**
 * Fix core for wrong string comparison detection.
 *
 * <p>Detects {@code str == "literal"} and replaces with {@code "literal".equals(str)},
 * and {@code str != "literal"} with {@code !"literal".equals(str)}.</p>
 *
 */
public class WrongStringComparisonFixCore {

	/**
	 * Finds wrong string comparison operations in the compilation unit.
	 *
	 * @param compilationUnit the compilation unit to search
	 * @param operations the set to add found operations to
	 */
	public static void findOperations(CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations) {

		compilationUnit.accept(new ASTVisitor() {
			@Override
			public boolean visit(InfixExpression node) {
				InfixExpression.Operator op = node.getOperator();
				if (op != InfixExpression.Operator.EQUALS && op != InfixExpression.Operator.NOT_EQUALS) {
					return true;
				}

				Expression left = node.getLeftOperand();
				Expression right = node.getRightOperand();

				StringLiteral literal = null;
				Expression other = null;

				if (left instanceof StringLiteral sl) {
					literal = sl;
					other = right;
				} else if (right instanceof StringLiteral sl) {
					literal = sl;
					other = left;
				}

				if (literal != null && other != null) {
					operations.add(new WrongStringComparisonOperation(node, literal, other,
							op == InfixExpression.Operator.NOT_EQUALS));
				}
				return true;
			}
		});
	}

	private static class WrongStringComparisonOperation extends CompilationUnitRewriteOperation {

		private final InfixExpression originalExpr;
		private final StringLiteral literal;
		private final Expression other;
		private final boolean negated;

		WrongStringComparisonOperation(InfixExpression originalExpr,
				StringLiteral literal, Expression other, boolean negated) {
			this.originalExpr = originalExpr;
			this.literal = literal;
			this.other = other;
			this.negated = negated;
		}

		@Override
		public void rewriteAST(CompilationUnitRewrite cuRewrite, LinkedProposalModelCore linkedModel) {
			ASTRewrite rewrite = cuRewrite.getASTRewrite();
			AST ast = cuRewrite.getRoot().getAST();
			TextEditGroup group = createTextEditGroup(
					"Replace string reference comparison with .equals()", cuRewrite); //$NON-NLS-1$

			MethodInvocation equalsCall = ast.newMethodInvocation();
			equalsCall.setExpression((Expression) ASTNode.copySubtree(ast, literal));
			equalsCall.setName(ast.newSimpleName("equals")); //$NON-NLS-1$
			equalsCall.arguments().add(ASTNode.copySubtree(ast, other));

			Expression replacement;
			if (negated) {
				PrefixExpression not = ast.newPrefixExpression();
				not.setOperator(PrefixExpression.Operator.NOT);
				ParenthesizedExpression paren = ast.newParenthesizedExpression();
				paren.setExpression(equalsCall);
				not.setOperand(paren);
				replacement = not;
			} else {
				replacement = equalsCall;
			}

			rewrite.replace(originalExpr, replacement, group);
		}
	}
}
