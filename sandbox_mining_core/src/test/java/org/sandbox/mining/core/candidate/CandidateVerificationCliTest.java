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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests end-to-end candidate verification and duplicate policy. */
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
