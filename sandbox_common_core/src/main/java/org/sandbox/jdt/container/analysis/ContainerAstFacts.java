/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.container.analysis;

import java.util.List;
import java.util.Optional;

import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperFieldAccess;

/** Shared, side-effect-free AST facts used by semantic container detectors. */
final class ContainerAstFacts {

	private ContainerAstFacts() {
		// Static utility.
	}

	static Expression unwrap(Expression expression) {
		Expression result= expression;
		while (result instanceof ParenthesizedExpression parenthesized) {
			result= parenthesized.getExpression();
		}
		return result;
	}

	static ExpressionStatement expressionStatement(Assignment assignment) {
		return assignment.getParent() instanceof ExpressionStatement statement ? statement : null;
	}

	static Assignment assignment(Statement statement) {
		if (!(statement instanceof ExpressionStatement expressionStatement)) {
			return null;
		}
		Expression expression= unwrap(expressionStatement.getExpression());
		return expression instanceof Assignment assignment ? assignment : null;
	}

	static Statement nextStatement(Statement statement) {
		if (!(statement.getParent() instanceof Block block)) {
			return null;
		}
		List<?> statements= block.statements();
		int index= statements.indexOf(statement);
		return index >= 0 && index + 1 < statements.size()
				? (Statement) statements.get(index + 1)
				: null;
	}

	static boolean isArrayLength(Expression expression, Expression array) {
		Expression unwrapped= unwrap(expression);
		if (unwrapped instanceof QualifiedName qualifiedName) {
			return "length".equals(qualifiedName.getName().getIdentifier()) //$NON-NLS-1$
					&& sameVariable(array, qualifiedName.getQualifier());
		}
		if (unwrapped instanceof FieldAccess fieldAccess) {
			return "length".equals(fieldAccess.getName().getIdentifier()) //$NON-NLS-1$
					&& sameVariable(array, fieldAccess.getExpression());
		}
		return false;
	}

	static boolean isOne(Expression expression) {
		Expression unwrapped= unwrap(expression);
		Object constant= unwrapped.resolveConstantExpressionValue();
		if (constant instanceof Number number) {
			return number.longValue() == 1L;
		}
		return unwrapped instanceof NumberLiteral literal && "1".equals(literal.getToken()); //$NON-NLS-1$
	}

	static boolean sameVariable(Expression first, Expression second) {
		Expression left= unwrap(first);
		Expression right= unwrap(second);
		Optional<IVariableBinding> leftBinding= variableBinding(left);
		Optional<IVariableBinding> rightBinding= variableBinding(right);
		if (leftBinding.isPresent() && rightBinding.isPresent()) {
			return leftBinding.get().getVariableDeclaration()
					.isEqualTo(rightBinding.get().getVariableDeclaration());
		}
		return left.subtreeMatch(new ASTMatcher(true), right);
	}

	static Optional<IVariableBinding> variableBinding(Expression expression) {
		Expression unwrapped= unwrap(expression);
		IBinding binding= null;
		if (unwrapped instanceof SimpleName simpleName) {
			binding= simpleName.resolveBinding();
		} else if (unwrapped instanceof QualifiedName qualifiedName) {
			binding= qualifiedName.resolveBinding();
		} else if (unwrapped instanceof FieldAccess fieldAccess) {
			binding= fieldAccess.resolveFieldBinding();
		} else if (unwrapped instanceof SuperFieldAccess superFieldAccess) {
			binding= superFieldAccess.resolveFieldBinding();
		}
		return binding instanceof IVariableBinding variableBinding
				? Optional.of(variableBinding)
				: Optional.empty();
	}
}
