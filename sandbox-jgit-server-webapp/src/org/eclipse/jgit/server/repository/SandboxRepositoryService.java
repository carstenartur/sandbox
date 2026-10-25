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

	/** Opens the identity, creating it when absent; the returned handle is borrowed and must not be closed. */
	Repository openOrCreate(String name) throws IOException;

	/**
	 * Reads an existing identity without creating it. The callback must not retain or close the
	 * borrowed repository, or close this service. Released Core denies ref publication through
	 * this handle; the callback still belongs to trusted application code.
	 */
	default <T> T withExistingRepository(String name, RepositoryRead<T> read) throws IOException {
		throw new UnsupportedOperationException("Existing-only reads require the released Core backend.");
	}

	/** A scoped application read which may use JGit's checked IO operations. */
	@FunctionalInterface
	interface RepositoryRead<T> {
		T read(Repository repository) throws IOException;
	}

	/** Normalizes, opens or creates a repository and returns stable application metadata. */
	SandboxRepositoryInfo info(String name) throws IOException;

	/** Normalizes the identity and updates metadata without exposing a backend-specific repository. */
	SandboxRepositoryInfo setDescription(String name, String description) throws IOException;

	/**
	 * Deletes a closed logical repository when the selected backend supports coordinated deletion.
	 *
	 * <p>The released Core adapter overrides this operation. REST deletion remains disabled;
     * callers must use the separately authorized application lifecycle operation.</p>
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
