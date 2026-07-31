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

import java.util.Objects;

import org.eclipse.jface.text.rules.ICharacterScanner;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.Token;

/**
 * Rule that matches one complete operator sequence.
 *
 * <p>Used to highlight operators such as {@code =>}, {@code =>!},
 * {@code ::}, and {@code ;;} in {@code .sandbox-hint} files.</p>
 *
 * @since 1.3.6
 */
public class OperatorRule implements IRule {

	private final String operator;
	private final IToken token;

	/**
	 * Creates an operator rule.
	 *
	 * @param operator the non-empty operator string to match
	 * @param token the token to return on match
	 */
	public OperatorRule(String operator, IToken token) {
		this.operator= Objects.requireNonNull(operator, "operator"); //$NON-NLS-1$
		if (operator.isEmpty()) {
			throw new IllegalArgumentException("operator must not be empty"); //$NON-NLS-1$
		}
		this.token= Objects.requireNonNull(token, "token"); //$NON-NLS-1$
	}

	@Override
	public IToken evaluate(ICharacterScanner scanner) {
		for (int index= 0; index < operator.length(); index++) {
			int character= scanner.read();
			if (character == operator.charAt(index)) {
				continue;
			}
			for (int consumed= index; consumed >= 0; consumed--) {
				scanner.unread();
			}
			return Token.UNDEFINED;
		}
		return token;
	}
}
