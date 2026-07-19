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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests the authoritative candidate model and lifecycle. */
class MiningCandidateTest {

	@Test
	void defaultCandidateUsesSchemaVersionTwo() {
		MiningCandidate candidate = new MiningCandidate();

		assertEquals(2, candidate.getSchemaVersion());
		assertEquals(1, candidate.getRevision());
		assertEquals("21", candidate.getSourceVersion()); //$NON-NLS-1$
		assertEquals(CandidateStatus.DISCOVERED, candidate.getStatus());
		assertTrue(candidate.getTransitions().isEmpty());
	}

	@Test
	void candidateIdIsStableAcrossContentRevisions() {
		MiningCandidate first = createCandidate();
		MiningCandidate revised = createCandidate();
		revised.setDslRule("$x * 1\n=> $x\n;;"); //$NON-NLS-1$
		revised.setAfterExample("class T { int m() { return 2; } }"); //$NON-NLS-1$

		assertEquals(first.getCandidateId(), revised.getCandidateId());
		assertNotEquals(first.getRuleFingerprint(), revised.getRuleFingerprint());
		assertNotEquals(first.getBehaviorFingerprint(), revised.getBehaviorFingerprint());
	}

	@Test
	void ordinalAllowsMultipleCandidatesFromOneCommit() {
		MiningCandidate first = createCandidate();
		MiningCandidate second = createCandidate();
		second.setCandidateOrdinal(1);

		assertNotEquals(first.getCandidateId(), second.getCandidateId());
	}

	@Test
	void fingerprintsIgnoreFormattingOnlyDifferences() {
		MiningCandidate first = createCandidate();
		MiningCandidate second = createCandidate();
		second.setDslRule("  $x + 0  \r\n  => $x\r\n ;;  "); //$NON-NLS-1$
		second.setBeforeExample("class T {\n  int m() {   return 1 + 0; }\n}"); //$NON-NLS-1$

		assertEquals(first.getRuleFingerprint(), second.getRuleFingerprint());
		assertEquals(first.getBehaviorFingerprint(), second.getBehaviorFingerprint());
	}

	@Test
	void lifecycleTransitionsAreRecorded() {
		MiningCandidate candidate = createCandidate();

		candidate.transitionTo(CandidateStatus.DSL_VALID, "validator", "valid DSL"); //$NON-NLS-1$ //$NON-NLS-2$
		candidate.transitionTo(CandidateStatus.BEHAVIOR_VALID, "verifier", "examples passed"); //$NON-NLS-1$ //$NON-NLS-2$
		candidate.transitionTo(CandidateStatus.READY_FOR_REVIEW, "pipeline", "gates passed"); //$NON-NLS-1$ //$NON-NLS-2$

		assertEquals(CandidateStatus.READY_FOR_REVIEW, candidate.getStatus());
		assertEquals(3, candidate.getTransitions().size());
		assertEquals(CandidateStatus.DISCOVERED, candidate.getTransitions().get(0).from());
		assertEquals(CandidateStatus.DSL_VALID, candidate.getTransitions().get(0).to());
	}

	@Test
	void invalidLifecycleTransitionFails() {
		MiningCandidate candidate = createCandidate();

		assertThrows(IllegalStateException.class,
				() -> candidate.transitionTo(CandidateStatus.PROMOTED, "pipeline", "skip gates")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(CandidateStatus.DISCOVERED, candidate.getStatus());
	}

	@Test
	void rejectionRecordsReason() {
		MiningCandidate candidate = createCandidate();
		candidate.transitionTo(CandidateStatus.REJECTED, "reviewer", "unsafe type change"); //$NON-NLS-1$ //$NON-NLS-2$

		assertEquals(CandidateStatus.REJECTED, candidate.getStatus());
		assertEquals("unsafe type change", candidate.getRejectionReason()); //$NON-NLS-1$
	}

	@Test
	void fullConstructorPreservesDiscoveryData() {
		MiningCandidate candidate = createCandidate();

		assertEquals("$x + 0\n=> $x\n;;", candidate.getDslRule()); //$NON-NLS-1$
		assertEquals("performance.sandbox-hint", candidate.getTargetHintFile()); //$NON-NLS-1$
		assertEquals("abc1234567890", candidate.getSourceCommit()); //$NON-NLS-1$
		assertEquals("https://github.com/example/repo", candidate.getSourceRepo()); //$NON-NLS-1$
		assertEquals("Remove addition of zero", candidate.getSummary()); //$NON-NLS-1$
		assertNull(candidate.getVerification());
		assertTrue(candidate.toFileName().endsWith("-candidate.json")); //$NON-NLS-1$
	}

	private static MiningCandidate createCandidate() {
		return new MiningCandidate(
				"$x + 0\n=> $x\n;;", //$NON-NLS-1$
				"class T { int m() { return 1 + 0; } }", //$NON-NLS-1$
				"class T { int m() { return 1; } }", //$NON-NLS-1$
				"class T { int m() { return 1 + 2; } }", //$NON-NLS-1$
				"performance.sandbox-hint", //$NON-NLS-1$
				"abc1234567890", //$NON-NLS-1$
				"https://github.com/example/repo", //$NON-NLS-1$
				"arithmetic-simplification", //$NON-NLS-1$
				"Remove addition of zero", //$NON-NLS-1$
				"2026-01-01T00:00:00Z"); //$NON-NLS-1$
	}
}
