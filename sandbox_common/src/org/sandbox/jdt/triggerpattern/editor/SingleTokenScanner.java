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

import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.RuleBasedScanner;

/**
 * A trivial scanner that returns a single token for the entire scanned region.
 *
 * <p>Used to apply a uniform text attribute to an entire partition
 * (e.g., comments or metadata directives).</p>
 *
 * @since 1.3.6
 */
public class SingleTokenScanner extends RuleBasedScanner {

	/**
	 * Creates a scanner that returns the given token for all content.
	 *
	 * @param token the token to return
	 */
	public SingleTokenScanner(IToken token) {
		setDefaultReturnToken(token);
		setRules(new IRule[0]);
	}
}
