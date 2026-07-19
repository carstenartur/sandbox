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

		apply(candidateFile, "approve", "Source commit reviewed"); //$NON-NLS-1$ //$NON-NLS-2$

		MiningCandidate loaded = new CandidateStore(tempDir).loadAll().get(0);
		assertEquals(CandidateStatus.APPROVED, loaded.getStatus());
		assertEquals("reviewer", loaded.getTransitions().get(3).actor()); //$NON-NLS-1$
	}

	@Test
	void repeatedApprovalIsIdempotent() throws IOException {
		Path candidateFile = saveReadyCandidate();
		apply(candidateFile, "approve", "Source commit reviewed"); //$NON-NLS-1$ //$NON-NLS-2$

		apply(candidateFile, "approve", "Retry after workflow interruption"); //$NON-NLS-1$ //$NON-NLS-2$

		MiningCandidate loaded = new CandidateStore(tempDir).loadAll().get(0);
		assertEquals(CandidateStatus.APPROVED, loaded.getStatus());
		assertEquals(4, loaded.getTransitions().size());
	}

	@Test
	void marksApprovedCandidatePromotedAfterMerge() throws IOException {
		Path candidateFile = saveReadyCandidate();
		apply(candidateFile, "approve", null); //$NON-NLS-1$
		apply(candidateFile, "promote", "Promotion PR #42 merged"); //$NON-NLS-1$ //$NON-NLS-2$

		MiningCandidate loaded = new CandidateStore(tempDir).loadAll().get(0);
		assertEquals(CandidateStatus.PROMOTED, loaded.getStatus());
		assertEquals("Promotion PR #42 merged", loaded.getTransitions().get(4).reason()); //$NON-NLS-1$
	}

	@Test
	void repeatedPromotionIsIdempotent() throws IOException {
		Path candidateFile = saveReadyCandidate();
		apply(candidateFile, "approve", null); //$NON-NLS-1$
		apply(candidateFile, "promote", "Promotion PR #42 merged"); //$NON-NLS-1$ //$NON-NLS-2$

		apply(candidateFile, "promote", "Retry completion workflow"); //$NON-NLS-1$ //$NON-NLS-2$

		MiningCandidate loaded = new CandidateStore(tempDir).loadAll().get(0);
		assertEquals(CandidateStatus.PROMOTED, loaded.getStatus());
		assertEquals(5, loaded.getTransitions().size());
	}

	@Test
	void rejectsReadyCandidateWithReason() throws IOException {
		Path candidateFile = saveReadyCandidate();

		apply(candidateFile, "reject", "Unsafe overload change"); //$NON-NLS-1$ //$NON-NLS-2$

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

		assertThrows(IllegalStateException.class, () -> apply(candidateFile, "approve", null)); //$NON-NLS-1$
	}

	private void apply(Path candidateFile, String action, String reason) throws IOException {
		if (reason == null) {
			CandidateReviewCli.run(new String[] {
					"--candidate", candidateFile.toString(), //$NON-NLS-1$
					"--action", action, //$NON-NLS-1$
					"--actor", "reviewer" //$NON-NLS-1$ //$NON-NLS-2$
			});
		} else {
			CandidateReviewCli.run(new String[] {
					"--candidate", candidateFile.toString(), //$NON-NLS-1$
					"--action", action, //$NON-NLS-1$
					"--actor", "reviewer", //$NON-NLS-1$ //$NON-NLS-2$
					"--reason", reason //$NON-NLS-1$
			});
		}
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
