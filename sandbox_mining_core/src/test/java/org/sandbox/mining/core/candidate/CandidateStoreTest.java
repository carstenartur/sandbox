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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sandbox.jdt.triggerpattern.llm.SourceEvidence;

import com.google.gson.Gson;

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
	void rejectsInvalidStoredIdBeforeResolvingFiles() throws IOException {
		CandidateStore store = new CandidateStore(tempDir);
		MiningCandidate valid = createCandidate("first", 0); //$NON-NLS-1$
		MiningCandidate invalid = createCandidate("second", 0); //$NON-NLS-1$
		Path record = tempDir.resolve(invalid.toFileName());
		String id = "archive/item"; //$NON-NLS-1$
		invalid.setCandidateId(id);
		assertThrows(IllegalArgumentException.class, () -> store.save(invalid));
		Files.writeString(record, new Gson().toJson(invalid), StandardCharsets.UTF_8);
		store.save(valid);

		assertTrue(store.findById(id).isEmpty());
		assertEquals(List.of(valid.getCandidateId()), store.loadAll().stream().map(MiningCandidate::getCandidateId).toList());
	}

	@Test
	void storeAndReviewRejectACanonicalFileForADifferentId() throws IOException {
		MiningCandidate first = createCandidate("first", 0); //$NON-NLS-1$
		MiningCandidate second = createCandidate("second", 0); //$NON-NLS-1$
		Path file = tempDir.resolve(first.toFileName());
		Files.writeString(file, new Gson().toJson(second), StandardCharsets.UTF_8);

		CandidateStore store = new CandidateStore(tempDir);
		assertTrue(store.findById(first.getCandidateId()).isEmpty());
		assertTrue(store.loadAll().isEmpty());
		assertThrows(IllegalArgumentException.class, () -> CandidateReviewCli.run(new String[] {
				"--candidate", file.toString(), "--action", "reject", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				"--reason", "reviewed", "--actor", "reviewer" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		}));
		assertFalse(Files.exists(tempDir.resolve(second.toFileName())));
	}

	@Test
	void rejectsFileSystemRootAsCandidateFile() {
		Path root = tempDir.toAbsolutePath().getRoot();
		assertThrows(IllegalArgumentException.class, () -> CandidateStore.readCandidate(root));
	}

	@Test
	void leavesPreexistingTemporaryFilesAlone() throws IOException {
		CandidateStore store = new CandidateStore(tempDir);
		MiningCandidate candidate = createCandidate("first", 0); //$NON-NLS-1$
		Path oldTemporaryName = tempDir.resolve(candidate.toFileName() + ".tmp"); //$NON-NLS-1$
		Files.writeString(oldTemporaryName, "other writer", StandardCharsets.UTF_8); //$NON-NLS-1$

		store.save(candidate);

		assertEquals("other writer", Files.readString(oldTemporaryName, StandardCharsets.UTF_8)); //$NON-NLS-1$
		try (var paths = Files.list(tempDir)) {
			assertEquals(2L, paths.count());
		}
	}

	@Test
	@EnabledOnOs({ OS.LINUX, OS.MAC })
	void excludesSymbolicLinksFromStoredRecords() throws IOException {
		MiningCandidate candidate = createCandidate("test", 0); //$NON-NLS-1$
		Path sourceDir = tempDir.resolve("source"); //$NON-NLS-1$
		new CandidateStore(sourceDir).save(candidate);
		Path storeDir = Files.createDirectory(tempDir.resolve("review")); //$NON-NLS-1$
		Files.createSymbolicLink(storeDir.resolve(candidate.toFileName()), sourceDir.resolve(candidate.toFileName()));

		CandidateStore store = new CandidateStore(storeDir);
		assertTrue(store.loadAll().isEmpty());
		assertTrue(store.findById(candidate.getCandidateId()).isEmpty());
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
	void correctedContentStartsUnverifiedRevisionAndKeepsStableId() throws IOException {
		CandidateStore store = new CandidateStore(tempDir);
		MiningCandidate original = createCandidate("abc1234567890", 0); //$NON-NLS-1$
		original.setStatus(CandidateStatus.READY_FOR_REVIEW);
		original.setVerification(CandidateVerification.success(CandidateVerifier.VERSION, 1, 1));
		store.save(original);

		MiningCandidate corrected = createCandidate("abc1234567890", 0); //$NON-NLS-1$
		corrected.setStatus(CandidateStatus.READY_FOR_REVIEW);
		corrected.setVerification(CandidateVerification.success(CandidateVerifier.VERSION, 1, 1));
		corrected.setDslRule("$x * 1\n=> $x\n;;"); //$NON-NLS-1$
		corrected.setBeforeExample("class T { int m() { return 2 * 1; } }"); //$NON-NLS-1$
		corrected.setAfterExample("class T { int m() { return 2; } }"); //$NON-NLS-1$

		assertFalse(store.containsCandidate(corrected));
		store.save(corrected);

		List<MiningCandidate> loaded = store.loadAll();
		assertEquals(1, loaded.size());
		MiningCandidate revision = loaded.get(0);
		assertEquals(original.getCandidateId(), revision.getCandidateId());
		assertEquals(2, revision.getRevision());
		assertEquals(CandidateStatus.DISCOVERED, revision.getStatus());
		assertNull(revision.getVerification());
		assertNull(revision.getRejectionReason());
		CandidateTransition boundary = revision.getTransitions().get(
				revision.getTransitions().size() - 1);
		assertEquals(CandidateStatus.READY_FOR_REVIEW, boundary.from());
		assertEquals(CandidateStatus.DISCOVERED, boundary.to());
		assertEquals("CandidateStore", boundary.actor()); //$NON-NLS-1$
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void sourceMetadataChangeAlsoRequiresNewVerification(boolean evidence) throws IOException {
		CandidateStore store = new CandidateStore(tempDir);
		MiningCandidate original = createCandidate("abc1234567890", 0); //$NON-NLS-1$
		original.setStatus(CandidateStatus.READY_FOR_REVIEW);
		original.setVerification(CandidateVerification.success(CandidateVerifier.VERSION, 1, 1));
		store.save(original);

		MiningCandidate changedVersion = store.loadAll().get(0);
		if (evidence) {
			changedVersion.setSourceEvidence(new SourceEvidence("Test.java", "1 + 0", "1", "changed hunk"));
		} else {
			changedVersion.setSourceVersion("17"); //$NON-NLS-1$
		}
		store.save(changedVersion);

		MiningCandidate revision = store.loadAll().get(0);
		assertEquals(2, revision.getRevision());
		assertEquals(CandidateStatus.DISCOVERED, revision.getStatus());
		assertNull(revision.getVerification());
	}

	@Test
	void promotedCandidateContentIsImmutable() throws IOException {
		CandidateStore store = new CandidateStore(tempDir);
		MiningCandidate promoted = createCandidate("abc1234567890", 0); //$NON-NLS-1$
		promoted.setStatus(CandidateStatus.PROMOTED);
		store.save(promoted);

		MiningCandidate changed = store.loadAll().get(0);
		changed.setDslRule("$x * 1\n=> $x\n;;"); //$NON-NLS-1$

		assertThrows(IllegalStateException.class, () -> store.save(changed));
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
		MiningCandidate otherSourceVersion = createCandidate("abc1234567890", 0); //$NON-NLS-1$
		otherSourceVersion.setSourceVersion("17"); //$NON-NLS-1$
		assertFalse(store.containsCandidate(otherSourceVersion));
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
