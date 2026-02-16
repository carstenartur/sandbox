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
 * Severity levels for mining match scores, ranging from trivial (IGNORE) to
 * critical (WARNING).
 *
 * @since 1.2.6
 */
public enum MatchSeverity {
	/**
	 * The match is provably non-null and requires no change.
	 */
	IGNORE,

	/**
	 * The match is most likely safe; provided for information only.
	 */
	INFO,

	/**
	 * The match might need attention; the developer should decide.
	 */
	QUICKASSIST,

	/**
	 * The match is a recommended change for null safety.
	 */
	CLEANUP,

	/**
	 * The match has a high risk of {@link NullPointerException}.
	 */
	WARNING
}
