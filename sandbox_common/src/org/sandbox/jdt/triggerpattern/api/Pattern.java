/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
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

import java.util.Objects;

/**
 * Represents a pattern for matching Java code snippets.
 * 
 * <p>A pattern consists of:</p>
 * <ul>
 *   <li>A pattern string with placeholders (e.g., {@code "$x + 1"})</li>
 *   <li>A {@link PatternKind} indicating whether it's an expression or statement</li>
 *   <li>Optional metadata (id, display name)</li>
 * </ul>
 * 
 * <p>Placeholders are identified by a {@code $} prefix (e.g., {@code $x}, {@code $var}, {@code $cond}).
 * When a pattern matches, placeholders are bound to actual AST nodes from the matched code.</p>
 * 
 * @since 1.2.2
 */
public final class Pattern {
	private final String value;
	private final PatternKind kind;
	private final String id;
	private final String displayName;
	private final String qualifiedType;
	
	/**
	 * Creates a new pattern with the specified value and kind.
	 * 
	 * @param value the pattern string with placeholders (e.g., {@code "$x + 1"})
	 * @param kind the kind of pattern (EXPRESSION or STATEMENT)
	 */
	public Pattern(String value, PatternKind kind) {
		this(value, kind, null, null, null);
	}
	
	/**
	 * Creates a new pattern with the specified value, kind, id, and display name.
	 * 
	 * @param value the pattern string with placeholders
	 * @param kind the kind of pattern (EXPRESSION or STATEMENT)
	 * @param id optional unique identifier for the pattern
	 * @param displayName optional human-readable name for the pattern
	 */
	public Pattern(String value, PatternKind kind, String id, String displayName) {
		this(value, kind, id, displayName, null);
	}
	
	/**
	 * Creates a new pattern with the specified value, kind, id, display name, and qualified type.
	 * 
	 * @param value the pattern string with placeholders
	 * @param kind the kind of pattern
	 * @param id optional unique identifier for the pattern
	 * @param displayName optional human-readable name for the pattern
	 * @param qualifiedType optional qualified type name (e.g., "org.junit.Before" for annotation patterns)
	 * @since 1.2.3
	 */
	public Pattern(String value, PatternKind kind, String id, String displayName, String qualifiedType) {
		this.value = Objects.requireNonNull(value, "Pattern value cannot be null"); //$NON-NLS-1$
		this.kind = Objects.requireNonNull(kind, "Pattern kind cannot be null"); //$NON-NLS-1$
		this.id = id;
		this.displayName = displayName;
		this.qualifiedType = qualifiedType;
	}
	
	/**
	 * Creates a new pattern with the specified value, kind, and qualified type.
	 * 
	 * @param value the pattern string with placeholders
	 * @param kind the kind of pattern
	 * @param qualifiedType qualified type name (e.g., "org.junit.Before")
	 * @since 1.2.3
	 */
	public Pattern(String value, PatternKind kind, String qualifiedType) {
		this(value, kind, null, null, qualifiedType);
	}
	
	/**
	 * Returns the pattern string.
	 * 
	 * @return the pattern string with placeholders
	 */
	public String getValue() {
		return value;
	}
	
	/**
	 * Returns the pattern kind.
	 * 
	 * @return the kind (EXPRESSION or STATEMENT)
	 */
	public PatternKind getKind() {
		return kind;
	}
	
	/**
	 * Returns the optional pattern ID.
	 * 
	 * @return the pattern ID or {@code null} if not set
	 */
	public String getId() {
		return id;
	}
	
	/**
	 * Returns the optional display name.
	 * 
	 * @return the display name or {@code null} if not set
	 */
	public String getDisplayName() {
		return displayName;
	}
	
	/**
	 * Returns the optional qualified type.
	 * 
	 * @return the qualified type name or {@code null} if not set
	 * @since 1.2.3
	 */
	public String getQualifiedType() {
		return qualifiedType;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		Pattern other = (Pattern) obj;
		return Objects.equals(value, other.value) 
				&& kind == other.kind 
				&& Objects.equals(id, other.id)
				&& Objects.equals(qualifiedType, other.qualifiedType);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(value, kind, id, qualifiedType);
	}
	
	@Override
	public String toString() {
		return "Pattern[kind=" + kind + ", value=" + value + ", id=" + id  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				+ (qualifiedType != null ? ", qualifiedType=" + qualifiedType : "") + "]"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}
}
