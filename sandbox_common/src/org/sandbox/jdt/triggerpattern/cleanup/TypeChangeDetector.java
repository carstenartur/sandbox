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

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;

/**
 * Detects whether a DSL replacement changed an argument from a {@code String}
 * charset literal (e.g.&nbsp;{@code "UTF-8"}) to a {@code Charset}-typed
 * expression (e.g.&nbsp;{@code StandardCharsets.UTF_8}).
 *
 * <p>Only a direct argument replacement by a known StandardCharsets field is
 * recognized. Calls such as {@code StandardCharsets.UTF_8.name()}, string content
 * and nested expressions do not authorize exception removal. This syntactic
 * check is used with type-checked encoding rules; it does not establish the
 * exception contract of an arbitrary method or constructor.</p>
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
		if (matchedNode == null || replacement == null || !replacement.contains(STANDARD_CHARSETS_PREFIX)) {
			return null;
		}
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_EXPRESSION);
		parser.setSource(replacement.toCharArray());
		return detectCharsetTypeChange(matchedNode, parser.createAST(null));
	}

	/** Uses an already parsed replacement; only a direct changed argument authorizes cleanup. */
	static TypeChangeInfo detectCharsetTypeChange(ASTNode matchedNode, ASTNode replacement) {
		if (replacement == null || (replacement.getFlags() & (ASTNode.MALFORMED | ASTNode.RECOVERED)) != 0) {
			return null;
		}
		List<?> original= arguments(matchedNode);
		List<?> rewritten= arguments(replacement);
		for (int i= 0; i < Math.min(original.size(), rewritten.size()); i++) {
			if (unparenthesized((ASTNode) original.get(i)) instanceof StringLiteral literal
					&& isCharsetString(literal.getLiteralValue())
					&& unparenthesized((ASTNode) rewritten.get(i)) instanceof QualifiedName name) {
				String owner= name.getQualifier().getFullyQualifiedName();
				if (("StandardCharsets".equals(owner) || "java.nio.charset.StandardCharsets".equals(owner)) //$NON-NLS-1$ //$NON-NLS-2$
						&& CHARSET_STRINGS.contains(name.getName().getIdentifier().replace('_', '-'))) {
					return new TypeChangeInfo(UNSUPPORTED_ENCODING_EXCEPTION_FQN, UNSUPPORTED_ENCODING_EXCEPTION_SIMPLE);
				}
			}
		}
		return null;
	}

	private static List<?> arguments(ASTNode node) {
		if (node instanceof ClassInstanceCreation creation) return creation.arguments();
		if (node instanceof MethodInvocation invocation) return invocation.arguments();
		if (node instanceof SuperMethodInvocation invocation) return invocation.arguments();
		return List.of();
	}

	private static ASTNode unparenthesized(ASTNode node) {
		while (node instanceof ParenthesizedExpression expression) node= expression.getExpression();
		return node;
	}

	private static boolean isCharsetString(String value) {
		return CHARSET_STRINGS.contains(value.toUpperCase(Locale.ROOT));
	}
}
