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
package org.sandbox.mining.gemini;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.sandbox.mining.gemini.gemini.GeminiPromptBuilder;

/**
 * Tests for {@link GeminiPromptBuilder}.
 */
class GeminiPromptBuilderTest {

	@Test
	void testBuildPromptContainsAllSections() {
		GeminiPromptBuilder builder = new GeminiPromptBuilder();
		String prompt = builder.buildPrompt(
				"existing rule context",
				"[\"Collections\"]",
				"diff --git a/Test.java b/Test.java\n-old\n+new",
				"Refactor ArrayList to List.of");

		assertNotNull(prompt);
		assertTrue(prompt.contains("DSL-Erklärung"));
		assertTrue(prompt.contains("Bestehende DSL-Regeln"));
		assertTrue(prompt.contains("existing rule context"));
		assertTrue(prompt.contains("Bestehende Kategorien"));
		assertTrue(prompt.contains("Collections"));
		assertTrue(prompt.contains("Commit-Nachricht"));
		assertTrue(prompt.contains("Refactor ArrayList to List.of"));
		assertTrue(prompt.contains("Diff"));
		assertTrue(prompt.contains("-old"));
		assertTrue(prompt.contains("+new"));
	}

	@Test
	void testBuildPromptWithNullContext() {
		GeminiPromptBuilder builder = new GeminiPromptBuilder();
		String prompt = builder.buildPrompt(null, null, "diff content", "commit msg");

		assertNotNull(prompt);
		assertTrue(prompt.contains("(keine)"));
		assertTrue(prompt.contains("diff content"));
	}

	@Test
	void testBuildPromptContainsTrafficLightInstructions() {
		GeminiPromptBuilder builder = new GeminiPromptBuilder();
		String prompt = builder.buildPrompt("ctx", "[]", "diff", "msg");

		assertTrue(prompt.contains("GREEN"));
		assertTrue(prompt.contains("YELLOW"));
		assertTrue(prompt.contains("RED"));
		assertTrue(prompt.contains("NOT_APPLICABLE"));
	}

	@Test
	void testBuildPromptContainsJsonResponseFormat() {
		GeminiPromptBuilder builder = new GeminiPromptBuilder();
		String prompt = builder.buildPrompt("ctx", "[]", "diff", "msg");

		assertTrue(prompt.contains("\"relevant\""));
		assertTrue(prompt.contains("\"trafficLight\""));
		assertTrue(prompt.contains("\"dslRule\""));
		assertTrue(prompt.contains("\"category\""));
		assertTrue(prompt.contains("1-10")); // Scale should be 1-10
	}
}
