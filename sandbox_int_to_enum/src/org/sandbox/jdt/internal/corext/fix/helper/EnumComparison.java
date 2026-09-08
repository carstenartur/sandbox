/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;

/** Comparisons preserved verbatim after a closed, non-null domain is proved. */
public final class EnumComparison {

	public record Operands(Expression left, Expression right) { }

	private EnumComparison() { }

	public static Operands operands(Expression expression) {
		Expression unwrapped= unwrap(expression);
		if (unwrapped instanceof InfixExpression infix && infix.extendedOperands().isEmpty()
				&& (infix.getOperator() == InfixExpression.Operator.EQUALS
						|| infix.getOperator() == InfixExpression.Operator.NOT_EQUALS)) {
			return new Operands(unwrap(infix.getLeftOperand()), unwrap(infix.getRightOperand()));
		}
		if (!(unwrapped instanceof MethodInvocation invocation)) {
			return null;
		}
		IMethodBinding binding= invocation.resolveMethodBinding();
		if (binding == null || binding.isRecovered() || !"equals".equals(binding.getName())) { //$NON-NLS-1$
			return null;
		}
		String owner= binding.getMethodDeclaration().getDeclaringClass().getQualifiedName();
		Expression left;
		Expression right;
		if ("java.lang.String".equals(owner) && invocation.getExpression() != null //$NON-NLS-1$
				&& invocation.arguments().size() == 1) {
			left= invocation.getExpression();
			right= (Expression) invocation.arguments().get(0);
		} else if ("java.util.Objects".equals(owner) && invocation.arguments().size() == 2) { //$NON-NLS-1$
			left= (Expression) invocation.arguments().get(0);
			right= (Expression) invocation.arguments().get(1);
		} else {
			return null;
		}
		if (!isString(left) || !isString(right)) {
			return null;
		}
		return new Operands(unwrap(left), unwrap(right));
	}

	private static boolean isString(Expression expression) {
		return expression.resolveTypeBinding() != null
				&& "java.lang.String".equals(expression.resolveTypeBinding().getQualifiedName()); //$NON-NLS-1$
	}

	private static Expression unwrap(Expression expression) {
		while (expression instanceof ParenthesizedExpression parenthesized) {
			expression= parenthesized.getExpression();
		}
		return expression;
	}
}
