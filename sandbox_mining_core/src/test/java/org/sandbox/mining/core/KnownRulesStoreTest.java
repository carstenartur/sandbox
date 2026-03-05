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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation;
import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation.TrafficLight;
import org.sandbox.mining.core.config.KnownRulesStore;
import org.sandbox.mining.core.config.KnownRulesStore.KnownRule;
import org.sandbox.mining.core.config.KnownRulesStore.RuleStatus;

/**
 * Tests for {@link KnownRulesStore}.
 */
class KnownRulesStoreTest {

	@TempDir
	Path tempDir;

	@Test
	void testNewStoreIsEmpty() {
		KnownRulesStore store = new KnownRulesStore();
		assertEquals(0, store.size());
		assertTrue(store.getRules().isEmpty());
	}

	@Test
	void testSaveAndLoad() throws IOException {
		KnownRulesStore store = new KnownRulesStore();
		CommitEvaluation eval = createGreenValidEval("abc123", "Use isEmpty", "Collections");
		store.registerFromEvaluations(List.of(eval), 1);

		Path file = tempDir.resolve("known-rules.json");
		store.save(file);
		assertTrue(Files.exists(file));

		KnownRulesStore loaded = KnownRulesStore.load(file);
		assertEquals(1, loaded.size());
		KnownRule rule = loaded.getRules().get(0);
		assertEquals("abc123", rule.getSourceCommit());
		assertEquals("Collections", rule.getCategory());
		assertEquals(RuleStatus.DISCOVERED, rule.getStatus());
	}

	@Test
	void testLoadFromNonExistentFile() throws IOException {
		KnownRulesStore store = KnownRulesStore.load(tempDir.resolve("nonexistent.json"));
		assertEquals(0, store.size());
	}

	@Test
	void testLoadFromInvalidJson() throws IOException {
		Path file = tempDir.resolve("known-rules.json");
		Files.writeString(file, "not valid json", StandardCharsets.UTF_8);
		KnownRulesStore store = KnownRulesStore.load(file);
		assertEquals(0, store.size());
	}

	@Test
	void testRegisterGreenValidEvaluation() {
		KnownRulesStore store = new KnownRulesStore();
		CommitEvaluation eval = createGreenValidEval("abc123", "Use isEmpty", "Collections");

		int added = store.registerFromEvaluations(List.of(eval), 1);

		assertEquals(1, added);
		assertEquals(1, store.size());
		assertTrue(store.containsCommit("abc123"));
	}

	@Test
	void testSkipsDuplicateCommits() {
		KnownRulesStore store = new KnownRulesStore();
		CommitEvaluation eval = createGreenValidEval("abc123", "Use isEmpty", "Collections");

		store.registerFromEvaluations(List.of(eval), 1);
		int added = store.registerFromEvaluations(List.of(eval), 2);

		assertEquals(0, added);
		assertEquals(1, store.size());
	}

	@Test
	void testSkipsNonGreenEvaluations() {
		KnownRulesStore store = new KnownRulesStore();
		CommitEvaluation yellowEval = createEval("abc123", "Summary", "Cat", TrafficLight.YELLOW, "VALID");
		CommitEvaluation redEval = createEval("def456", "Summary", "Cat", TrafficLight.RED, "VALID");

		int added = store.registerFromEvaluations(List.of(yellowEval, redEval), 1);

		assertEquals(0, added);
		assertEquals(0, store.size());
	}

	@Test
	void testSkipsInvalidDslEvaluations() {
		KnownRulesStore store = new KnownRulesStore();
		CommitEvaluation invalidDsl = createEval("abc123", "Summary", "Cat", TrafficLight.GREEN, "PARSE_ERROR");

		int added = store.registerFromEvaluations(List.of(invalidDsl), 1);

		assertEquals(0, added);
	}

	@Test
	void testSkipsEvaluationsWithNullDslRule() {
		KnownRulesStore store = new KnownRulesStore();
		CommitEvaluation noDsl = new CommitEvaluation(
				"abc123", "message", "https://example.com",
				Instant.now(), null, true, null, false, null,
				5, 5, 3, TrafficLight.GREEN,
				"Cat", false, null,
				true, null, null,
				null, null, "Summary", "VALID");

		int added = store.registerFromEvaluations(List.of(noDsl), 1);

		assertEquals(0, added);
	}

	@Test
	void testFormatForPromptEmpty() {
		KnownRulesStore store = new KnownRulesStore();
		assertEquals("", store.formatForPrompt());
	}

	@Test
	void testFormatForPromptWithRules() {
		KnownRulesStore store = new KnownRulesStore();
		store.registerFromEvaluations(List.of(
				createGreenValidEval("abc123", "Use isEmpty for Collection", "Collections")), 1);

		String prompt = store.formatForPrompt();
		assertFalse(prompt.isEmpty());
		assertTrue(prompt.contains("Collections"));
		assertTrue(prompt.contains("Use isEmpty for Collection"));
	}

	@Test
	void testGetCommitHashIndex() {
		KnownRulesStore store = new KnownRulesStore();
		store.registerFromEvaluations(List.of(
				createGreenValidEval("abc123", "Rule 1", "Cat1"),
				createGreenValidEval("def456", "Rule 2", "Cat2")), 1);

		var index = store.getCommitHashIndex();
		assertEquals(2, index.size());
		assertTrue(index.containsKey("abc123"));
		assertTrue(index.containsKey("def456"));
	}

	@Test
	void testContainsCommitReturnsFalseForUnknown() {
		KnownRulesStore store = new KnownRulesStore();
		assertFalse(store.containsCommit("nonexistent"));
	}

	private CommitEvaluation createGreenValidEval(String hash, String summary, String category) {
		return createEval(hash, summary, category, TrafficLight.GREEN, "VALID");
	}

	private CommitEvaluation createEval(String hash, String summary, String category,
			TrafficLight light, String validationResult) {
		return new CommitEvaluation(
				hash, "commit message", "https://github.com/test/repo",
				Instant.now(), null, true, null, false, null,
				5, 5, 3, light,
				category, false, null,
				true, "new Boolean(true)\n=> Boolean.TRUE\n;;\n", "test.sandbox-hint",
				null, null, summary, validationResult);
	}
}
