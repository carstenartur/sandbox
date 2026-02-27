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
	private final String overridesType;
	private final ConstraintVariableType[] constraints;
	
	/**
	 * Creates a new pattern with the specified value, kind, id, display name, qualified type,
	 * overrides type, and type constraints.
	 * 
	 * @param value the pattern string with placeholders
	 * @param kind the kind of pattern
	 * @param id optional unique identifier for the pattern
	 * @param displayName optional human-readable name for the pattern
	 * @param qualifiedType optional qualified type name
	 * @param overridesType optional fully qualified type name for override constraint
	 * @param constraints optional type constraints for placeholder variables
	 */
	public Pattern(String value, PatternKind kind, String id, String displayName, String qualifiedType,
			String overridesType, ConstraintVariableType[] constraints) {
		this.value = Objects.requireNonNull(value, "Pattern value cannot be null"); //$NON-NLS-1$
		this.kind = Objects.requireNonNull(kind, "Pattern kind cannot be null"); //$NON-NLS-1$
		this.id = id;
		this.displayName = displayName;
		this.qualifiedType = qualifiedType;
		this.overridesType = overridesType;
		this.constraints = constraints == null ? null : constraints.clone();
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
	
	/**
	 * Returns the optional overrides type constraint.
	 * 
	 * <p>For METHOD_DECLARATION patterns, this specifies that the matched method
	 * must override a method from the specified type.</p>
	 * 
	 * @return the fully qualified type name that must be overridden, or {@code null} if not set
	 * @since 1.2.6
	 */
	public String getOverridesType() {
		return overridesType;
	}
	
	/**
	 * Returns the type constraints for placeholder variables.
	 * 
	 * <p>Each constraint maps a placeholder variable to an expected Java type.
	 * When binding resolution is available, matches are filtered to only include
	 * those where bound nodes satisfy the type constraints.</p>
	 * 
	 * @return the type constraints, or {@code null} if not set
	 * @since 1.4.0
	 */
	public ConstraintVariableType[] getConstraints() {
		if (constraints == null) {
			return null;
		}
		return constraints.clone();
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
				&& Objects.equals(qualifiedType, other.qualifiedType)
				&& Objects.equals(overridesType, other.overridesType);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(value, kind, id, qualifiedType, overridesType);
	}
	
	@Override
	public String toString() {
		return "Pattern[kind=" + kind + ", value=" + value + ", id=" + id  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				+ (qualifiedType != null ? ", qualifiedType=" + qualifiedType : "") //$NON-NLS-1$ //$NON-NLS-2$
				+ (overridesType != null ? ", overridesType=" + overridesType : "") + "]"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}
}
