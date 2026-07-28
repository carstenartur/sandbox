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

/** Sandbox-owned metadata that is not authoritative Git storage state. */
public interface SandboxRepositoryMetadataStore {

	/** Returns the optional Gitweb-style description for a normalized identity. */
	String description(String repositoryName);

	/** Stores or removes the description for a normalized identity. */
	void setDescription(String repositoryName, String description);
}
