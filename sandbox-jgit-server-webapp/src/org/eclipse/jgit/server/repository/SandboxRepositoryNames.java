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

import java.util.Objects;

/** Canonical application-level repository identity normalization. */
public final class SandboxRepositoryNames {

	private SandboxRepositoryNames() {
	}

	/**
	 * Normalizes REST and Smart HTTP forms to one logical repository identity.
	 *
	 * @param name raw repository identity
	 * @return non-blank identity without a leading slash or trailing {@code .git}
	 */
	public static String normalize(String name) {
		String normalized= Objects.requireNonNull(name, "name").strip(); //$NON-NLS-1$
		while (normalized.startsWith("/")) { //$NON-NLS-1$
			normalized= normalized.substring(1);
		}
		if (normalized.endsWith(".git")) { //$NON-NLS-1$
			normalized= normalized.substring(0, normalized.length() - 4);
		}
		if (normalized.isBlank()) {
			throw new IllegalArgumentException("Repository name must not be blank."); //$NON-NLS-1$
		}
		return normalized;
	}
}
