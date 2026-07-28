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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.carstenartur.jgit.storage.hibernate.RepositoryName;

/** Public-API contract test for the first external-library adoption slice. */
class JGitStorageLibraryBoundaryTest {

	@Test
	void createsValidatedExternalRepositoryName() {
		RepositoryName name= JGitStorageLibraryBoundary.repositoryName("sandbox"); //$NON-NLS-1$

		assertEquals("sandbox", name.value()); //$NON-NLS-1$
		assertEquals("sandbox", name.toString()); //$NON-NLS-1$
		assertThrows(IllegalArgumentException.class,
				() -> JGitStorageLibraryBoundary.repositoryName(" ")); //$NON-NLS-1$
	}

	@Test
	void exposesOnlyReleasedCoreEntityContract() {
		List<Class<?>> entities= JGitStorageLibraryBoundary.coreEntities();

		assertEquals(List.of(
				"io.github.carstenartur.jgit.storage.hibernate.entity.GitPackEntity", //$NON-NLS-1$
				"io.github.carstenartur.jgit.storage.hibernate.entity.GitPackChunkEntity", //$NON-NLS-1$
				"io.github.carstenartur.jgit.storage.hibernate.entity.GitReflogEntity", //$NON-NLS-1$
				"io.github.carstenartur.jgit.storage.hibernate.entity.GitRepositoryLockEntity"), //$NON-NLS-1$
				entities.stream().map(Class::getName).toList());
		assertTrue(entities.stream().allMatch(type ->
				type.getPackageName().startsWith("io.github.carstenartur.jgit.storage.hibernate"))); //$NON-NLS-1$
	}
}
