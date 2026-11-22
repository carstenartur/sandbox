/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0, available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.internal;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.sandbox.jdt.triggerpattern.api.GuardContext;
import org.sandbox.jdt.triggerpattern.internal.BuiltInGuardRegistration.HierarchyMatch;

/** Binding and context checks for replacing Map view operations with Map operations. */
final class MapViewCleanupGuards {

	private static final String MAP= "java.util.Map"; //$NON-NLS-1$

	private MapViewCleanupGuards() {
	}

	static boolean operationSafe(GuardContext context, Object... arguments) {
		if (arguments.length != 0 || HintRewriteSafety.hasCommentInRewriteRange(context)
				|| !(context.getMatchedNode() instanceof MethodInvocation operation)
				|| !(unparenthesize(operation.getExpression()) instanceof MethodInvocation view)
				|| view.getExpression() == null || !operation.arguments().isEmpty() || !view.arguments().isEmpty()
				|| !Set.of("keySet", "values").contains(view.getName().getIdentifier()) //$NON-NLS-1$ //$NON-NLS-2$
				|| !Set.of("size", "isEmpty", "clear").contains(operation.getName().getIdentifier())) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			return false;
		}
		IMethodBinding viewMethod= view.resolveMethodBinding();
		IMethodBinding operationMethod= operation.resolveMethodBinding();
		if (!instanceMethod(context, viewMethod) || !instanceMethod(context, operationMethod)
				|| hierarchy(context, view.getExpression().resolveTypeBinding(), MAP) != HierarchyMatch.MATCH
				|| hierarchy(context, viewMethod.getDeclaringClass(), MAP) != HierarchyMatch.MATCH
				|| hierarchy(context, operationMethod.getDeclaringClass(), "java.util.Collection") != HierarchyMatch.MATCH) { //$NON-NLS-1$
			return false;
		}
		// Inside a Map implementation the original operation may implement size(),
		// isEmpty() or clear() through its own view. Replacing it could recurse.
		for (ASTNode node= operation.getParent(); node != null; node= node.getParent()) {
			ITypeBinding type;
			if (node instanceof AbstractTypeDeclaration declaration) {
				type= declaration.resolveBinding();
			} else if (node instanceof AnonymousClassDeclaration declaration) {
				type= declaration.resolveBinding();
			} else {
				continue;
			}
			if (hierarchy(context, type, MAP) != HierarchyMatch.NO_MATCH) {
				return false;
			}
		}
		return true;
	}

	private static boolean instanceMethod(GuardContext context, IMethodBinding method) {
		if (method == null || method.isRecovered()) {
			context.markUnknown("mapViewOperationSafe", "Cannot resolve the Map view method"); //$NON-NLS-1$ //$NON-NLS-2$
			return false;
		}
		return !Modifier.isStatic(method.getModifiers()) && method.getParameterTypes().length == 0;
	}

	private static HierarchyMatch hierarchy(GuardContext context, ITypeBinding type, String expected) {
		HierarchyMatch result= type == null ? HierarchyMatch.UNKNOWN
				: BuiltInGuardRegistration.subtypeMatch(type, expected, new HashSet<>());
		if (result == HierarchyMatch.UNKNOWN) {
			context.markUnknown("mapViewOperationSafe", "Cannot resolve the Map view type hierarchy"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return result;
	}

	private static Expression unparenthesize(Expression expression) {
		while (expression instanceof ParenthesizedExpression parentheses) {
			expression= parentheses.getExpression();
		}
		return expression;
	}
}
