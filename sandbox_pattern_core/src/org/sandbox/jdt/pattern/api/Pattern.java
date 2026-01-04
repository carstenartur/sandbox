/*******************************************************************************
 * Copyright (c) 2026 Sandbox contributors.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Sandbox contributors - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.pattern.api;

import java.util.Objects;

/**
 * Represents a pattern to match against Java source code.
 * <p>
 * A pattern consists of a string representation (with placeholders prefixed by $)
 * and a kind that determines what type of AST nodes to match.
 * </p>
 * <p>
 * Example patterns:
 * <ul>
 * <li>{@code "$x.toString()"} - matches any toString() call</li>
 * <li>{@code "$list.size() == 0"} - matches size() == 0 comparisons</li>
 * <li>{@code "if ($cond) $stmt;"} - matches if statements</li>
 * </ul>
 * </p>
 * 
 * @since 1.0
 */
public final class Pattern {
	private final String value;
	private final PatternKind kind;
	private final String id;
	private final String displayName;

	/**
	 * Creates a new pattern with the specified value and kind.
	 * 
	 * @param value the pattern string with $ placeholders
	 * @param kind the kind of pattern (EXPRESSION or STATEMENT)
	 */
	public Pattern(String value, PatternKind kind) {
		this(value, kind, null, null);
	}

	/**
	 * Creates a new pattern with all attributes.
	 * 
	 * @param value the pattern string with $ placeholders
	 * @param kind the kind of pattern (EXPRESSION or STATEMENT)
	 * @param id optional unique identifier for this pattern
	 * @param displayName optional human-readable name
	 */
	public Pattern(String value, PatternKind kind, String id, String displayName) {
		this.value= Objects.requireNonNull(value, "Pattern value cannot be null");
		this.kind= Objects.requireNonNull(kind, "Pattern kind cannot be null");
		this.id= id;
		this.displayName= displayName;
	}

	/**
	 * Returns the pattern string.
	 * 
	 * @return the pattern string with $ placeholders
	 */
	public String getValue() {
		return value;
	}

	/**
	 * Returns the kind of this pattern.
	 * 
	 * @return the pattern kind
	 */
	public PatternKind getKind() {
		return kind;
	}

	/**
	 * Returns the optional unique identifier for this pattern.
	 * 
	 * @return the pattern ID, or null if not set
	 */
	public String getId() {
		return id;
	}

	/**
	 * Returns the optional human-readable name for this pattern.
	 * 
	 * @return the display name, or null if not set
	 */
	public String getDisplayName() {
		return displayName;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof Pattern)) {
			return false;
		}
		Pattern other= (Pattern) obj;
		return Objects.equals(value, other.value) && kind == other.kind;
	}

	@Override
	public int hashCode() {
		return Objects.hash(value, kind);
	}

	@Override
	public String toString() {
		return "Pattern[" + kind + ": " + value + "]";
	}
}
