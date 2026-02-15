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
package org.sandbox.jdt.triggerpattern.api;

/**
 * Represents a single rewrite alternative in a transformation rule.
 * 
 * <p>Each alternative has a replacement pattern and an optional guard condition.
 * When the guard evaluates to {@code true} (or is {@code null} for unconditional
 * alternatives), this alternative's replacement is applied.</p>
 * 
 * <p>An alternative with a {@code null} condition acts as an "otherwise" catch-all.</p>
 * 
 * @param replacementPattern the replacement pattern with placeholders
 * @param condition the guard condition, or {@code null} for unconditional/otherwise
 * @since 1.3.2
 */
public record RewriteAlternative(String replacementPattern, GuardExpression condition) {
	
	/**
	 * Creates an unconditional (otherwise) rewrite alternative.
	 * 
	 * @param replacementPattern the replacement pattern
	 * @return an unconditional rewrite alternative
	 */
	public static RewriteAlternative otherwise(String replacementPattern) {
		return new RewriteAlternative(replacementPattern, null);
	}
	
	/**
	 * Returns {@code true} if this is an unconditional (otherwise) alternative.
	 * 
	 * @return {@code true} if no condition is set
	 */
	public boolean isOtherwise() {
		return condition == null;
	}
}
