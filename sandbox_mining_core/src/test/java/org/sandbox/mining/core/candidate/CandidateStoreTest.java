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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests candidate persistence, revisioning, and status queries. */
class CandidateStoreTest {

	@TempDir
	Path tempDir;

	@Test
	void savesAndLoadsCandidateMetadata() throws IOException {
		CandidateStore store = new CandidateStore(tempDir);
		MiningCandidate candidate = createCandidate("abc1234567890", 0); //$NON-NLS-1$
		candidate.setStatus(CandidateStatus.DSL_VALID);

		store.save(candidate);

		MiningCandidate loaded = store.loadAll().get(0);
		assertEquals(candidate.getCandidateId(), loaded.getCandidateId());
		assertEquals(CandidateStatus.DSL_VALID, loaded.getStatus());
		assertEquals(candidate.getRuleFingerprint(), loaded.getRuleFingerprint());
		assertEquals(candidate.getBehaviorFingerprint(), loaded.getBehaviorFingerprint());
		assertEquals(2, loaded.getSchemaVersion());
	}

	@Test
	void statusOnlyUpdateDoesNotIncrementRevision() throws IOException {
		CandidateStore store = new CandidateStore(tempDir);
		MiningCandidate candidate = createCandidate("abc1234567890", 0); //$NON-NLS-1$
		store.save(candidate);
		candidate.setStatus(CandidateStatus.DSL_VALID);

		store.save(candidate);

		MiningCandidate loaded = store.loadAll().get(0);
		assertEquals(1, loaded.getRevision());
		assertEquals(CandidateStatus.DSL_VALID, loaded.getStatus());
	}

	@Test
	void correctedContentIncrementsRevisionAndKeepsStableId() throws IOException {
		CandidateStore store = new CandidateStore(tempDir);
		MiningCandidate original = createCandidate("abc1234567890", 0); //$NON-NLS-1$
		store.save(original);
		MiningCandidate corrected = createCandidate("abc1234567890", 0); //$NON-NLS-1$
		corrected.setDslRule("$x * 1\n=> $x\n;;"); //$NON-NLS-1$
		corrected.setBeforeExample("class T { int m() { return 2 * 1; } }"); //$NON-NLS-1$
		corrected.setAfterExample("class T { int m() { return 2; } }"); //$NON-NLS-1$

		assertFalse(store.containsCandidate(corrected));
		store.save(corrected);

		List<MiningCandidate> loaded = store.loadAll();
		assertEquals(1, loaded.size());
		assertEquals(original.getCandidateId(), loaded.get(0).getCandidateId());
		assertEquals(2, loaded.get(0).getRevision());
	}

	@Test
	void allowsMultipleCandidatesFromSameCommitUsingOrdinal() throws IOException {
		CandidateStore store = new CandidateStore(tempDir);
		store.save(createCandidate("abc1234567890", 0)); //$NON-NLS-1$
		store.save(createCandidate("abc1234567890", 1)); //$NON-NLS-1$

		assertEquals(2, store.loadAll().size());
	}

	@Test
	void exactDuplicateIsDetected() throws IOException {
		CandidateStore store = new CandidateStore(tempDir);
		MiningCandidate candidate = createCandidate("abc1234567890", 0); //$NON-NLS-1$
		store.save(candidate);

		assertTrue(store.containsCandidate(createCandidate("abc1234567890", 0))); //$NON-NLS-1$
		assertFalse(store.containsCandidate(createCandidate("def5678901234", 0))); //$NON-NLS-1$
		assertFalse(store.containsCandidate(null));
	}

	@Test
	void loadsByOperationalStatus() throws IOException {
		CandidateStore store = new CandidateStore(tempDir);
		MiningCandidate discovered = createCandidate("a", 0); //$NON-NLS-1$
		MiningCandidate ready = createCandidate("b", 0); //$NON-NLS-1$
		ready.setStatus(CandidateStatus.READY_FOR_REVIEW);
		store.save(discovered);
		store.save(ready);

		assertEquals(1, store.loadByStatus(CandidateStatus.DISCOVERED).size());
		assertEquals(1, store.loadByStatus(CandidateStatus.READY_FOR_REVIEW).size());
	}

	@Test
	void emptyAndMissingDirectoriesReturnNoCandidates() throws IOException {
		assertTrue(new CandidateStore(tempDir.resolve("missing")).loadAll().isEmpty()); //$NON-NLS-1$
		Path empty = tempDir.resolve("empty"); //$NON-NLS-1$
		Files.createDirectories(empty);
		assertTrue(new CandidateStore(empty).loadAll().isEmpty());
	}

	@Test
	void saveCreatesStoreDirectory() throws IOException {
		Path storePath = tempDir.resolve("new-store"); //$NON-NLS-1$
		CandidateStore store = new CandidateStore(storePath);

		store.save(createCandidate("abc", 0)); //$NON-NLS-1$

		assertTrue(Files.isDirectory(storePath));
		assertEquals(storePath, store.getStoreDir());
	}

	private static MiningCandidate createCandidate(String commitHash, int ordinal) {
		MiningCandidate candidate = new MiningCandidate(
				"$x + 0\n=> $x\n;;", //$NON-NLS-1$
				"class T { int m() { return 1 + 0; } }", //$NON-NLS-1$
				"class T { int m() { return 1; } }", //$NON-NLS-1$
				"class T { int m() { return 1 + 2; } }", //$NON-NLS-1$
				"performance.sandbox-hint", //$NON-NLS-1$
				commitHash,
				"https://github.com/example/repo", //$NON-NLS-1$
				"arithmetic", //$NON-NLS-1$
				"Remove addition of zero", //$NON-NLS-1$
				"2026-01-01T00:00:00Z"); //$NON-NLS-1$
		candidate.setCandidateOrdinal(ordinal);
		return candidate;
	}
}
