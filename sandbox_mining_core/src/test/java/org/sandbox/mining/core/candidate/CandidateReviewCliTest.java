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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests explicit candidate review decisions. */
class CandidateReviewCliTest {

	@TempDir
	Path tempDir;

	@Test
	void approvesReadyCandidate() throws IOException {
		Path candidateFile = saveReadyCandidate();

		CandidateReviewCli.run(new String[] {
				"--candidate", candidateFile.toString(), //$NON-NLS-1$
				"--action", "approve", //$NON-NLS-1$ //$NON-NLS-2$
				"--actor", "reviewer", //$NON-NLS-1$ //$NON-NLS-2$
				"--reason", "Source commit reviewed" //$NON-NLS-1$ //$NON-NLS-2$
		});

		MiningCandidate loaded = new CandidateStore(tempDir).loadAll().get(0);
		assertEquals(CandidateStatus.APPROVED, loaded.getStatus());
		assertEquals("reviewer", loaded.getTransitions().get(3).actor()); //$NON-NLS-1$
	}

	@Test
	void rejectsReadyCandidateWithReason() throws IOException {
		Path candidateFile = saveReadyCandidate();

		CandidateReviewCli.run(new String[] {
				"--candidate", candidateFile.toString(), //$NON-NLS-1$
				"--action", "reject", //$NON-NLS-1$ //$NON-NLS-2$
				"--actor", "reviewer", //$NON-NLS-1$ //$NON-NLS-2$
				"--reason", "Unsafe overload change" //$NON-NLS-1$ //$NON-NLS-2$
		});

		MiningCandidate loaded = new CandidateStore(tempDir).loadAll().get(0);
		assertEquals(CandidateStatus.REJECTED, loaded.getStatus());
		assertEquals("Unsafe overload change", loaded.getRejectionReason()); //$NON-NLS-1$
	}

	@Test
	void rejectionWithoutReasonFails() throws IOException {
		Path candidateFile = saveReadyCandidate();

		assertThrows(IllegalArgumentException.class, () -> CandidateReviewCli.run(new String[] {
				"--candidate", candidateFile.toString(), //$NON-NLS-1$
				"--action", "reject", //$NON-NLS-1$ //$NON-NLS-2$
				"--actor", "reviewer" //$NON-NLS-1$ //$NON-NLS-2$
		}));
	}

	@Test
	void cannotApproveUnverifiedCandidate() throws IOException {
		MiningCandidate candidate = candidate();
		CandidateStore store = new CandidateStore(tempDir);
		store.save(candidate);
		Path candidateFile = tempDir.resolve(candidate.toFileName());

		assertThrows(IllegalStateException.class, () -> CandidateReviewCli.run(new String[] {
				"--candidate", candidateFile.toString(), //$NON-NLS-1$
				"--action", "approve", //$NON-NLS-1$ //$NON-NLS-2$
				"--actor", "reviewer" //$NON-NLS-1$ //$NON-NLS-2$
		}));
	}

	private Path saveReadyCandidate() throws IOException {
		MiningCandidate candidate = candidate();
		candidate.transitionTo(CandidateStatus.DSL_VALID, "validator", "valid"); //$NON-NLS-1$ //$NON-NLS-2$
		candidate.transitionTo(CandidateStatus.BEHAVIOR_VALID, "verifier", "valid"); //$NON-NLS-1$ //$NON-NLS-2$
		candidate.transitionTo(CandidateStatus.READY_FOR_REVIEW, "pipeline", "ready"); //$NON-NLS-1$ //$NON-NLS-2$
		CandidateStore store = new CandidateStore(tempDir);
		store.save(candidate);
		return tempDir.resolve(candidate.toFileName());
	}

	private static MiningCandidate candidate() {
		return new MiningCandidate(
				"$x + 0\n=> $x\n;;", //$NON-NLS-1$
				"class T { int m() { return 1 + 0; } }", //$NON-NLS-1$
				"class T { int m() { return 1; } }", //$NON-NLS-1$
				"class T { int m() { return 1 + 2; } }", //$NON-NLS-1$
				"performance.sandbox-hint", //$NON-NLS-1$
				"abc123", //$NON-NLS-1$
				"https://github.com/example/repo", //$NON-NLS-1$
				"arithmetic", //$NON-NLS-1$
				"Remove addition of zero", //$NON-NLS-1$
				"2026-07-19T00:00:00Z"); //$NON-NLS-1$
	}
}
