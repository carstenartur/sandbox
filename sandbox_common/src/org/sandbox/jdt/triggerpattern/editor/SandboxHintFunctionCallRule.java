/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.editor;

import org.eclipse.jface.text.rules.ICharacterScanner;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.Token;

/** Highlights every Java-identifier-shaped function call without a keyword list. */
final class SandboxHintFunctionCallRule implements IRule {

	private final IToken token;

	SandboxHintFunctionCallRule(IToken token) {
		this.token= token;
	}

	@Override
	public IToken evaluate(ICharacterScanner scanner) {
		int consumed= 0;
		int identifierLength= 0;
		int character= scanner.read();
		consumed++;
		if (character == ICharacterScanner.EOF
				|| !Character.isJavaIdentifierStart((char) character)) {
			unread(scanner, consumed);
			return Token.UNDEFINED;
		}
		identifierLength= 1;
		while (true) {
			character= scanner.read();
			consumed++;
			if (character != ICharacterScanner.EOF
					&& Character.isJavaIdentifierPart((char) character)) {
				identifierLength++;
				continue;
			}
			break;
		}
		while (character != ICharacterScanner.EOF && Character.isWhitespace((char) character)) {
			character= scanner.read();
			consumed++;
		}
		if (character == '(') {
			unread(scanner, consumed - identifierLength);
			return token;
		}
		unread(scanner, consumed);
		return Token.UNDEFINED;
	}

	private static void unread(ICharacterScanner scanner, int count) {
		for (int index= 0; index < count; index++) {
			scanner.unread();
		}
	}
}
