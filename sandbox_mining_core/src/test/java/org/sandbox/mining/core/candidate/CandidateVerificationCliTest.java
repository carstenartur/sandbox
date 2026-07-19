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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests end-to-end candidate verification, migration, and duplicate policy. */
class CandidateVerificationCliTest {

	@TempDir
	Path tempDir;

	@Test
	void verifiesOneCandidateAndMarksCrossOriginRuleDuplicate() throws IOException {
		Path candidateDir = tempDir.resolve("candidates"); //$NON-NLS-1$
		Path reportDir = tempDir.resolve("report"); //$NON-NLS-1$
		CandidateStore store = new CandidateStore(candidateDir);
		store.save(candidate("commit-a", "2026-01-01T00:00:00Z")); //$NON-NLS-1$ //$NON-NLS-2$
		store.save(candidate("commit-b", "2026-01-02T00:00:00Z")); //$NON-NLS-1$ //$NON-NLS-2$

		CandidateVerificationCli.run(new String[] {
				"--candidate-dir", candidateDir.toString(), //$NON-NLS-1$
				"--report-dir", reportDir.toString(), //$NON-NLS-1$
				"--source-version", "21" //$NON-NLS-1$ //$NON-NLS-2$
		});

		List<MiningCandidate> candidates = store.loadAll();
		assertEquals(1, candidates.stream()
				.filter(candidate -> candidate.getStatus() == CandidateStatus.READY_FOR_REVIEW)
				.count());
		MiningCandidate duplicate = candidates.stream()
				.filter(candidate -> candidate.getStatus() == CandidateStatus.DUPLICATE)
				.findFirst().orElseThrow();
		assertEquals(CandidateVerification.Stage.DUPLICATE,
				duplicate.getVerification().stage());
		assertTrue(duplicate.getVerification().message().contains("staged candidate")); //$NON-NLS-1$
		assertTrue(Files.isRegularFile(reportDir.resolve("candidates.json"))); //$NON-NLS-1$
		assertTrue(Files.isRegularFile(reportDir.resolve("candidates.html"))); //$NON-NLS-1$
	}

	@Test
	void failedEarlierCandidateDoesNotSuppressLaterValidRule() throws IOException {
		Path candidateDir = tempDir.resolve("failed-before-valid"); //$NON-NLS-1$
		CandidateStore store = new CandidateStore(candidateDir);
		MiningCandidate invalid = candidate("commit-invalid", "2026-01-01T00:00:00Z"); //$NON-NLS-1$ //$NON-NLS-2$
		invalid.setAfterExample("class T { int m() { return 99; } }"); //$NON-NLS-1$
		store.save(invalid);
		store.save(candidate("commit-valid", "2026-01-02T00:00:00Z")); //$NON-NLS-1$ //$NON-NLS-2$

		CandidateVerificationCli.run(new String[] {
				"--candidate-dir", candidateDir.toString(), //$NON-NLS-1$
				"--report-dir", tempDir.resolve("failed-before-valid-report").toString() //$NON-NLS-1$ //$NON-NLS-2$
		});

		List<MiningCandidate> candidates = store.loadAll();
		assertEquals(1, candidates.stream()
				.filter(candidate -> candidate.getStatus() == CandidateStatus.READY_FOR_REVIEW)
				.count());
		assertEquals(0, candidates.stream()
				.filter(candidate -> candidate.getStatus() == CandidateStatus.DUPLICATE)
				.count());
		MiningCandidate failed = candidates.stream()
				.filter(candidate -> "commit-invalid".equals(candidate.getSourceCommit())) //$NON-NLS-1$
				.findFirst().orElseThrow();
		assertFalse(failed.getVerification().successful());
		assertEquals(CandidateVerification.Stage.AFTER_REWRITE,
				failed.getVerification().stage());
	}

	@Test
	void approvedCandidateRemainsCanonicalForLaterDuplicate() throws IOException {
		Path candidateDir = tempDir.resolve("approved-candidates"); //$NON-NLS-1$
		CandidateStore store = new CandidateStore(candidateDir);
		MiningCandidate approved = candidate("commit-z", "2026-02-01T00:00:00Z"); //$NON-NLS-1$ //$NON-NLS-2$
		approved.setVerification(CandidateVerification.success(CandidateVerifier.VERSION, 1, 1));
		approved.transitionTo(CandidateStatus.DSL_VALID, "test", "valid"); //$NON-NLS-1$ //$NON-NLS-2$
		approved.transitionTo(CandidateStatus.BEHAVIOR_VALID, "test", "valid"); //$NON-NLS-1$ //$NON-NLS-2$
		approved.transitionTo(CandidateStatus.READY_FOR_REVIEW, "test", "valid"); //$NON-NLS-1$ //$NON-NLS-2$
		approved.transitionTo(CandidateStatus.APPROVED, "reviewer", "approved"); //$NON-NLS-1$ //$NON-NLS-2$
		store.save(approved);
		store.save(candidate("commit-a", "2026-01-01T00:00:00Z")); //$NON-NLS-1$ //$NON-NLS-2$

		CandidateVerificationCli.run(new String[] {
				"--candidate-dir", candidateDir.toString(), //$NON-NLS-1$
				"--report-dir", tempDir.resolve("approved-report").toString() //$NON-NLS-1$ //$NON-NLS-2$
		});

		List<MiningCandidate> candidates = store.loadAll();
		assertEquals(CandidateStatus.APPROVED, candidates.stream()
				.filter(candidate -> candidate.getCandidateId().equals(approved.getCandidateId()))
				.findFirst().orElseThrow().getStatus());
		assertEquals(1, candidates.stream()
				.filter(candidate -> candidate.getStatus() == CandidateStatus.DUPLICATE)
				.count());
	}

	@Test
	void marksCandidateThatDuplicatesCuratedRule() throws IOException {
		Path candidateDir = tempDir.resolve("curated-duplicate-candidates"); //$NON-NLS-1$
		Path curatedDir = tempDir.resolve("curated-rules"); //$NON-NLS-1$
		Files.createDirectories(curatedDir);
		Files.writeString(curatedDir.resolve("arithmetic.sandbox-hint"), """
				<!id: arithmetic>
				<!description: Existing reviewed arithmetic rules>

				$x + 0
				=> $x
				;;
				"""); //$NON-NLS-1$
		CandidateStore store = new CandidateStore(candidateDir);
		store.save(candidate("commit-new", "2026-03-01T00:00:00Z")); //$NON-NLS-1$ //$NON-NLS-2$

		CandidateVerificationCli.run(new String[] {
				"--candidate-dir", candidateDir.toString(), //$NON-NLS-1$
				"--report-dir", tempDir.resolve("curated-duplicate-report").toString(), //$NON-NLS-1$ //$NON-NLS-2$
				"--curated-hint-dir", curatedDir.toString() //$NON-NLS-1$
		});

		MiningCandidate duplicate = store.loadAll().get(0);
		assertEquals(CandidateStatus.DUPLICATE, duplicate.getStatus());
		assertEquals(CandidateVerification.Stage.DUPLICATE,
				duplicate.getVerification().stage());
		assertTrue(duplicate.getVerification().message()
				.contains("curated rule arithmetic.sandbox-hint#rule 1")); //$NON-NLS-1$
	}

	@Test
	void migratesMultipleLegacyCandidatesFromOneOriginWithoutCollision() throws IOException {
		Path candidateDir = tempDir.resolve("legacy-candidates"); //$NON-NLS-1$
		Files.createDirectories(candidateDir);
		writeLegacyCandidate(candidateDir.resolve("legacy-a-candidate.json"), //$NON-NLS-1$
				"$x + 0\n=> $x\n;;", //$NON-NLS-1$
				"class T { int m() { return 1 + 0; } }", //$NON-NLS-1$
				"class T { int m() { return 1; } }", //$NON-NLS-1$
				"class T { int m() { return 1 + 2; } }"); //$NON-NLS-1$
		writeLegacyCandidate(candidateDir.resolve("legacy-b-candidate.json"), //$NON-NLS-1$
				"$x * 1\n=> $x\n;;", //$NON-NLS-1$
				"class T { int m() { return 2 * 1; } }", //$NON-NLS-1$
				"class T { int m() { return 2; } }", //$NON-NLS-1$
				"class T { int m() { return 2 * 3; } }"); //$NON-NLS-1$

		CandidateVerificationCli.run(new String[] {
				"--candidate-dir", candidateDir.toString(), //$NON-NLS-1$
				"--report-dir", tempDir.resolve("legacy-report").toString() //$NON-NLS-1$ //$NON-NLS-2$
		});

		List<MiningCandidate> migrated = new CandidateStore(candidateDir).loadAll();
		assertEquals(2, migrated.size());
		assertTrue(migrated.stream().allMatch(candidate -> candidate.getSchemaVersion() == 2));
		assertTrue(migrated.stream().allMatch(candidate -> candidate.getRevision() == 1));
		Set<Integer> ordinals = migrated.stream()
				.map(MiningCandidate::getCandidateOrdinal)
				.collect(Collectors.toSet());
		assertEquals(Set.of(0, 1), ordinals);
		assertEquals(2, migrated.stream()
				.filter(candidate -> candidate.getStatus() == CandidateStatus.READY_FOR_REVIEW)
				.count());
		assertFalse(Files.exists(candidateDir.resolve("legacy-a-candidate.json"))); //$NON-NLS-1$
		assertFalse(Files.exists(candidateDir.resolve("legacy-b-candidate.json"))); //$NON-NLS-1$
	}

	private static void writeLegacyCandidate(Path path, String dslRule, String before,
			String after, String negative) throws IOException {
		String json = """
				{
				  "dslRule": %s,
				  "beforeExample": %s,
				  "afterExample": %s,
				  "negativeExample": %s,
				  "targetHintFile": "performance.sandbox-hint",
				  "sourceCommit": "same-commit",
				  "sourceRepo": "https://github.com/example/repo",
				  "category": "arithmetic",
				  "summary": "Legacy candidate",
				  "status": "DSL_VALID",
				  "discoveredAt": "2026-01-01T00:00:00Z"
				}
				""".formatted(jsonString(dslRule), jsonString(before),
				jsonString(after), jsonString(negative));
		Files.writeString(path, json, StandardCharsets.UTF_8);
	}

	private static String jsonString(String value) {
		return '"' + value.replace("\\", "\\\\") //$NON-NLS-1$ //$NON-NLS-2$
				.replace("\"", "\\\"") //$NON-NLS-1$ //$NON-NLS-2$
				.replace("\n", "\\n") + '"'; //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static MiningCandidate candidate(String commit, String discoveredAt) {
		return new MiningCandidate(
				"$x + 0\n=> $x\n;;", //$NON-NLS-1$
				"class T { int m() { return 1 + 0; } }", //$NON-NLS-1$
				"class T { int m() { return 1; } }", //$NON-NLS-1$
				"class T { int m() { return 1 + 2; } }", //$NON-NLS-1$
				"performance.sandbox-hint", //$NON-NLS-1$
				commit,
				"https://github.com/example/repo", //$NON-NLS-1$
				"arithmetic", //$NON-NLS-1$
				"Remove addition of zero", //$NON-NLS-1$
				discoveredAt);
	}
}
