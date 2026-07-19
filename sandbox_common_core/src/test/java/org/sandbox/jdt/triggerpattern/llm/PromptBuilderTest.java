/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.llm.PromptBuilder.CommitData;

/** Tests the focused candidate-or-no-cleanup prompt contract. */
class PromptBuilderTest {

	@Test
	void singlePromptContainsCommitAndDslContext() {
		PromptBuilder builder = new PromptBuilder();
		String prompt = builder.buildPrompt(
				"ignored full rule context", //$NON-NLS-1$
				"[\"Collections\"]", //$NON-NLS-1$
				"diff --git a/Test.java b/Test.java\n-old\n+new", //$NON-NLS-1$
				"Refactor ArrayList to List.of"); //$NON-NLS-1$

		assertNotNull(prompt);
		assertTrue(prompt.contains("TriggerPattern DSL")); //$NON-NLS-1$
		assertTrue(prompt.contains("Commit to analyze")); //$NON-NLS-1$
		assertTrue(prompt.contains("Refactor ArrayList to List.of")); //$NON-NLS-1$
		assertTrue(prompt.contains("-old")); //$NON-NLS-1$
		assertTrue(prompt.contains("+new")); //$NON-NLS-1$
	}

	@Test
	void promptRequestsExactlyOneCandidateOrNoCleanup() {
		String prompt = new PromptBuilder().buildPrompt(null, null, "diff", "message"); //$NON-NLS-1$ //$NON-NLS-2$

		assertTrue(prompt.contains("exactly one reusable")); //$NON-NLS-1$
		assertTrue(prompt.contains("Return exactly one JSON object")); //$NON-NLS-1$
		assertTrue(prompt.contains("\"noCleanup\"")); //$NON-NLS-1$
		assertTrue(prompt.contains("\"reason\"")); //$NON-NLS-1$
		assertTrue(prompt.contains("\"confidence\"")); //$NON-NLS-1$
	}

	@Test
	void promptContainsOnlyCandidateFieldsConsumedByPipeline() {
		String prompt = new PromptBuilder().buildPrompt("ctx", "[]", "diff", "msg"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

		assertTrue(prompt.contains("\"relevant\"")); //$NON-NLS-1$
		assertTrue(prompt.contains("\"trafficLight\"")); //$NON-NLS-1$
		assertTrue(prompt.contains("\"dslRule\"")); //$NON-NLS-1$
		assertTrue(prompt.contains("\"sourceVersion\"")); //$NON-NLS-1$
		assertTrue(prompt.contains("\"beforeExample\"")); //$NON-NLS-1$
		assertTrue(prompt.contains("\"afterExample\"")); //$NON-NLS-1$
		assertTrue(prompt.contains("\"negativeExample\"")); //$NON-NLS-1$
		assertFalse(prompt.contains("\"implementationEffort\"")); //$NON-NLS-1$
		assertFalse(prompt.contains("\"replacesPlugin\"")); //$NON-NLS-1$
		assertFalse(prompt.contains("\"dslRuleAfterChange\"")); //$NON-NLS-1$
	}

	@Test
	void promptDoesNotSendBroadLegacyInventories() {
		String prompt = new PromptBuilder().buildPrompt(
				"FULL EXISTING RULE INVENTORY", //$NON-NLS-1$
				"[\"Legacy category\"]", //$NON-NLS-1$
				"diff", "msg", //$NON-NLS-1$ //$NON-NLS-2$
				"FULL PREVIOUS RESULT INVENTORY"); //$NON-NLS-1$

		assertFalse(prompt.contains("FULL EXISTING RULE INVENTORY")); //$NON-NLS-1$
		assertFalse(prompt.contains("Legacy category")); //$NON-NLS-1$
		assertFalse(prompt.contains("FULL PREVIOUS RESULT INVENTORY")); //$NON-NLS-1$
		assertFalse(prompt.contains("Existing Java-Based Cleanup Plugins")); //$NON-NLS-1$
	}

	@Test
	void promptContainsMandatorySafetyRejections() {
		String prompt = new PromptBuilder().buildPrompt("ctx", "[]", "diff", "msg"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

		assertTrue(prompt.contains("import-only")); //$NON-NLS-1$
		assertTrue(prompt.contains("type-changing")); //$NON-NLS-1$
		assertTrue(prompt.contains("architecture refactorings")); //$NON-NLS-1$
		assertTrue(prompt.contains("unavailable data-flow")); //$NON-NLS-1$
		assertTrue(prompt.contains("complete compiling before, after, and negative examples")); //$NON-NLS-1$
	}

	@Test
	void batchPromptContainsAllCommitsInOriginalOrder() {
		PromptBuilder builder = new PromptBuilder();
		List<CommitData> commits = List.of(
				new CommitData("abc123", "First commit", "diff1"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				new CommitData("def456", "Second commit", "diff2")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		String prompt = builder.buildBatchPrompt("rule ctx", "[]", commits); //$NON-NLS-1$ //$NON-NLS-2$

		assertTrue(prompt.contains("Commit 0 (abc123)")); //$NON-NLS-1$
		assertTrue(prompt.contains("Commit 1 (def456)")); //$NON-NLS-1$
		assertTrue(prompt.indexOf("abc123") < prompt.indexOf("def456")); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(prompt.contains("First commit")); //$NON-NLS-1$
		assertTrue(prompt.contains("Second commit")); //$NON-NLS-1$
		assertTrue(prompt.contains("diff1")); //$NON-NLS-1$
		assertTrue(prompt.contains("diff2")); //$NON-NLS-1$
	}

	@Test
	void batchPromptRequiresExactResultCount() {
		List<CommitData> commits = List.of(
				new CommitData("aaa", "msg1", "d1"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				new CommitData("bbb", "msg2", "d2"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				new CommitData("ccc", "msg3", "d3")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		String prompt = new PromptBuilder().buildBatchPrompt("ctx", "[]", commits); //$NON-NLS-1$ //$NON-NLS-2$

		assertTrue(prompt.contains("exactly 3 objects")); //$NON-NLS-1$
		assertTrue(prompt.contains("same order")); //$NON-NLS-1$
	}

	@Test
	void optionalDeterministicContextAndErrorFeedbackAreIncluded() {
		PromptBuilder builder = new PromptBuilder();
		builder.setTypeContext("Resolved receiver: java.lang.String"); //$NON-NLS-1$
		builder.setErrorFeedback("Do not emit unbound placeholders"); //$NON-NLS-1$

		String prompt = builder.buildPrompt(null, null, "diff", "msg"); //$NON-NLS-1$ //$NON-NLS-2$

		assertTrue(prompt.contains("Deterministic type context")); //$NON-NLS-1$
		assertTrue(prompt.contains("Resolved receiver: java.lang.String")); //$NON-NLS-1$
		assertTrue(prompt.contains("Recurring response errors")); //$NON-NLS-1$
		assertTrue(prompt.contains("Do not emit unbound placeholders")); //$NON-NLS-1$
	}
}
