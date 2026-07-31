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

import java.util.List;

/**
 * Represents one guarded rewrite alternative.
 *
 * <p>An alternative may contain a traditional Java replacement pattern, one or
 * more schema-validated structured AST actions, or both. Structured actions are
 * first-class data and are never hidden in syntactically invalid Java text.</p>
 *
 * @param replacementPattern replacement pattern with placeholders, or {@code null}
 * @param condition guard condition, or {@code null} for unconditional/otherwise
 * @param embeddedFixFunctionName optional legacy embedded fix reference
 * @param structuredActions ordered structured AST actions
 * @since 1.3.2
 */
public record RewriteAlternative(String replacementPattern, GuardExpression condition,
		String embeddedFixFunctionName, List<StructuredRewriteAction> structuredActions) {

	public RewriteAlternative {
		structuredActions= List.copyOf(structuredActions == null ? List.of() : structuredActions);
	}

	/** Creates a text alternative without an embedded fix or structured action. */
	public RewriteAlternative(String replacementPattern, GuardExpression condition) {
		this(replacementPattern, condition, null, List.of());
	}

	/** Backward-compatible constructor for one legacy embedded fix reference. */
	public RewriteAlternative(String replacementPattern, GuardExpression condition,
			String embeddedFixFunctionName) {
		this(replacementPattern, condition, embeddedFixFunctionName, List.of());
	}

	/** Creates a structured action-only alternative. */
	public static RewriteAlternative structured(List<StructuredRewriteAction> actions,
			GuardExpression condition) {
		return new RewriteAlternative(null, condition, null, actions);
	}

	/** Creates an unconditional text alternative. */
	public static RewriteAlternative otherwise(String replacementPattern) {
		return new RewriteAlternative(replacementPattern, null, null, List.of());
	}

	/** Returns whether this is the unconditional catch-all alternative. */
	public boolean isOtherwise() {
		return condition == null;
	}

	/** Returns whether this alternative references a legacy embedded fix function. */
	public boolean isEmbeddedFix() {
		return embeddedFixFunctionName != null && !embeddedFixFunctionName.isEmpty();
	}

	/** Returns whether this alternative has a Java replacement pattern. */
	public boolean hasTextReplacement() {
		return replacementPattern != null;
	}

	/** Returns whether this alternative carries structured AST actions. */
	public boolean hasStructuredActions() {
		return !structuredActions.isEmpty();
	}

	/** Returns whether this alternative can produce any source change. */
	public boolean hasRewrite() {
		return hasTextReplacement() || hasStructuredActions();
	}
}
