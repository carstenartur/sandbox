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
package org.sandbox.mining.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sandbox.mining.core.llm.PromptBuilder;
import org.sandbox.mining.core.llm.PromptBuilder.CommitData;

/**
 * Tests for {@link PromptBuilder}.
 */
class PromptBuilderTest {

	@Test
	void testBuildPromptContainsAllSections() {
		PromptBuilder builder = new PromptBuilder();
		String prompt = builder.buildPrompt(
				"existing rule context",
				"[\"Collections\"]",
				"diff --git a/Test.java b/Test.java\n-old\n+new",
				"Refactor ArrayList to List.of");

		assertNotNull(prompt);
		assertTrue(prompt.contains("DSL Explanation"));
		assertTrue(prompt.contains("Existing DSL Rules"));
		assertTrue(prompt.contains("existing rule context"));
		assertTrue(prompt.contains("Existing Categories"));
		assertTrue(prompt.contains("Collections"));
		assertTrue(prompt.contains("Commit Message"));
		assertTrue(prompt.contains("Refactor ArrayList to List.of"));
		assertTrue(prompt.contains("Diff"));
		assertTrue(prompt.contains("-old"));
		assertTrue(prompt.contains("+new"));
	}

	@Test
	void testBuildPromptWithNullContext() {
		PromptBuilder builder = new PromptBuilder();
		String prompt = builder.buildPrompt(null, null, "diff content", "commit msg");

		assertNotNull(prompt);
		assertTrue(prompt.contains("(none)"));
		assertTrue(prompt.contains("diff content"));
	}

	@Test
	void testBuildPromptContainsTrafficLightInstructions() {
		PromptBuilder builder = new PromptBuilder();
		String prompt = builder.buildPrompt("ctx", "[]", "diff", "msg");

		assertTrue(prompt.contains("GREEN"));
		assertTrue(prompt.contains("YELLOW"));
		assertTrue(prompt.contains("RED"));
		assertTrue(prompt.contains("NOT_APPLICABLE"));
	}

	@Test
	void testBuildPromptContainsJsonResponseFormat() {
		PromptBuilder builder = new PromptBuilder();
		String prompt = builder.buildPrompt("ctx", "[]", "diff", "msg");

		assertTrue(prompt.contains("\"relevant\""));
		assertTrue(prompt.contains("\"trafficLight\""));
		assertTrue(prompt.contains("\"dslRule\""));
		assertTrue(prompt.contains("\"category\""));
		assertTrue(prompt.contains("1-10")); // Scale should be 1-10
	}

	@Test
	void testBuildBatchPromptContainsAllCommits() {
		PromptBuilder builder = new PromptBuilder();
		List<CommitData> commits = List.of(
				new CommitData("abc123", "First commit", "diff1"),
				new CommitData("def456", "Second commit", "diff2"));
		String prompt = builder.buildBatchPrompt("rule ctx", "[\"Collections\"]", commits);

		assertNotNull(prompt);
		assertTrue(prompt.contains("abc123"));
		assertTrue(prompt.contains("def456"));
		assertTrue(prompt.contains("First commit"));
		assertTrue(prompt.contains("Second commit"));
		assertTrue(prompt.contains("diff1"));
		assertTrue(prompt.contains("diff2"));
	}

	@Test
	void testBuildBatchPromptRequestsArrayOfExactlyN() {
		PromptBuilder builder = new PromptBuilder();
		List<CommitData> commits = List.of(
				new CommitData("aaa", "msg1", "d1"),
				new CommitData("bbb", "msg2", "d2"),
				new CommitData("ccc", "msg3", "d3"));
		String prompt = builder.buildBatchPrompt("ctx", "[]", commits);

		assertTrue(prompt.contains("exactly 3"));
	}

	@Test
	void testBuildBatchPromptLabelsCommitsByIndex() {
		PromptBuilder builder = new PromptBuilder();
		List<CommitData> commits = List.of(
				new CommitData("hash0", "msg0", "d0"),
				new CommitData("hash1", "msg1", "d1"));
		String prompt = builder.buildBatchPrompt("ctx", "[]", commits);

		assertTrue(prompt.contains("Commit 0"));
		assertTrue(prompt.contains("Commit 1"));
		assertTrue(prompt.contains("hash0"));
		assertTrue(prompt.contains("hash1"));
	}

	@Test
	void testBuildBatchPromptContainsJsonArraySchema() {
		PromptBuilder builder = new PromptBuilder();
		List<CommitData> commits = List.of(new CommitData("x", "y", "z"));
		String prompt = builder.buildBatchPrompt("ctx", "[]", commits);

		assertTrue(prompt.contains("\"relevant\""));
		assertTrue(prompt.contains("\"trafficLight\""));
		assertTrue(prompt.contains("\"dslRule\""));
	}
}
