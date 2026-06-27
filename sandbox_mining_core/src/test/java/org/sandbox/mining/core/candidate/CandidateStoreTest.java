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
package org.sandbox.mining.core.candidate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link CandidateStore}.
 */
class CandidateStoreTest {

	@TempDir
	Path tempDir;

	@Test
	void testSaveAndLoadSingleCandidate() throws IOException {
		CandidateStore store = new CandidateStore(tempDir);
		MiningCandidate candidate = createCandidate("abc1234567890", CandidateStatus.DSL_VALID);

		store.save(candidate);

		List<MiningCandidate> loaded = store.loadAll();
		assertEquals(1, loaded.size());
		MiningCandidate loaded0 = loaded.get(0);
		assertEquals("abc1234567890", loaded0.getSourceCommit()); //$NON-NLS-1$
		assertEquals(CandidateStatus.DSL_VALID, loaded0.getStatus());
		assertEquals("$x + 0\n=> $x\n;;", loaded0.getDslRule()); //$NON-NLS-1$
		assertEquals("before example", loaded0.getBeforeExample()); //$NON-NLS-1$
		assertEquals("after example", loaded0.getAfterExample()); //$NON-NLS-1$
		assertEquals("negative example", loaded0.getNegativeExample()); //$NON-NLS-1$
	}

	@Test
	void testSaveMultipleCandidates() throws IOException {
		CandidateStore store = new CandidateStore(tempDir);
		store.save(createCandidate("abc1234567890", CandidateStatus.DISCOVERED));
		store.save(createCandidate("def5678901234", CandidateStatus.TEST_PASSED));
		store.save(createCandidate("ghi9012345678", CandidateStatus.READY_FOR_PR));

		List<MiningCandidate> loaded = store.loadAll();
		assertEquals(3, loaded.size());
	}

	@Test
	void testOverwriteExistingCandidate() throws IOException {
		CandidateStore store = new CandidateStore(tempDir);
		MiningCandidate candidate = createCandidate("abc1234567890", CandidateStatus.DISCOVERED);
		store.save(candidate);

		// Update status and save again
		candidate.setStatus(CandidateStatus.DSL_VALID);
		store.save(candidate);

		List<MiningCandidate> loaded = store.loadAll();
		assertEquals(1, loaded.size(), "Should have only one file (overwritten)"); //$NON-NLS-1$
		assertEquals(CandidateStatus.DSL_VALID, loaded.get(0).getStatus());
	}

	@Test
	void testLoadAllFromEmptyDirectory() throws IOException {
		CandidateStore store = new CandidateStore(tempDir.resolve("empty-subdir")); //$NON-NLS-1$
		List<MiningCandidate> loaded = store.loadAll();
		assertTrue(loaded.isEmpty(), "Empty directory should return empty list"); //$NON-NLS-1$
	}

	@Test
	void testLoadAllFromNonExistentDirectory() throws IOException {
		CandidateStore store = new CandidateStore(tempDir.resolve("nonexistent")); //$NON-NLS-1$
		List<MiningCandidate> loaded = store.loadAll();
		assertTrue(loaded.isEmpty(), "Non-existent directory should return empty list"); //$NON-NLS-1$
	}

	@Test
	void testLoadByStatus() throws IOException {
		CandidateStore store = new CandidateStore(tempDir);
		store.save(createCandidate("abc1234567890", CandidateStatus.DISCOVERED));
		store.save(createCandidate("def5678901234", CandidateStatus.DSL_VALID));
		store.save(createCandidate("ghi9012345678", CandidateStatus.DSL_VALID));

		List<MiningCandidate> discovered = store.loadByStatus(CandidateStatus.DISCOVERED);
		List<MiningCandidate> valid = store.loadByStatus(CandidateStatus.DSL_VALID);

		assertEquals(1, discovered.size());
		assertEquals(2, valid.size());
	}

	@Test
	void testContainsCommit() throws IOException {
		CandidateStore store = new CandidateStore(tempDir);
		store.save(createCandidate("abc1234567890", CandidateStatus.DISCOVERED));

		assertTrue(store.containsCommit("abc1234567890")); //$NON-NLS-1$
		assertFalse(store.containsCommit("def5678901234")); //$NON-NLS-1$
	}

	@Test
	void testContainsCommitNullAndBlank() throws IOException {
		CandidateStore store = new CandidateStore(tempDir);
		assertFalse(store.containsCommit(null));
		assertFalse(store.containsCommit("")); //$NON-NLS-1$
		assertFalse(store.containsCommit("   ")); //$NON-NLS-1$
	}

	@Test
	void testGetStoreDir() {
		CandidateStore store = new CandidateStore(tempDir);
		assertEquals(tempDir, store.getStoreDir());
	}

	@Test
	void testDirectoryCreatedOnSave() throws IOException {
		Path subDir = tempDir.resolve("new-subdir"); //$NON-NLS-1$
		assertFalse(java.nio.file.Files.exists(subDir), "Directory should not exist yet"); //$NON-NLS-1$

		CandidateStore store = new CandidateStore(subDir);
		store.save(createCandidate("abc1234567890", CandidateStatus.DISCOVERED));

		assertTrue(java.nio.file.Files.exists(subDir), "Directory should be created on save"); //$NON-NLS-1$
	}

	// --- helpers ---

	private MiningCandidate createCandidate(String commitHash, CandidateStatus status) {
		MiningCandidate candidate = new MiningCandidate(
				"$x + 0\n=> $x\n;;", //$NON-NLS-1$
				"before example", //$NON-NLS-1$
				"after example", //$NON-NLS-1$
				"negative example", //$NON-NLS-1$
				"performance.sandbox-hint", //$NON-NLS-1$
				commitHash,
				"https://github.com/example/repo", //$NON-NLS-1$
				"arithmetic", //$NON-NLS-1$
				"Remove addition of zero", //$NON-NLS-1$
				"2026-01-01T00:00:00Z"); //$NON-NLS-1$
		candidate.setStatus(status);
		return candidate;
	}
}
