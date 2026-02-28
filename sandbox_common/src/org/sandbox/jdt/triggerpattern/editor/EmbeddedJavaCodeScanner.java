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
package org.sandbox.jdt.triggerpattern.editor;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.IWordDetector;
import org.eclipse.jface.text.rules.NumberRule;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.rules.SingleLineRule;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.text.rules.WordRule;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Display;

/**
 * Scanner for embedded Java code regions ({@code <? ?>}) in {@code .sandbox-hint} files.
 *
 * <p>Provides Java-style syntax highlighting including:</p>
 * <ul>
 *   <li>Java keywords ({@code public}, {@code class}, {@code return}, etc.) – bold dark blue</li>
 *   <li>Java primitive types ({@code boolean}, {@code int}, etc.) – dark blue</li>
 *   <li>String literals – blue</li>
 *   <li>Character literals – blue</li>
 *   <li>Number literals – dark magenta</li>
 *   <li>Java annotations ({@code @Override}, etc.) – gray italic</li>
 * </ul>
 *
 * @since 1.5.0
 */
public class EmbeddedJavaCodeScanner extends RuleBasedScanner {

	/**
	 * Java language keywords.
	 */
	private static final String[] JAVA_KEYWORDS = {
		"abstract", "assert", //$NON-NLS-1$ //$NON-NLS-2$
		"break", //$NON-NLS-1$
		"case", "catch", "class", "const", "continue", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
		"default", "do", //$NON-NLS-1$ //$NON-NLS-2$
		"else", "enum", "extends", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		"final", "finally", "for", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		"goto", //$NON-NLS-1$
		"if", "implements", "import", "instanceof", "interface", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
		"native", "new", //$NON-NLS-1$ //$NON-NLS-2$
		"package", "private", "protected", "public", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		"record", "return", //$NON-NLS-1$ //$NON-NLS-2$
		"sealed", "static", "strictfp", "super", "switch", "synchronized", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
		"this", "throw", "throws", "transient", "try", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
		"var", "void", "volatile", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		"when", "while", //$NON-NLS-1$ //$NON-NLS-2$
		"yield", //$NON-NLS-1$
	};

	/**
	 * Java primitive types and special literals.
	 */
	private static final String[] JAVA_TYPES = {
		"boolean", "byte", "char", "double", "float", "int", "long", "short", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$
		"true", "false", "null", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	};

	public EmbeddedJavaCodeScanner() {
		Color keywordColor = new Color(Display.getDefault(), 127, 0, 85);
		IToken keywordToken = new Token(new TextAttribute(keywordColor, null, SWT.BOLD));

		Color typeColor = new Color(Display.getDefault(), 127, 0, 85);
		IToken typeToken = new Token(new TextAttribute(typeColor));

		Color stringColor = new Color(Display.getDefault(), 42, 0, 255);
		IToken stringToken = new Token(new TextAttribute(stringColor));

		Color numberColor = new Color(Display.getDefault(), 128, 0, 128);
		IToken numberToken = new Token(new TextAttribute(numberColor));

		Color annotationColor = new Color(Display.getDefault(), 100, 100, 100);
		IToken annotationToken = new Token(new TextAttribute(annotationColor, null, SWT.ITALIC));

		List<IRule> rules = new ArrayList<>();

		// String literals
		rules.add(new SingleLineRule("\"", "\"", stringToken, '\\')); //$NON-NLS-1$ //$NON-NLS-2$
		rules.add(new SingleLineRule("'", "'", stringToken, '\\')); //$NON-NLS-1$ //$NON-NLS-2$

		// Annotations (simple detection)
		rules.add(new AnnotationRule(annotationToken));

		// Number literals
		rules.add(new NumberRule(numberToken));

		// Keywords and types
		WordRule wordRule = new WordRule(new JavaWordDetector(), Token.UNDEFINED);
		for (String keyword : JAVA_KEYWORDS) {
			wordRule.addWord(keyword, keywordToken);
		}
		for (String type : JAVA_TYPES) {
			wordRule.addWord(type, typeToken);
		}
		rules.add(wordRule);

		setRules(rules.toArray(new IRule[0]));
	}

	/**
	 * Word detector for Java identifiers.
	 */
	private static class JavaWordDetector implements IWordDetector {
		@Override
		public boolean isWordStart(char c) {
			return Character.isJavaIdentifierStart(c);
		}

		@Override
		public boolean isWordPart(char c) {
			return Character.isJavaIdentifierPart(c);
		}
	}

	/**
	 * Simple rule for detecting Java annotations ({@code @Name}).
	 */
	private static class AnnotationRule implements IRule {
		private final IToken token;

		AnnotationRule(IToken token) {
			this.token = token;
		}

		@Override
		public IToken evaluate(org.eclipse.jface.text.rules.ICharacterScanner scanner) {
			int c = scanner.read();
			if (c != '@') {
				scanner.unread();
				return Token.UNDEFINED;
			}
			int length = 1;
			c = scanner.read();
			length++;
			if (!Character.isJavaIdentifierStart((char) c)) {
				for (int i = 0; i < length; i++) {
					scanner.unread();
				}
				return Token.UNDEFINED;
			}
			while (true) {
				c = scanner.read();
				length++;
				if (c == org.eclipse.jface.text.rules.ICharacterScanner.EOF
						|| !Character.isJavaIdentifierPart((char) c)) {
					scanner.unread();
					return token;
				}
			}
		}
	}
}
