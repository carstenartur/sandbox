/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.container.analysis;

import static org.sandbox.jdt.container.analysis.ContainerAstFacts.unwrap;
import static org.sandbox.jdt.container.analysis.ContainerAstFacts.variableBinding;

import java.util.Objects;
import java.util.Optional;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;

/**
 * Shared AST facts for the deliberately narrow
 * {@code if (!values.contains(value)) values.add(value)} pattern.
 *
 * <p>The matcher proves only the local syntactic relationship. The surrounding
 * analyzer must still classify every use of the collection binding before a
 * migration may be offered.</p>
 */
public final class UniqueSequencePattern {

	private UniqueSequencePattern() {
		// Utility class.
	}

	/**
	 * Matches one duplicate-suppressing insertion for the supplied collection binding.
	 *
	 * @param statement candidate conditional
	 * @param bindingKey stable collection binding key
	 * @return the matched guard and insertion, or an empty result
	 */
	public static Optional<GuardedAdd> match(
			IfStatement statement,
			String bindingKey) {
		Objects.requireNonNull(statement, "statement"); //$NON-NLS-1$
		if (bindingKey == null || bindingKey.isBlank() || statement.getElseStatement() != null) {
			return Optional.empty();
		}

		Expression condition= unwrap(statement.getExpression());
		if (!(condition instanceof PrefixExpression negation)
				|| negation.getOperator() != PrefixExpression.Operator.NOT
				|| !(unwrap(negation.getOperand()) instanceof MethodInvocation contains)
				|| !isInvocation(contains, "contains", bindingKey) //$NON-NLS-1$
				|| contains.arguments().size() != 1) {
			return Optional.empty();
		}

		MethodInvocation add= singleInvocation(statement.getThenStatement());
		if (add == null
				|| !isInvocation(add, "add", bindingKey) //$NON-NLS-1$
				|| add.arguments().size() != 1) {
			return Optional.empty();
		}

		Expression testedValue= (Expression) contains.arguments().get(0);
		Expression insertedValue= (Expression) add.arguments().get(0);
		if (!sameStableValue(testedValue, insertedValue)) {
			return Optional.empty();
		}
		return Optional.of(new GuardedAdd(statement, contains, add, insertedValue));
	}

	/** Returns the nearest matching guarded add enclosing the supplied AST node. */
	public static Optional<GuardedAdd> enclosing(
			ASTNode node,
			String bindingKey) {
		ASTNode current= Objects.requireNonNull(node, "node"); //$NON-NLS-1$
		while (current != null && !(current instanceof org.eclipse.jdt.core.dom.MethodDeclaration)) {
			if (current instanceof IfStatement statement) {
				Optional<GuardedAdd> match= match(statement, bindingKey);
				if (match.isPresent()) {
					GuardedAdd guardedAdd= match.get();
					if (isDescendant(node, guardedAdd.contains())
							|| isDescendant(node, guardedAdd.add())) {
						return match;
					}
				}
			}
			current= current.getParent();
		}
		return Optional.empty();
	}

	/** Returns whether the invocation receiver resolves to the supplied binding. */
	public static boolean isInvocation(
			MethodInvocation invocation,
			String methodName,
			String bindingKey) {
		return methodName.equals(invocation.getName().getIdentifier())
				&& invocation.getExpression() != null
				&& variableBinding(unwrap(invocation.getExpression()))
						.map(IVariableBinding::getVariableDeclaration)
						.map(IVariableBinding::getKey)
						.filter(bindingKey::equals)
						.isPresent();
	}

	private static MethodInvocation singleInvocation(Statement statement) {
		Statement effective= statement;
		if (effective instanceof Block block) {
			if (block.statements().size() != 1
					|| !(block.statements().get(0) instanceof Statement nested)) {
				return null;
			}
			effective= nested;
		}
		if (!(effective instanceof ExpressionStatement expressionStatement)
				|| !(unwrap(expressionStatement.getExpression()) instanceof MethodInvocation invocation)) {
			return null;
		}
		return invocation;
	}

	private static boolean sameStableValue(Expression first, Expression second) {
		Expression left= unwrap(first);
		Expression right= unwrap(second);
		if (left instanceof SimpleName leftName && right instanceof SimpleName rightName) {
			return variableBinding(leftName)
					.flatMap(leftBinding -> variableBinding(rightName)
							.map(rightBinding -> sameDeclaration(leftBinding, rightBinding)))
					.orElse(false);
		}
		if (left instanceof StringLiteral leftLiteral && right instanceof StringLiteral rightLiteral) {
			return leftLiteral.getLiteralValue().equals(rightLiteral.getLiteralValue());
		}
		if (left instanceof NumberLiteral leftLiteral && right instanceof NumberLiteral rightLiteral) {
			return leftLiteral.getToken().equals(rightLiteral.getToken());
		}
		if (left instanceof CharacterLiteral leftLiteral
				&& right instanceof CharacterLiteral rightLiteral) {
			return leftLiteral.charValue() == rightLiteral.charValue();
		}
		if (left instanceof BooleanLiteral leftLiteral
				&& right instanceof BooleanLiteral rightLiteral) {
			return leftLiteral.booleanValue() == rightLiteral.booleanValue();
		}
		return left instanceof NullLiteral && right instanceof NullLiteral;
	}

	private static boolean sameDeclaration(
			IVariableBinding first,
			IVariableBinding second) {
		String firstKey= first.getVariableDeclaration().getKey();
		String secondKey= second.getVariableDeclaration().getKey();
		return firstKey != null && firstKey.equals(secondKey);
	}

	private static boolean isDescendant(ASTNode candidate, ASTNode ancestor) {
		ASTNode current= candidate;
		while (current != null) {
			if (current == ancestor) {
				return true;
			}
			current= current.getParent();
		}
		return false;
	}

	/** One matched membership guard and its sole insertion. */
	public record GuardedAdd(
			IfStatement statement,
			MethodInvocation contains,
			MethodInvocation add,
			Expression value) {

		public GuardedAdd {
			Objects.requireNonNull(statement, "statement"); //$NON-NLS-1$
			Objects.requireNonNull(contains, "contains"); //$NON-NLS-1$
			Objects.requireNonNull(add, "add"); //$NON-NLS-1$
			Objects.requireNonNull(value, "value"); //$NON-NLS-1$
		}
	}
}
