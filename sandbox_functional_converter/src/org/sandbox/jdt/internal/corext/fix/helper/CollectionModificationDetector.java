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
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.Set;

import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.ThisExpression;

/**
 * Shared utility for detecting structural modifications on the source being
 * iterated.
 *
 * <p>The production API compares Java bindings rather than spelling. It handles
 * local variables, fields, repeated getter invocations, and map view expressions
 * such as {@code map.keySet()} whose structural owner is {@code map}.</p>
 *
 * @see <a href="https://github.com/carstenartur/sandbox/issues/670">Issue #670</a>
 * @since 1.0.0
 */
public final class CollectionModificationDetector {

	private static final Set<String> MODIFYING_METHODS= Set.of(
			"remove", "add", "clear", "set", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			"addAll", "removeAll", "retainAll", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			"removeIf", "replaceAll", "sort", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			"put", "putAll", "putIfAbsent", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			"compute", "computeIfAbsent", "computeIfPresent", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			"merge", "replace"); //$NON-NLS-1$ //$NON-NLS-2$

	private static final Set<String> MAP_VIEW_METHODS= Set.of(
			"entrySet", "keySet", "values"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

	private static final String[] GETTER_PREFIXES= {
			"get", "fetch", "retrieve", "obtain" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	};

	private CollectionModificationDetector() {
		// utility class
	}

	/**
	 * Checks whether an invocation structurally modifies the expression whose
	 * elements are being traversed.
	 *
	 * @param methodInvocation candidate mutation
	 * @param iteratedExpression expression from the enhanced-for or iterator source
	 * @return {@code true} only when the mutation receiver identifies the same source
	 */
	public static boolean isModification(MethodInvocation methodInvocation, Expression iteratedExpression) {
		if (!MODIFYING_METHODS.contains(methodInvocation.getName().getIdentifier())) {
			return false;
		}
		Expression receiver= methodInvocation.getExpression();
		if (receiver == null || iteratedExpression == null) {
			return false;
		}
		return referencesSameTarget(receiver, normalizeIteratedExpression(iteratedExpression));
	}

	/**
	 * Compatibility overload for existing focused detector tests. Production loop
	 * analysis should use {@link #isModification(MethodInvocation, Expression)}.
	 *
	 * @param methodInvocation candidate mutation
	 * @param collectionName simple source name
	 * @return whether the receiver denotes the named source
	 */
	public static boolean isModification(MethodInvocation methodInvocation, String collectionName) {
		if (collectionName == null || !MODIFYING_METHODS.contains(methodInvocation.getName().getIdentifier())) {
			return false;
		}
		Expression receiver= unwrap(methodInvocation.getExpression());
		if (receiver instanceof SimpleName name) {
			return collectionName.equals(name.getIdentifier());
		}
		if (receiver instanceof FieldAccess fieldAccess
				&& fieldAccess.getExpression() instanceof ThisExpression) {
			return collectionName.equals(fieldAccess.getName().getIdentifier());
		}
		if (receiver instanceof MethodInvocation getterInvocation) {
			return matchesGetterPattern(getterInvocation, collectionName);
		}
		return false;
	}

	/**
	 * A map view iterates the backing map. Mutating that map while traversing
	 * {@code keySet()}, {@code values()}, or {@code entrySet()} must therefore block
	 * conversion as well.
	 */
	static Expression normalizeIteratedExpression(Expression expression) {
		Expression current= unwrap(expression);
		if (current instanceof MethodInvocation invocation
				&& MAP_VIEW_METHODS.contains(invocation.getName().getIdentifier())
				&& invocation.arguments().isEmpty()
				&& invocation.getExpression() != null) {
			return unwrap(invocation.getExpression());
		}
		return current;
	}

	private static boolean referencesSameTarget(Expression first, Expression second) {
		Expression left= unwrap(first);
		Expression right= unwrap(second);
		IBinding leftBinding= resolveIdentityBinding(left);
		IBinding rightBinding= resolveIdentityBinding(right);
		if (leftBinding != null && rightBinding != null) {
			String leftKey= bindingKey(leftBinding);
			String rightKey= bindingKey(rightBinding);
			return leftKey != null && leftKey.equals(rightKey);
		}
		if (leftBinding != null || rightBinding != null) {
			return false;
		}
		return left.subtreeMatch(new ASTMatcher(), right);
	}

	private static IBinding resolveIdentityBinding(Expression expression) {
		if (expression instanceof SimpleName name) {
			return name.resolveBinding();
		}
		if (expression instanceof FieldAccess fieldAccess) {
			return fieldAccess.resolveFieldBinding();
		}
		if (expression instanceof QualifiedName qualifiedName) {
			return qualifiedName.resolveBinding();
		}
		if (expression instanceof SuperFieldAccess superFieldAccess) {
			return superFieldAccess.resolveFieldBinding();
		}
		if (expression instanceof MethodInvocation invocation) {
			return invocation.resolveMethodBinding();
		}
		return null;
	}

	private static String bindingKey(IBinding binding) {
		if (binding instanceof IVariableBinding variableBinding) {
			return variableBinding.getVariableDeclaration().getKey();
		}
		if (binding instanceof IMethodBinding methodBinding) {
			return methodBinding.getMethodDeclaration().getKey();
		}
		return binding.getKey();
	}

	private static boolean matchesGetterPattern(MethodInvocation invocation, String collectionName) {
		if (!invocation.arguments().isEmpty()) {
			return false;
		}
		String methodName= invocation.getName().getIdentifier();
		for (String prefix : GETTER_PREFIXES) {
			if (methodName.startsWith(prefix) && methodName.length() > prefix.length()) {
				String propertyName= methodName.substring(prefix.length());
				String expectedName= Character.toLowerCase(propertyName.charAt(0)) + propertyName.substring(1);
				if (collectionName.equals(expectedName)) {
					return true;
				}
			}
		}
		return false;
	}

	private static Expression unwrap(Expression expression) {
		Expression current= expression;
		while (current instanceof ParenthesizedExpression parenthesized) {
			current= parenthesized.getExpression();
		}
		return current;
	}
}
