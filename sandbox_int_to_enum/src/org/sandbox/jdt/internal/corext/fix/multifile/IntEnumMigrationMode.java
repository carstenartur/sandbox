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

/**
 * Compatibility contract for integer-domain to enum migrations.
 *
 * <p>Only {@link #CLOSED_FLOW_AUTOMATIC} is currently executable. The other
 * modes make future compatibility work explicit without weakening the current
 * conservative detector.</p>
 */
public enum IntEnumMigrationMode {

	/** Package-private source flow whose complete declaration/caller closure is proven. */
	CLOSED_FLOW_AUTOMATIC(CleanUpImpact.PROJECT_CLOSED, true, false,
			"Closed source flow: all declarations and callers are migrated atomically; no public, persisted or wire identity is claimed."), //$NON-NLS-1$

	/** Public or externally represented numeric identity with explicit adapters. */
	NUMERIC_ADAPTER_OPT_IN(CleanUpImpact.COMPATIBILITY_MANAGED, false, true,
			"Compatibility-managed numeric domain: explicit value/fromValue adapters and an unknown-value policy are required."), //$NON-NLS-1$

	/** Bit flags, aliases or ranged domains that require an interactive design decision. */
	MANUAL_DOMAIN_REFACTORING(CleanUpImpact.MANUAL_REFACTORING, false, true,
			"Manual domain refactoring: aliases, ranges or bit flags require an explicit representation such as EnumSet."); //$NON-NLS-1$

	private final CleanUpImpact impact;
	private final boolean implemented;
	private final boolean explicitNumericIdentityRequired;
	private final String previewStatement;

	IntEnumMigrationMode(CleanUpImpact impact, boolean implemented, boolean explicitNumericIdentityRequired,
			String previewStatement) {
		this.impact= impact;
		this.implemented= implemented;
		this.explicitNumericIdentityRequired= explicitNumericIdentityRequired;
		this.previewStatement= previewStatement;
	}

	/** Returns the cleanup impact shown in previews and reports. */
	public CleanUpImpact impact() {
		return impact;
	}

	/** Returns whether this mode may currently create rewrite operations. */
	public boolean implemented() {
		return implemented;
	}

	/** Returns whether constants require explicit stable numeric values and adapters. */
	public boolean explicitNumericIdentityRequired() {
		return explicitNumericIdentityRequired;
	}

	/** Returns the user-facing compatibility statement. */
	public String previewStatement() {
		return previewStatement;
	}

	/**
	 * External identity must never be derived from {@link Enum#ordinal()} in any
	 * migration mode.
	 */
	public boolean ordinalAllowedForExternalIdentity() {
		return false;
	}

	/** Returns the only mode available to the automatic cleanup. */
	public static IntEnumMigrationMode automaticMode() {
		return CLOSED_FLOW_AUTOMATIC;
	}
}
