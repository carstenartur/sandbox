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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory metadata store for tests and explicitly non-persistent deployments. */
public final class InMemorySandboxRepositoryMetadataStore implements SandboxRepositoryMetadataStore {

	private final Map<String, String> descriptions= new ConcurrentHashMap<>();

	@Override
	public String description(String repositoryName) {
		return descriptions.get(SandboxRepositoryNames.normalize(repositoryName));
	}

	@Override
	public void setDescription(String repositoryName, String description) {
		String normalized= SandboxRepositoryNames.normalize(repositoryName);
		if (description == null) {
			descriptions.remove(normalized);
		} else {
			descriptions.put(normalized, description);
		}
	}
}
