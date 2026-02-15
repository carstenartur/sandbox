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
 * Rule that matches a specific multi-character operator sequence.
 *
 * <p>Used to highlight {@code =>}, {@code ::}, and {@code ;;} operators
 * in {@code .sandbox-hint} files.</p>
 *
 * @since 1.3.6
 */
public class OperatorRule implements IRule {

	private final String operator;
	private final IToken token;

	/**
	 * Creates an operator rule.
	 *
	 * @param operator the operator string to match (e.g., {@code "=>"})
	 * @param token the token to return on match
	 */
	public OperatorRule(String operator, IToken token) {
		this.operator = operator;
		this.token = token;
	}

	@Override
	public IToken evaluate(ICharacterScanner scanner) {
		int c = scanner.read();
		if (c == operator.charAt(0)) {
			if (operator.length() == 1) {
				return token;
			}
			int c2 = scanner.read();
			if (c2 == operator.charAt(1)) {
				return token;
			}
			scanner.unread();
		}
		scanner.unread();
		return Token.UNDEFINED;
	}
}
