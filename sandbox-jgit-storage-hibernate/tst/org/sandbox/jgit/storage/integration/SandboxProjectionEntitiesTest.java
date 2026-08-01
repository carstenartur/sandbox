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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Contracts for the transitional external-Core projection boundary. */
class SandboxProjectionEntitiesTest {

	@Test
	void exposesOnlyExplicitDerivedProjectionMappings() {
		List<Class<?>> entities= SandboxProjectionEntities.annotatedClasses();

		assertEquals(List.of(
				"org.eclipse.jgit.storage.hibernate.entity.GitCommitIndex", //$NON-NLS-1$
				"org.eclipse.jgit.storage.hibernate.entity.JavaBlobIndex", //$NON-NLS-1$
				"org.eclipse.jgit.storage.hibernate.entity.FilePathHistory"), //$NON-NLS-1$
				entities.stream().map(Class::getName).toList());
		assertThrows(UnsupportedOperationException.class,
				() -> entities.add(Object.class));
	}

	@Test
	void doesNotCollideWithReleasedCoreEntityNames() {
		Set<String> coreEntityNames= new HashSet<>(
				JGitStorageLibraryBoundary.coreEntities().stream()
						.map(Class::getSimpleName).toList());

		assertTrue(SandboxProjectionEntities.annotatedClasses().stream()
				.map(Class::getSimpleName)
				.allMatch(name -> !coreEntityNames.contains(name)));
	}
}
