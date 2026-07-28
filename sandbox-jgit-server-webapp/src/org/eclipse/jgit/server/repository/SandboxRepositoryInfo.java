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

import java.util.Objects;

/** Stable application-owned repository metadata exposed to REST resources. */
public record SandboxRepositoryInfo(String name, String description) {

	/** Validates repository identity while allowing an absent description. */
	public SandboxRepositoryInfo {
		name= Objects.requireNonNull(name, "name").strip(); //$NON-NLS-1$
		if (name.isEmpty()) {
			throw new IllegalArgumentException("Repository name must not be blank."); //$NON-NLS-1$
		}
	}
}
