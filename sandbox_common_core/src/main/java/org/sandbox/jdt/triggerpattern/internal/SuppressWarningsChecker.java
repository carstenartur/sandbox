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
package org.sandbox.jdt.triggerpattern.internal;

import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.StringLiteral;

/**
 * Utility that checks if an AST node is suppressed by a {@code @SuppressWarnings}
 * annotation on any enclosing method, field, or type declaration.
 *
 * <p>This walks up the AST from a given node and checks each enclosing
 * {@link BodyDeclaration} for a {@code @SuppressWarnings} annotation
 * whose value contains the specified key.</p>
 *
 * @since 1.4.0
 */
public final class SuppressWarningsChecker {

	private static final String SUPPRESS_WARNINGS = "SuppressWarnings"; //$NON-NLS-1$
	private static final String VALUE = "value"; //$NON-NLS-1$

	private SuppressWarningsChecker() {
		// utility class
	}

	/**
	 * Checks if the given AST node is suppressed by a {@code @SuppressWarnings}
	 * annotation containing the specified key.
	 *
	 * @param node the AST node to check
	 * @param key the suppress warnings key to look for
	 * @return {@code true} if the node is suppressed
	 */
	public static boolean isSuppressed(ASTNode node, String key) {
		if (node == null || key == null || key.isEmpty()) {
			return false;
		}
		ASTNode current = node;
		while (current != null) {
			if (current instanceof BodyDeclaration bodyDecl) {
				if (hasSuppressWarningsKey(bodyDecl, key)) {
					return true;
				}
			}
			current = current.getParent();
		}
		return false;
	}

	/**
	 * Checks if a body declaration has a {@code @SuppressWarnings} annotation
	 * containing the given key.
	 */
	@SuppressWarnings("unchecked")
	private static boolean hasSuppressWarningsKey(BodyDeclaration bodyDecl, String key) {
		for (Object modifier : bodyDecl.modifiers()) {
			if (modifier instanceof Annotation annotation) {
				String annotationName = annotation.getTypeName().getFullyQualifiedName();
				if (SUPPRESS_WARNINGS.equals(annotationName)
						|| "java.lang.SuppressWarnings".equals(annotationName)) { //$NON-NLS-1$
					return containsKey(annotation, key);
				}
			}
		}
		return false;
	}

	/**
	 * Checks if a {@code @SuppressWarnings} annotation contains the given key.
	 */
	private static boolean containsKey(Annotation annotation, String key) {
		if (annotation instanceof SingleMemberAnnotation sma) {
			return expressionContainsKey(sma.getValue(), key);
		}
		if (annotation instanceof NormalAnnotation na) {
			@SuppressWarnings("unchecked")
			List<MemberValuePair> values = na.values();
			for (MemberValuePair pair : values) {
				if (VALUE.equals(pair.getName().getIdentifier())) {
					return expressionContainsKey(pair.getValue(), key);
				}
			}
		}
		return false;
	}

	/**
	 * Checks if an expression (String literal or array of String literals)
	 * contains the given key.
	 */
	private static boolean expressionContainsKey(Expression expr, String key) {
		if (expr instanceof StringLiteral literal) {
			return key.equals(literal.getLiteralValue());
		}
		if (expr instanceof ArrayInitializer arrayInit) {
			@SuppressWarnings("unchecked")
			List<Expression> expressions = arrayInit.expressions();
			for (Expression element : expressions) {
				if (element instanceof StringLiteral literal) {
					if (key.equals(literal.getLiteralValue())) {
						return true;
					}
				}
			}
		}
		return false;
	}
}
