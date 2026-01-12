/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer and others.
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
package org.sandbox.jdt.internal.corext.fix.helper;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.Expression;
import org.sandbox.jdt.internal.corext.util.ExpressionHelper;

/**
 * Utility class for common expression operations in the functional converter.
 * 
 * <p>
 * This class delegates to {@link ExpressionHelper} in sandbox_common for
 * the actual implementation. It is maintained for backward compatibility
 * within the functional converter.
 * </p>
 * 
 * @see ExpressionHelper
 * @deprecated Use {@link ExpressionHelper} directly from sandbox_common instead.
 */
@Deprecated
public final class ExpressionUtils {

	/**
	 * Private constructor to prevent instantiation of this utility class.
	 */
	private ExpressionUtils() {
		// Utility class - no instances allowed
	}

	/**
	 * @see ExpressionHelper#createNegatedExpression(AST, Expression)
	 */
	public static Expression createNegatedExpression(AST ast, Expression condition) {
		return ExpressionHelper.createNegatedExpression(ast, condition);
	}

	/**
	 * @see ExpressionHelper#needsParentheses(Expression)
	 */
	public static boolean needsParentheses(Expression expr) {
		return ExpressionHelper.needsParentheses(expr);
	}

	/**
	 * @see ExpressionHelper#isIdentityMapping(Expression, String)
	 */
	public static boolean isIdentityMapping(Expression expression, String varName) {
		return ExpressionHelper.isIdentityMapping(expression, varName);
	}

	/**
	 * @see ExpressionHelper#stripNegation(Expression)
	 */
	public static Expression stripNegation(Expression expr) {
		return ExpressionHelper.stripNegation(expr);
	}

	/**
	 * @see ExpressionHelper#isNegatedExpression(Expression)
	 */
	public static boolean isNegatedExpression(Expression expr) {
		return ExpressionHelper.isNegatedExpression(expr);
	}

	/**
	 * @see ExpressionHelper#getUnparenthesized(Expression)
	 */
	public static Expression getUnparenthesized(Expression expr) {
		return ExpressionHelper.getUnparenthesized(expr);
	}
}