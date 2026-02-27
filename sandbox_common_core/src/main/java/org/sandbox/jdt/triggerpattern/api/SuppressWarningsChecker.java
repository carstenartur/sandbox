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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.api;

import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IExtendedModifier;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.StringLiteral;

/**
 * Utility for checking {@code @SuppressWarnings} annotations on enclosing elements.
 * 
 * <p>This checker walks up the AST from a matched node and checks if any enclosing
 * {@link BodyDeclaration} (method, field, type) has a {@code @SuppressWarnings}
 * annotation with one of the specified suppression keys.</p>
 * 
 * <p>Example usage:</p>
 * <pre>
 * // Check if the matched node is suppressed by @SuppressWarnings("my-hint-key")
 * boolean suppressed = SuppressWarningsChecker.isSuppressed(
 *     match.getMatchedNode(), Set.of("my-hint-key"));
 * </pre>
 * 
 * @since 1.3.3
 */
public final class SuppressWarningsChecker {

	private SuppressWarningsChecker() {
		// utility class
	}

	/**
	 * Checks if the given AST node is within the scope of a {@code @SuppressWarnings}
	 * annotation that contains one of the specified suppression keys.
	 * 
	 * <p>The method walks up the AST tree from the given node, checking each
	 * enclosing {@link BodyDeclaration} for a {@code @SuppressWarnings} annotation.
	 * The annotation can use either the single-member or normal annotation syntax:</p>
	 * <ul>
	 *   <li>{@code @SuppressWarnings("key")}</li>
	 *   <li>{@code @SuppressWarnings({"key1", "key2"})}</li>
	 *   <li>{@code @SuppressWarnings(value = "key")}</li>
	 *   <li>{@code @SuppressWarnings(value = {"key1", "key2"})}</li>
	 * </ul>
	 * 
	 * @param node the AST node to check
	 * @param suppressionKeys the set of suppression keys to look for
	 * @return {@code true} if the node is within the scope of a matching {@code @SuppressWarnings}
	 */
	public static boolean isSuppressed(ASTNode node, Set<String> suppressionKeys) {
		if (node == null || suppressionKeys == null || suppressionKeys.isEmpty()) {
			return false;
		}

		ASTNode current = node;
		while (current != null) {
			if (current instanceof BodyDeclaration bodyDecl) {
				if (hasSuppressWarnings(bodyDecl, suppressionKeys)) {
					return true;
				}
			}
			current = current.getParent();
		}

		return false;
	}

	/**
	 * Checks if a body declaration has a {@code @SuppressWarnings} annotation
	 * with one of the specified keys.
	 */
	@SuppressWarnings("unchecked")
	private static boolean hasSuppressWarnings(BodyDeclaration bodyDecl, Set<String> keys) {
		List<IExtendedModifier> modifiers = bodyDecl.modifiers();
		if (modifiers == null) {
			return false;
		}

		for (IExtendedModifier modifier : modifiers) {
			if (modifier instanceof Annotation annotation) {
				String annotationName = annotation.getTypeName().getFullyQualifiedName();
				if ("SuppressWarnings".equals(annotationName) //$NON-NLS-1$
						|| "java.lang.SuppressWarnings".equals(annotationName)) { //$NON-NLS-1$
					return containsKey(annotation, keys);
				}
			}
		}

		return false;
	}

	/**
	 * Checks if a {@code @SuppressWarnings} annotation contains one of the specified keys.
	 */
	private static boolean containsKey(Annotation annotation, Set<String> keys) {
		if (annotation instanceof SingleMemberAnnotation sma) {
			return expressionContainsKey(sma.getValue(), keys);
		} else if (annotation instanceof NormalAnnotation na) {
			return normalAnnotationContainsKey(na, keys);
		}
		// MarkerAnnotation (@SuppressWarnings without value) — no keys to check
		return false;
	}

	/**
	 * Checks a NormalAnnotation for a "value" member containing one of the keys.
	 */
	@SuppressWarnings("unchecked")
	private static boolean normalAnnotationContainsKey(NormalAnnotation na, Set<String> keys) {
		List<MemberValuePair> values = na.values();
		if (values == null) {
			return false;
		}

		for (MemberValuePair pair : values) {
			if ("value".equals(pair.getName().getIdentifier())) { //$NON-NLS-1$
				return expressionContainsKey(pair.getValue(), keys);
			}
		}

		return false;
	}

	/**
	 * Checks if an expression (string literal or array initializer) contains
	 * one of the specified keys.
	 */
	@SuppressWarnings("unchecked")
	private static boolean expressionContainsKey(Expression expr, Set<String> keys) {
		if (expr instanceof StringLiteral stringLiteral) {
			return keys.contains(stringLiteral.getLiteralValue());
		} else if (expr instanceof ArrayInitializer arrayInit) {
			List<Expression> expressions = arrayInit.expressions();
			for (Expression element : expressions) {
				if (element instanceof StringLiteral sl && keys.contains(sl.getLiteralValue())) {
					return true;
				}
			}
		}
		return false;
	}
}
