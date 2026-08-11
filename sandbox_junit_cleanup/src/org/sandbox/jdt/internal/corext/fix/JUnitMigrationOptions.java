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
package org.sandbox.jdt.internal.corext.fix;

/** Cleanup option identifiers specific to JUnit migration execution policy. */
public final class JUnitMigrationOptions {

	/**
	 * Migrates every independently proven construct, keeps unresolved constructs,
	 * and adds deterministic {@code @todo} scaffolds with manual remediation.
	 * The default is {@code false}; strict fail-closed migration remains the normal
	 * behavior.
	 */
	public static final String BEST_EFFORT= "cleanup.junitcleanup_best_effort"; //$NON-NLS-1$

	private JUnitMigrationOptions() {
	}
}
