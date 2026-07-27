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
package org.sandbox.jgit.storage.integration;

import java.util.List;

import io.github.carstenartur.jgit.storage.hibernate.RepositoryName;
import io.github.carstenartur.jgit.storage.hibernate.config.CoreEntities;

/**
 * Sandbox-owned integration boundary to the released generic storage library.
 *
 * <p>New Sandbox code must enter database-backed Git storage through public
 * {@code io.github.carstenartur.jgit.storage.hibernate} APIs. The copied
 * {@code org.eclipse.jgit.storage.hibernate} implementation remains only as a
 * temporary migration source until its callers and Sandbox-specific services
 * have been separated.</p>
 */
public final class JGitStorageLibraryBoundary {

	private JGitStorageLibraryBoundary() {
	}

	/** Creates the external library's validated logical repository identity. */
	public static RepositoryName repositoryName(String value) {
		return new RepositoryName(value);
	}

	/** Returns the stable public entity-registration contract for generic core storage. */
	public static List<Class<?>> coreEntities() {
		return CoreEntities.annotatedClasses();
	}
}
