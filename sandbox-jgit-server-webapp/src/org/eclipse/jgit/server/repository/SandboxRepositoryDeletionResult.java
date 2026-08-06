/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jgit.server.repository;

/** Application-owned summary of one logical repository deletion. */
public record SandboxRepositoryDeletionResult(int packRows, int reflogRows, int projectionRows) {

	/** Rejects invalid row counts before they cross the application boundary. */
	public SandboxRepositoryDeletionResult {
		if (packRows < 0 || reflogRows < 0 || projectionRows < 0) {
			throw new IllegalArgumentException("Deleted row counts must not be negative."); //$NON-NLS-1$
		}
	}

	/** Returns whether the deletion removed any persisted state. */
	public boolean deletedAnything() {
		return packRows > 0 || reflogRows > 0 || projectionRows > 0;
	}
}
