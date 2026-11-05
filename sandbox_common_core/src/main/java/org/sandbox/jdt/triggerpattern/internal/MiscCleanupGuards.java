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
package org.sandbox.jdt.triggerpattern.internal;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.SwitchExpression;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.sandbox.jdt.triggerpattern.api.GuardContext;

/** Safety checks for the optional miscellaneous hint library. */
final class MiscCleanupGuards {

	private MiscCleanupGuards() {
	}

	static boolean noCommentsOrSwitch(GuardContext context, Object... arguments) {
		if (arguments.length != 0 || HintRewriteSafety.hasCommentInRewriteRange(context)) {
			return false;
		}
		SwitchFinder finder= new SwitchFinder();
		context.getMatchedNode().accept(finder);
		return !finder.found;
	}

	static boolean primitiveBooleanConditional(GuardContext context, Object... arguments) {
		if (!noCommentsOrSwitch(context, arguments)
				|| !(context.getMatchedNode() instanceof ConditionalExpression conditional)) {
			return false;
		}
		Expression condition= conditional.getExpression();
		while (condition instanceof ParenthesizedExpression parenthesized) {
			condition= parenthesized.getExpression();
		}
		// Switch/conditional expressions may become target-typed in the new context.
		if (condition instanceof ConditionalExpression) {
			return false;
		}
		ITypeBinding conditionType= conditional.getExpression().resolveTypeBinding();
		if (conditionType == null || conditionType.isRecovered()) {
			context.markUnknown("miscPrimitiveBooleanConditionalSafe", "Cannot resolve condition type"); //$NON-NLS-1$ //$NON-NLS-2$
			return false;
		}
		if (!conditionType.isPrimitive() || !"boolean".equals(conditionType.getName())) { //$NON-NLS-1$
			return false;
		}
		ASTNode parent= conditional.getParent();
		while (parent instanceof ParenthesizedExpression) {
			parent= parent.getParent();
		}
		return !(parent instanceof LambdaExpression);
	}

	private static final class SwitchFinder extends ASTVisitor {
		boolean found;

		@Override
		public boolean visit(SwitchExpression node) {
			found= true;
			return false;
		}

		@Override
		public boolean visit(SwitchStatement node) {
			found= true;
			return false;
		}
	}
}
