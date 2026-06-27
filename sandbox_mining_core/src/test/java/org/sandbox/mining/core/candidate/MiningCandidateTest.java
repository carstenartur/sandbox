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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link MiningCandidate}.
 */
class MiningCandidateTest {

	@Test
	void testDefaultConstructor() {
		MiningCandidate candidate = new MiningCandidate();
		assertEquals(CandidateStatus.DISCOVERED, candidate.getStatus());
		assertNull(candidate.getDslRule());
		assertNull(candidate.getBeforeExample());
		assertNull(candidate.getAfterExample());
		assertNull(candidate.getNegativeExample());
	}

	@Test
	void testFullConstructor() {
		MiningCandidate candidate = new MiningCandidate(
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

		assertEquals(CandidateStatus.DISCOVERED, candidate.getStatus());
		assertEquals("$x + 0\n=> $x\n;;", candidate.getDslRule()); //$NON-NLS-1$
		assertEquals("class T { int m() { return 1 + 0; } }", candidate.getBeforeExample()); //$NON-NLS-1$
		assertEquals("class T { int m() { return 1; } }", candidate.getAfterExample()); //$NON-NLS-1$
		assertEquals("class T { int m() { return 1 + 2; } }", candidate.getNegativeExample()); //$NON-NLS-1$
		assertEquals("performance.sandbox-hint", candidate.getTargetHintFile()); //$NON-NLS-1$
		assertEquals("abc1234567890", candidate.getSourceCommit()); //$NON-NLS-1$
		assertEquals("https://github.com/example/repo", candidate.getSourceRepo()); //$NON-NLS-1$
		assertEquals("arithmetic-simplification", candidate.getCategory()); //$NON-NLS-1$
		assertEquals("Remove addition of zero", candidate.getSummary()); //$NON-NLS-1$
		assertEquals("2026-01-01T00:00:00Z", candidate.getDiscoveredAt()); //$NON-NLS-1$
		assertNull(candidate.getRejectionReason());
	}

	@Test
	void testSetters() {
		MiningCandidate candidate = new MiningCandidate();
		candidate.setStatus(CandidateStatus.DSL_VALID);
		candidate.setDslRule("$x + 0\n=> $x\n;;"); //$NON-NLS-1$
		candidate.setBeforeExample("before"); //$NON-NLS-1$
		candidate.setAfterExample("after"); //$NON-NLS-1$
		candidate.setNegativeExample("negative"); //$NON-NLS-1$
		candidate.setRejectionReason("bad rule"); //$NON-NLS-1$

		assertEquals(CandidateStatus.DSL_VALID, candidate.getStatus());
		assertEquals("$x + 0\n=> $x\n;;", candidate.getDslRule()); //$NON-NLS-1$
		assertEquals("before", candidate.getBeforeExample()); //$NON-NLS-1$
		assertEquals("after", candidate.getAfterExample()); //$NON-NLS-1$
		assertEquals("negative", candidate.getNegativeExample()); //$NON-NLS-1$
		assertEquals("bad rule", candidate.getRejectionReason()); //$NON-NLS-1$
	}

	@Test
	void testToFileName() {
		MiningCandidate candidate = createCandidate();
		String filename = candidate.toFileName();
		assertTrue(filename.endsWith("-candidate.json")); //$NON-NLS-1$
		assertEquals(64 + "-candidate.json".length(), filename.length()); //$NON-NLS-1$
	}

	@Test
	void testCandidateIdStableForSameContent() {
		MiningCandidate first = createCandidate();
		MiningCandidate second = createCandidate();
		assertEquals(first.getCandidateId(), second.getCandidateId());
	}

	@Test
	void testCandidateIdChangesForDifferentRule() {
		MiningCandidate first = createCandidate();
		MiningCandidate second = createCandidate();
		second.setDslRule("$x * 1\n=> $x\n;;"); //$NON-NLS-1$
		assertFalse(first.getCandidateId().equals(second.getCandidateId())); //$NON-NLS-1$
	}

	@Test
	void testCandidateIdGeneratedWhenFieldsMissing() {
		MiningCandidate candidate = new MiningCandidate();
		assertEquals(64, candidate.getCandidateId().length());
		assertTrue(candidate.toFileName().endsWith("-candidate.json")); //$NON-NLS-1$
	}

	@Test
	void testToStringContainsKeyFields() {
		MiningCandidate candidate = new MiningCandidate();
		candidate.setSourceCommit("abc1234"); //$NON-NLS-1$
		candidate.setStatus(CandidateStatus.PROMOTED);
		candidate.setCategory("string-modernization"); //$NON-NLS-1$
		candidate.setSummary("Test rule"); //$NON-NLS-1$

		String str = candidate.toString();
		assertNotNull(str);
		assertTrue(str.contains("abc1234")); //$NON-NLS-1$
		assertTrue(str.contains("PROMOTED")); //$NON-NLS-1$
		assertTrue(str.contains("string-modernization")); //$NON-NLS-1$
	}

	@Test
	void testAllStatusValues() {
		for (CandidateStatus status : CandidateStatus.values()) {
			MiningCandidate candidate = new MiningCandidate();
			candidate.setStatus(status);
			assertEquals(status, candidate.getStatus());
		}
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
