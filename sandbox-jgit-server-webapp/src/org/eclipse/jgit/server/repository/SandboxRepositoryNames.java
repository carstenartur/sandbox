/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jgit.server.repository;

import java.util.Objects;

/** Shared REST/Smart HTTP identity normalization. */
public final class SandboxRepositoryNames {
	private SandboxRepositoryNames() {}

	public static String normalize(String name) {
		String normalized = Objects.requireNonNull(name, "name").strip();
		if (normalized.startsWith("/")) {
			normalized = normalized.substring(1);
		}
		if (normalized.endsWith(".git")) {
			normalized = normalized.substring(0, normalized.length() - 4);
		}
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("Repository name must not be blank.");
		}
		return normalized;
	}
}
