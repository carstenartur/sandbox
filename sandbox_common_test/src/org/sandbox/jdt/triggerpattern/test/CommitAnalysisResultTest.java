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
package org.sandbox.jdt.triggerpattern.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.mining.analysis.CommitAnalysisResult;
import org.sandbox.jdt.triggerpattern.mining.analysis.CommitAnalysisResult.AnalysisStatus;
import org.sandbox.jdt.triggerpattern.mining.analysis.CommitInfo;
import org.sandbox.jdt.triggerpattern.mining.analysis.InferredRule;

/**
 * Tests for {@link CommitAnalysisResult} and {@link CommitInfo} record types.
 */
public class CommitAnalysisResultTest {

	@Test
	public void testPendingStatus() {
		CommitAnalysisResult result = new CommitAnalysisResult(
				"abc123", AnalysisStatus.PENDING, List.of(), null); //$NON-NLS-1$

		assertEquals("abc123", result.commitId()); //$NON-NLS-1$
		assertEquals(AnalysisStatus.PENDING, result.status());
		assertTrue(result.inferredRules().isEmpty());
	}

	@Test
	public void testDoneStatusWithRules() {
		InferredRule rule = new InferredRule(
				"old()", "new_()", PatternKind.METHOD_CALL, //$NON-NLS-1$ //$NON-NLS-2$
				0.9, List.of(), null);

		CommitAnalysisResult result = new CommitAnalysisResult(
				"def456", AnalysisStatus.DONE, //$NON-NLS-1$
				List.of(rule), Duration.ofMillis(250));

		assertEquals(AnalysisStatus.DONE, result.status());
		assertEquals(1, result.inferredRules().size());
		assertNotNull(result.analysisTime());
		assertEquals(250, result.analysisTime().toMillis());
	}

	@Test
	public void testNoRulesStatus() {
		CommitAnalysisResult result = new CommitAnalysisResult(
				"ghi789", AnalysisStatus.NO_RULES, //$NON-NLS-1$
				List.of(), Duration.ofMillis(100));

		assertEquals(AnalysisStatus.NO_RULES, result.status());
		assertTrue(result.inferredRules().isEmpty());
	}

	@Test
	public void testFailedStatus() {
		CommitAnalysisResult result = new CommitAnalysisResult(
				"jkl012", AnalysisStatus.FAILED, //$NON-NLS-1$
				List.of(), Duration.ofMillis(50));

		assertEquals(AnalysisStatus.FAILED, result.status());
	}

	@Test
	public void testCommitInfoRecord() {
		LocalDateTime now = LocalDateTime.now();
		CommitInfo info = new CommitInfo(
				"abc123def456", "abc123", //$NON-NLS-1$ //$NON-NLS-2$
				"Fix encoding issues", //$NON-NLS-1$
				"developer", now, 3); //$NON-NLS-1$

		assertEquals("abc123def456", info.id()); //$NON-NLS-1$
		assertEquals("abc123", info.shortId()); //$NON-NLS-1$
		assertEquals("Fix encoding issues", info.message()); //$NON-NLS-1$
		assertEquals("developer", info.author()); //$NON-NLS-1$
		assertEquals(now, info.timestamp());
		assertEquals(3, info.changedFileCount());
	}

	@Test
	public void testAllAnalysisStatusValues() {
		// Ensure all status values exist
		assertEquals(5, AnalysisStatus.values().length,
				"Should have 5 analysis status values"); //$NON-NLS-1$
		assertNotNull(AnalysisStatus.PENDING);
		assertNotNull(AnalysisStatus.ANALYZING);
		assertNotNull(AnalysisStatus.DONE);
		assertNotNull(AnalysisStatus.FAILED);
		assertNotNull(AnalysisStatus.NO_RULES);
	}
}
