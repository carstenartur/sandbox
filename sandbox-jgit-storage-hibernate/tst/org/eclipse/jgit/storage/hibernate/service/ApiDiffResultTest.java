/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.eclipse.jgit.storage.hibernate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.eclipse.jgit.storage.hibernate.entity.JavaBlobIndex;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ApiDiffResult} and {@link ApiChangeEntry} DTOs.
 */
class ApiDiffResultTest {

	@Test
	void defaultConstructorCreatesEmptyDto() {
		ApiDiffResult result = new ApiDiffResult();
		assertNull(result.getAddedFiles());
		assertNull(result.getRemovedFiles());
		assertNull(result.getChangedFiles());
	}

	@Test
	void constructorSetsAllFields() {
		JavaBlobIndex added = new JavaBlobIndex();
		JavaBlobIndex removed = new JavaBlobIndex();
		ApiChangeEntry changed = new ApiChangeEntry();

		ApiDiffResult result = new ApiDiffResult(List.of(added),
				List.of(removed), List.of(changed));

		assertEquals(1, result.getAddedFiles().size());
		assertEquals(1, result.getRemovedFiles().size());
		assertEquals(1, result.getChangedFiles().size());
	}

	@Test
	void settersWorkCorrectly() {
		ApiDiffResult result = new ApiDiffResult();
		List<JavaBlobIndex> added = List.of(new JavaBlobIndex());
		List<JavaBlobIndex> removed = List.of(new JavaBlobIndex());
		List<ApiChangeEntry> changed = List.of(new ApiChangeEntry());

		result.setAddedFiles(added);
		result.setRemovedFiles(removed);
		result.setChangedFiles(changed);

		assertEquals(added, result.getAddedFiles());
		assertEquals(removed, result.getRemovedFiles());
		assertEquals(changed, result.getChangedFiles());
	}

	@Test
	void emptyListsAreValid() {
		ApiDiffResult result = new ApiDiffResult(List.of(), List.of(),
				List.of());
		assertNotNull(result.getAddedFiles());
		assertNotNull(result.getRemovedFiles());
		assertNotNull(result.getChangedFiles());
		assertEquals(0, result.getAddedFiles().size());
	}

	// --- ApiChangeEntry tests ---

	@Test
	void apiChangeEntryDefaultConstructor() {
		ApiChangeEntry entry = new ApiChangeEntry();
		assertNull(entry.getBefore());
		assertNull(entry.getAfter());
		assertNull(entry.getChangeDescription());
	}

	@Test
	void apiChangeEntryConstructorSetsFields() {
		JavaBlobIndex before = new JavaBlobIndex();
		JavaBlobIndex after = new JavaBlobIndex();
		String desc = "methods changed"; //$NON-NLS-1$

		ApiChangeEntry entry = new ApiChangeEntry(before, after, desc);

		assertEquals(before, entry.getBefore());
		assertEquals(after, entry.getAfter());
		assertEquals(desc, entry.getChangeDescription());
	}

	@Test
	void apiChangeEntrySetters() {
		ApiChangeEntry entry = new ApiChangeEntry();
		JavaBlobIndex before = new JavaBlobIndex();
		JavaBlobIndex after = new JavaBlobIndex();

		entry.setBefore(before);
		entry.setAfter(after);
		entry.setChangeDescription("visibility changed"); //$NON-NLS-1$

		assertEquals(before, entry.getBefore());
		assertEquals(after, entry.getAfter());
		assertEquals("visibility changed", entry.getChangeDescription()); //$NON-NLS-1$
	}

	@Test
	void apiChangeEntryAllowsNullFields() {
		ApiChangeEntry entry = new ApiChangeEntry(null, null, null);
		assertNull(entry.getBefore());
		assertNull(entry.getAfter());
		assertNull(entry.getChangeDescription());
	}
}
