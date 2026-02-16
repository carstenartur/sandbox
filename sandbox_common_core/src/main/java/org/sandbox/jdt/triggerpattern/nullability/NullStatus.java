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
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.nullability;

/**
 * Represents the nullability status of a variable or expression.
 *
 * @since 1.2.6
 */
public enum NullStatus {
	/**
	 * The expression is guaranteed to be non-null (e.g., {@code new},
	 * known non-null type, or guarded by a null check).
	 */
	NON_NULL,

	/**
	 * The expression may be null (e.g., {@code Map.get()}, {@code @Nullable}
	 * annotation, or null check found elsewhere for the same variable).
	 */
	NULLABLE,

	/**
	 * The expression might be null based on cross-reference analysis
	 * (e.g., a null check for the same type exists in another method).
	 */
	POTENTIALLY_NULLABLE,

	/**
	 * The nullability could not be determined.
	 */
	UNKNOWN
}
