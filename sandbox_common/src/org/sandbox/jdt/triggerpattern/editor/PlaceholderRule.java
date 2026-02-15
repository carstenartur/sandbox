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

import org.eclipse.jface.text.rules.ICharacterScanner;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.Token;

/**
 * Rule that matches placeholder tokens in {@code .sandbox-hint} files.
 *
 * <p>Matches two forms:</p>
 * <ul>
 *   <li>{@code $name} – single placeholder (uses {@code placeholderToken})</li>
 *   <li>{@code $name$} – variadic placeholder (uses {@code variadicToken})</li>
 * </ul>
 *
 * @since 1.3.6
 */
public class PlaceholderRule implements IRule {

	private final IToken placeholderToken;
	private final IToken variadicToken;

	/**
	 * Creates a placeholder rule.
	 *
	 * @param placeholderToken token for single placeholders ({@code $name})
	 * @param variadicToken token for variadic placeholders ({@code $name$})
	 */
	public PlaceholderRule(IToken placeholderToken, IToken variadicToken) {
		this.placeholderToken = placeholderToken;
		this.variadicToken = variadicToken;
	}

	@Override
	public IToken evaluate(ICharacterScanner scanner) {
		int c = scanner.read();
		if (c != '$') {
			scanner.unread();
			return Token.UNDEFINED;
		}

		// Read the placeholder name
		int length = 1; // '$' already read
		boolean hasNameChars = false;
		while (true) {
			c = scanner.read();
			length++;
			if (c == ICharacterScanner.EOF) {
				// Unread all and fail
				for (int i = 0; i < length; i++) {
					scanner.unread();
				}
				return Token.UNDEFINED;
			}
			if (c == '$') {
				// Variadic: $name$
				if (hasNameChars) {
					return variadicToken;
				}
				// Just "$$" - not a valid placeholder
				for (int i = 0; i < length; i++) {
					scanner.unread();
				}
				return Token.UNDEFINED;
			}
			if (Character.isLetterOrDigit((char) c) || c == '_') {
				hasNameChars = true;
			} else {
				// End of placeholder name
				scanner.unread();
				if (hasNameChars) {
					return placeholderToken;
				}
				// Just '$' followed by non-name char
				scanner.unread(); // unread the '$'
				return Token.UNDEFINED;
			}
		}
	}
}
