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
package org.sandbox.jdt.triggerpattern.llm;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation.TrafficLight;
import org.sandbox.jdt.triggerpattern.llm.PromptBuilder.CommitData;

/**
 * Tests for {@link ScreeningPromptBuilder} and {@link CommitScreening}.
 */
class ScreeningPromptBuilderTest {

	@Test
	void testScreeningPromptDoesNotAskForExpensiveCandidateFields() {
		CommitData commit = new CommitData("abc123", "Replace legacy API", "diff"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		String prompt = new ScreeningPromptBuilder().buildScreeningPrompt(List.of(commit));

		assertTrue(prompt.contains("trafficLight")); //$NON-NLS-1$
		assertTrue(prompt.contains("confidence")); //$NON-NLS-1$
		assertFalse(prompt.contains("dslRule")); //$NON-NLS-1$
		assertFalse(prompt.contains("beforeExample")); //$NON-NLS-1$
		assertFalse(prompt.contains("afterExample")); //$NON-NLS-1$
		assertFalse(prompt.contains("negativeExample")); //$NON-NLS-1$
	}

	@Test
	void testDiffIsTruncatedForScreening() {
		String longDiff = "x".repeat(2_000); //$NON-NLS-1$
		CommitData commit = new CommitData("abc123", "Long diff", longDiff); //$NON-NLS-1$ //$NON-NLS-2$

		String prompt = new ScreeningPromptBuilder(1_000).buildScreeningPrompt(List.of(commit));

		assertTrue(prompt.contains("[diff truncated for screening]")); //$NON-NLS-1$
	}

	@Test
	void testCommitScreeningCandidateThreshold() {
		CommitScreening good = new CommitScreening("abc123", true, //$NON-NLS-1$
				TrafficLight.GREEN, "api-modernization", 0.9d, "Reusable API replacement"); //$NON-NLS-1$ //$NON-NLS-2$
		CommitScreening weak = new CommitScreening("def456", true, //$NON-NLS-1$
				TrafficLight.GREEN, "api-modernization", 0.4d, "Unclear pattern"); //$NON-NLS-1$ //$NON-NLS-2$
		CommitScreening yellow = new CommitScreening("ghi789", true, //$NON-NLS-1$
				TrafficLight.YELLOW, "api-modernization", 0.95d, "Needs DSL change"); //$NON-NLS-1$ //$NON-NLS-2$

		assertTrue(good.shouldRequestCandidateDetails());
		assertFalse(weak.shouldRequestCandidateDetails());
		assertFalse(yellow.shouldRequestCandidateDetails());
	}
}
