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
package org.sandbox.jdt.cleanup.multifile;

/** Supported source-root expansion policies for coordinated cleanups. */
public enum SourceRootPolicy {
	/** Keep the cleanup inside the explicitly selected editable source roots. */
	EXPLICIT_SELECTED_ROOTS,
	/** Include all test roots plus explicitly selected editable support roots. */
	TEST_ROOTS_AND_SELECTED_SUPPORT,
	/**
	 * A production selection may expand through production and test roots; a
	 * test-only selection must not pull production sources into the migration.
	 */
	PRODUCTION_WITH_DEPENDENT_TESTS,
	/** Include every editable production and test root in the Java project. */
	COMPLETE_PROJECT
}
