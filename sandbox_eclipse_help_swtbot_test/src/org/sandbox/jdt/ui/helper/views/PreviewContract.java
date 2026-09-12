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
package org.sandbox.jdt.ui.helper.views;

/** The selection contract asserted by a concrete Workbench scenario. */
enum PreviewContract {
	/** Stock LTK: one checkbox and a combined comparison for each source file. */
	FILE_COMBINED_DIFF,
	/** Coordinated preview: one checkbox for a whole, indivisible migration plan. */
	ATOMIC_CANDIDATE
}
