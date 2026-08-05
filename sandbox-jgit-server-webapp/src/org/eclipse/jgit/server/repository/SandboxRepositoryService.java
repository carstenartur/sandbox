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
package org.eclipse.jgit.server.repository;

import java.io.IOException;

import org.eclipse.jgit.lib.Repository;

/** Application-owned repository lifecycle boundary for REST and Smart HTTP. */
public interface SandboxRepositoryService extends AutoCloseable {

	/** Opens the supplied repository identity, normalizing and creating it when absent. */
	Repository openOrCreate(String name) throws IOException;

	/** Normalizes, opens or creates a repository and returns stable application metadata. */
	SandboxRepositoryInfo info(String name) throws IOException;

	/** Normalizes the identity and updates metadata without exposing a backend-specific repository. */
	SandboxRepositoryInfo setDescription(String name, String description) throws IOException;

	/**
	 * Deletes a closed logical repository when the selected backend supports coordinated deletion.
	 *
	 * <p>The released Core adapter overrides this operation. The copied transition backend deliberately
	 * keeps deletion disabled until normal startup has completed the Core cut-over.</p>
	 */
	default SandboxRepositoryDeletionResult delete(String name) throws IOException {
		throw new UnsupportedOperationException("Repository deletion requires the released Core backend."); //$NON-NLS-1$
	}

	/** Returns whether this process already owns an open handle for the normalized identity. */
	boolean isOpen(String name);

	/** Closes all application-owned repository handles. */
	@Override
	void close();
}
