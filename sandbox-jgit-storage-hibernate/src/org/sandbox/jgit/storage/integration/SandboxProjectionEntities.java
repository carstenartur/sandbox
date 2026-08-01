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
package org.sandbox.jgit.storage.integration;

import java.util.List;

import org.eclipse.jgit.storage.hibernate.entity.FilePathHistory;
import org.eclipse.jgit.storage.hibernate.entity.GitCommitIndex;
import org.eclipse.jgit.storage.hibernate.entity.JavaBlobIndex;

/**
 * Transitional registry of Sandbox-owned derived projections that can share a
 * persistence context with the released Core entities.
 *
 * <p>The list deliberately excludes every copied storage mapping. In
 * particular, copied pack and reflog entities would collide with the released
 * Core entity names, while copied object and ref entities are not maintained by
 * the released pack/reftable backend. Keeping the list explicit prevents a
 * package scan from silently reintroducing those incompatible mappings during
 * the cut-over.</p>
 */
public final class SandboxProjectionEntities {

	private static final List<Class<?>> ANNOTATED_CLASSES= List.of(
			GitCommitIndex.class, JavaBlobIndex.class, FilePathHistory.class);

	private SandboxProjectionEntities() {
	}

	/**
	 * Return the immutable, explicitly reviewed projection entity list.
	 *
	 * @return compatible Sandbox projection entities in stable registration order
	 */
	public static List<Class<?>> annotatedClasses() {
		return ANNOTATED_CLASSES;
	}
}
