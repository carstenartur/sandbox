/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.multifile;

import org.sandbox.jdt.cleanup.multifile.CleanUpImpact;

/** Compatibility policy selected for one integer-domain migration. */
public enum IntEnumCompatibilityMode {

	/**
	 * Package-private source domain whose declaration and every caller are proven
	 * closed in the selected source scope. Numeric values are implementation
	 * details and are not copied into the generated enum.
	 */
	CLOSED_SOURCE(CleanUpImpact.PROJECT_CLOSED, true, false,
			"All declarations and callers are source-visible and migrated atomically."), //$NON-NLS-1$

	/**
	 * External numeric identity retained through explicit value fields,
	 * {@code fromValue(int)} handling and compatibility adapters. This mode is
	 * deliberately unavailable until persistence, serialization and unknown-value
	 * behavior are implemented and proven.
	 */
	NUMERIC_ADAPTER(CleanUpImpact.COMPATIBILITY_MANAGED, false, true,
			"External numeric identity requires explicit values and adapters; enum ordinal is forbidden."), //$NON-NLS-1$

	/** Migration requiring interactive decisions or non-Java resource updates. */
	MANUAL_EXTERNAL(CleanUpImpact.MANUAL_REFACTORING, false, true,
			"Persistence, wire, build or configuration resources require a dedicated manual refactoring."); //$NON-NLS-1$

	private final CleanUpImpact impact;
	private final boolean implemented;
	private final boolean explicitNumericValueRequired;
	private final String previewStatement;

	IntEnumCompatibilityMode(CleanUpImpact impact, boolean implemented,
			boolean explicitNumericValueRequired, String previewStatement) {
		this.impact= impact;
		this.implemented= implemented;
		this.explicitNumericValueRequired= explicitNumericValueRequired;
		this.previewStatement= previewStatement;
	}

	/** Impact level shown before execution. */
	public CleanUpImpact impact() {
		return impact;
	}

	/** Whether this mode may currently produce an automatic migration plan. */
	public boolean implemented() {
		return implemented;
	}

	/** Whether generated constants must preserve explicit numeric values. */
	public boolean explicitNumericValueRequired() {
		return explicitNumericValueRequired;
	}

	/** Stable user-facing compatibility statement. */
	public String previewStatement() {
		return previewStatement;
	}

	/**
	 * External identity must never be derived from declaration order.
	 *
	 * @return always {@code false}; {@code Enum.ordinal()} is prohibited
	 */
	public boolean ordinalAllowedForExternalIdentity() {
		return false;
	}
}
