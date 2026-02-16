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
package org.sandbox.jdt.triggerpattern.cleanup;

import java.util.Locale;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.StringLiteral;

/**
 * Detects whether a DSL replacement changed an argument from a {@code String}
 * charset literal (e.g.&nbsp;{@code "UTF-8"}) to a {@code Charset}-typed
 * expression (e.g.&nbsp;{@code StandardCharsets.UTF_8}).
 *
 * <p>The detection is deliberately conservative — pure syntactic checks,
 * no binding resolution. If it cannot determine the type change it returns
 * {@code null} (no false positives).</p>
 *
 * @since 1.3.5
 */
public class TypeChangeDetector {

	/** Known charset string values that map to StandardCharsets constants. */
	private static final Set<String> CHARSET_STRINGS = Set.of(
			"UTF-8", "UTF-16", "UTF-16BE", "UTF-16LE", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			"US-ASCII", "ISO-8859-1" //$NON-NLS-1$ //$NON-NLS-2$
	);

	private static final String STANDARD_CHARSETS_PREFIX = "StandardCharsets."; //$NON-NLS-1$

	private static final String UNSUPPORTED_ENCODING_EXCEPTION_FQN =
			"java.io.UnsupportedEncodingException"; //$NON-NLS-1$
	private static final String UNSUPPORTED_ENCODING_EXCEPTION_SIMPLE =
			"UnsupportedEncodingException"; //$NON-NLS-1$

	private TypeChangeDetector() {
		// utility class – not instantiable
	}

	/**
	 * Detects if a replacement changed a {@code String} argument to a
	 * {@code Charset} type.
	 *
	 * @param matchedNode the original matched AST node
	 * @param replacement the replacement text
	 * @return info about the type change, or {@code null} if no type change detected
	 */
	public static TypeChangeInfo detectCharsetTypeChange(ASTNode matchedNode, String replacement) {
		if (matchedNode == null || replacement == null) {
			return null;
		}

		// 1. Does the replacement contain "StandardCharsets."?
		if (!replacement.contains(STANDARD_CHARSETS_PREFIX)) {
			return null;
		}

		// 2. Does the matched node contain a StringLiteral whose value is a known charset?
		if (!containsCharsetStringLiteral(matchedNode)) {
			return null;
		}

		return new TypeChangeInfo(UNSUPPORTED_ENCODING_EXCEPTION_FQN, UNSUPPORTED_ENCODING_EXCEPTION_SIMPLE);
	}

	/**
	 * Walks the matched node's children looking for a {@link StringLiteral}
	 * whose value (upper-cased) is in {@link #CHARSET_STRINGS}.
	 */
	static boolean containsCharsetStringLiteral(ASTNode node) {
		if (node instanceof StringLiteral literal) {
			return isCharsetString(literal.getLiteralValue());
		}
		boolean[] found = { false };
		node.accept(new ASTVisitor() {
			@Override
			public boolean visit(StringLiteral literal) {
				if (isCharsetString(literal.getLiteralValue())) {
					found[0] = true;
				}
				return !found[0];
			}
		});
		return found[0];
	}

	private static boolean isCharsetString(String value) {
		return CHARSET_STRINGS.contains(value.toUpperCase(Locale.ROOT));
	}
}
