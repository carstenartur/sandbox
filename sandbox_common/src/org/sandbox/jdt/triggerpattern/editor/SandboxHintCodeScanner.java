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
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.rules.SingleLineRule;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.text.rules.WordRule;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Display;

/**
 * Scanner for code regions in {@code .sandbox-hint} files.
 *
 * <p>Highlights:</p>
 * <ul>
 *   <li>{@code ::} guard separator – purple bold</li>
 *   <li>{@code =>} rewrite arrow – purple bold</li>
 *   <li>{@code ;;} rule terminator – purple bold</li>
 *   <li>{@code $placeholder} – dark red</li>
 *   <li>{@code $variadic$} – dark red bold</li>
 *   <li>Guard function keywords – dark blue</li>
 *   <li>{@code otherwise} – dark blue bold</li>
 * </ul>
 *
 * @since 1.3.6
 */
public class SandboxHintCodeScanner extends RuleBasedScanner {

	/**
	 * Guard function keywords known to the built-in registry.
	 */
	private static final String[] GUARD_KEYWORDS = {
		"instanceof", //$NON-NLS-1$
		"matchesAny", //$NON-NLS-1$
		"matchesNone", //$NON-NLS-1$
		"referencedIn", //$NON-NLS-1$
		"hasNoSideEffect", //$NON-NLS-1$
		"sourceVersionGE", //$NON-NLS-1$
		"sourceVersionLE", //$NON-NLS-1$
		"sourceVersionBetween", //$NON-NLS-1$
		"elementKindMatches", //$NON-NLS-1$
		"isStatic", //$NON-NLS-1$
		"isFinal", //$NON-NLS-1$
		"hasAnnotation", //$NON-NLS-1$
		"isDeprecated", //$NON-NLS-1$
		"otherwise", //$NON-NLS-1$
		"contains", //$NON-NLS-1$
		"parent", //$NON-NLS-1$
		"enclosingMethod", //$NON-NLS-1$
	};

	public SandboxHintCodeScanner() {
		Color operatorColor = new Color(Display.getDefault(), 128, 0, 128);
		IToken operatorToken = new Token(new TextAttribute(operatorColor, null, SWT.BOLD));

		Color placeholderColor = new Color(Display.getDefault(), 128, 0, 0);
		IToken placeholderToken = new Token(new TextAttribute(placeholderColor));
		IToken variadicToken = new Token(new TextAttribute(placeholderColor, null, SWT.BOLD));

		Color keywordColor = new Color(Display.getDefault(), 0, 0, 192);
		IToken keywordToken = new Token(new TextAttribute(keywordColor, null, SWT.BOLD));

		Color stringColor = new Color(Display.getDefault(), 42, 0, 255);
		IToken stringToken = new Token(new TextAttribute(stringColor));

		List<IRule> rules = new ArrayList<>();

		// String literals in guard expressions
		rules.add(new SingleLineRule("\"", "\"", stringToken, '\\')); //$NON-NLS-1$ //$NON-NLS-2$

		// Operators: =>, ::, ;;
		rules.add(new OperatorRule("=>", operatorToken)); //$NON-NLS-1$
		rules.add(new OperatorRule("::", operatorToken)); //$NON-NLS-1$
		rules.add(new OperatorRule(";;", operatorToken)); //$NON-NLS-1$

		// Placeholders: $name and $variadic$
		rules.add(new PlaceholderRule(placeholderToken, variadicToken));

		// Guard function keywords
		WordRule keywordRule = new WordRule(new IWordDetector() {
			@Override
			public boolean isWordStart(char c) {
				return Character.isLetter(c);
			}

			@Override
			public boolean isWordPart(char c) {
				return Character.isLetterOrDigit(c) || c == '_';
			}
		}, Token.UNDEFINED);

		for (String keyword : GUARD_KEYWORDS) {
			keywordRule.addWord(keyword, keywordToken);
		}
		rules.add(keywordRule);

		setRules(rules.toArray(new IRule[0]));
	}
}
