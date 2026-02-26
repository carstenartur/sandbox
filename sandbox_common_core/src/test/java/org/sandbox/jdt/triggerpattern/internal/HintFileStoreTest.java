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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation;

/**
 * Tests for {@link HintFileStore} — inferred rule registration,
 * persistence, and promotion.
 */
class HintFileStoreTest {

	private HintFileStore store;

	@BeforeEach
	void setUp() {
		store = new HintFileStore();
	}

	// ---- registerInferredRules (single HintFile) ----

	@Test
	void testRegisterInferredRulesSingle() {
		HintFile hf = new HintFile();
		store.registerInferredRules(hf, "abc123");

		assertEquals(1, store.getInferredHintFiles().size());
		assertNotNull(store.getHintFile("inferred:abc123"));
	}

	// ---- registerInferredRules (batch CommitEvaluation) ----

	@Test
	void testRegisterInferredRulesBatchWithValidEvaluations() {
		CommitEvaluation eval = createEvaluation("commit1",
				"<!id: test>\nnew Boolean(true)\n=> Boolean.TRUE\n;;\n");
		CommitEvaluation eval2 = createEvaluation("commit2",
				"<!id: test2>\nold()\n=> newer()\n;;\n");

		List<String> ids = store.registerInferredRules(List.of(eval, eval2), "test-repo");

		assertEquals(2, ids.size());
		assertTrue(ids.get(0).startsWith("inferred:"));
		assertTrue(ids.get(1).startsWith("inferred:"));
		assertEquals(2, store.getInferredHintFiles().size());
	}

	@Test
	void testRegisterInferredRulesBatchSkipsIrrelevant() {
		CommitEvaluation irrelevant = new CommitEvaluation(
				"commit1", "msg", "repo", Instant.now(),
				false, "not relevant", false, null,
				0, 0, 0, CommitEvaluation.TrafficLight.NOT_APPLICABLE,
				null, false, null, false, null, null, null, null, null, null);

		List<String> ids = store.registerInferredRules(List.of(irrelevant), "src");

		assertTrue(ids.isEmpty());
	}

	@Test
	void testRegisterInferredRulesBatchSkipsNullDslRule() {
		CommitEvaluation noRule = createEvaluation("commit1", null);

		List<String> ids = store.registerInferredRules(List.of(noRule), "src");

		assertTrue(ids.isEmpty());
	}

	@Test
	void testRegisterInferredRulesBatchSkipsBlankDslRule() {
		CommitEvaluation blankRule = createEvaluation("commit1", "   ");

		List<String> ids = store.registerInferredRules(List.of(blankRule), "src");

		assertTrue(ids.isEmpty());
	}

	@Test
	void testRegisterInferredRulesBatchSkipsInvalidDsl() {
		CommitEvaluation badDsl = createEvaluation("commit1", "not a valid dsl rule");

		List<String> ids = store.registerInferredRules(List.of(badDsl), "src");

		assertTrue(ids.isEmpty());
	}

	@Test
	void testRegisterInferredRulesBatchNullList() {
		List<String> ids = store.registerInferredRules((List<CommitEvaluation>) null, "src");

		assertTrue(ids.isEmpty());
	}

	// ---- getInferredHintFiles ----

	@Test
	void testGetInferredHintFilesExcludesManual() throws HintFileParser.HintParseException {
		store.loadFromString("manual:rule1",
				"<!id: manual:rule1>\nnew Boolean(true)\n=> Boolean.TRUE\n;;\n");

		HintFile inferred = new HintFile();
		store.registerInferredRules(inferred, "abc");

		List<HintFile> result = store.getInferredHintFiles();
		assertEquals(1, result.size());
		assertTrue(result.get(0).getId().startsWith("inferred:"));
	}

	// ---- promoteToManual ----

	@Test
	void testPromoteToManual() {
		HintFile hf = new HintFile();
		store.registerInferredRules(hf, "abc123");

		store.promoteToManual("inferred:abc123");

		assertNull(store.getHintFile("inferred:abc123"));
		assertNotNull(store.getHintFile("manual:abc123"));
		assertTrue(store.getInferredHintFiles().isEmpty());
	}

	@Test
	void testPromoteNonExistentIsNoOp() {
		store.promoteToManual("inferred:doesnotexist");
		// Should not throw
	}

	// ---- persistence ----

	@Test
	void testSaveAndLoadInferredHintFiles(@TempDir Path tempDir) throws IOException {
		CommitEvaluation eval = createEvaluation("commit1",
				"<!id: persisted>\nnew Boolean(true)\n=> Boolean.TRUE\n;;\n");
		store.registerInferredRules(List.of(eval), "test-repo");

		List<Path> written = store.saveInferredHintFiles(tempDir);

		assertFalse(written.isEmpty());
		for (Path p : written) {
			assertTrue(Files.exists(p));
			String content = Files.readString(p, StandardCharsets.UTF_8);
			assertFalse(content.isBlank());
		}

		// Load into a fresh store
		HintFileStore store2 = new HintFileStore();
		List<String> loaded = store2.loadInferredHintFiles(tempDir);

		assertFalse(loaded.isEmpty());
		assertFalse(store2.getInferredHintFiles().isEmpty());
	}

	@Test
	void testSaveCreatesHintsDirectory(@TempDir Path tempDir) throws IOException {
		HintFile hf = new HintFile();
		hf.addRule(new org.sandbox.jdt.triggerpattern.api.TransformationRule(
				null,
				new org.sandbox.jdt.triggerpattern.api.Pattern("old()", org.sandbox.jdt.triggerpattern.api.PatternKind.METHOD_CALL),
				null,
				List.of(org.sandbox.jdt.triggerpattern.api.RewriteAlternative.otherwise("newer()"))));
		store.registerInferredRules(hf, "xyz");

		List<Path> written = store.saveInferredHintFiles(tempDir);

		assertFalse(written.isEmpty());
		assertTrue(Files.isDirectory(tempDir.resolve(".hints")));
	}

	@Test
	void testLoadFromNonExistentDirectoryReturnsEmpty(@TempDir Path tempDir) {
		List<String> loaded = store.loadInferredHintFiles(tempDir.resolve("nonexistent"));
		assertTrue(loaded.isEmpty());
	}

	@Test
	void testSaveWithNoInferredFilesReturnsEmpty(@TempDir Path tempDir) throws IOException {
		List<Path> written = store.saveInferredHintFiles(tempDir);
		assertTrue(written.isEmpty());
	}

	// ---- helpers ----

	private static CommitEvaluation createEvaluation(String commitHash, String dslRule) {
		return new CommitEvaluation(
				commitHash, "Test commit", "https://example.com",
				Instant.now(), true, null, false, null,
				5, 5, 3, CommitEvaluation.TrafficLight.GREEN,
				"TestCategory", false, null,
				dslRule != null, dslRule, null,
				null, null, "Test summary", "VALID");
	}
}
