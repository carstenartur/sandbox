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

/** Tests read-compatible handling of legacy known-rules data. */
@SuppressWarnings("removal")
class KnownRulesStoreTest {

	@TempDir
	Path tempDir;

	@Test
	void newLegacyArchiveIsEmpty() {
		KnownRulesStore store = new KnownRulesStore();
		assertEquals(0, store.size());
		assertTrue(store.getRules().isEmpty());
		assertEquals("", store.formatForPrompt()); //$NON-NLS-1$
	}

	@Test
	void loadsAndPreservesHistoricData() throws IOException {
		Path file = tempDir.resolve("known-rules.json"); //$NON-NLS-1$
		Files.writeString(file, """
				{
				  "version": 1,
				  "rules": [
				    {
				      "id": "legacy-rule",
				      "category": "Collections",
				      "dslRule": "$x.size() == 0\\n=> $x.isEmpty()\\n;;",
				      "summary": "Legacy discovery",
				      "sourceCommit": "abc123",
				      "status": "DISCOVERED",
				      "hintFile": "collections.sandbox-hint"
				    }
				  ]
				}
				""", StandardCharsets.UTF_8);

		KnownRulesStore loaded = KnownRulesStore.load(file);

		assertEquals(1, loaded.size());
		KnownRule rule = loaded.getRules().get(0);
		assertEquals("abc123", rule.getSourceCommit()); //$NON-NLS-1$
		assertEquals("Collections", rule.getCategory()); //$NON-NLS-1$
		assertEquals(RuleStatus.DISCOVERED, rule.getStatus());
		assertTrue(loaded.containsCommit("abc123")); //$NON-NLS-1$
		assertEquals("legacy-rule", loaded.getCommitHashIndex().get("abc123")); //$NON-NLS-1$ //$NON-NLS-2$

		Path copy = tempDir.resolve("copy.json"); //$NON-NLS-1$
		loaded.save(copy);
		assertTrue(Files.readString(copy, StandardCharsets.UTF_8).contains("legacy-rule")); //$NON-NLS-1$
	}

	@Test
	void newEvaluationsAreNotRegisteredInLegacyArchive() {
		KnownRulesStore store = new KnownRulesStore();
		CommitEvaluation evaluation = greenEvaluation();

		int added = store.registerFromEvaluations(List.of(evaluation), 12);

		assertEquals(0, added);
		assertEquals(0, store.size());
		assertFalse(store.containsCommit("new-commit")); //$NON-NLS-1$
		assertEquals("", store.formatForPrompt()); //$NON-NLS-1$
	}

	@Test
	void missingAndInvalidArchivesLoadAsEmpty() throws IOException {
		assertTrue(KnownRulesStore.load(tempDir.resolve("missing.json")).getRules().isEmpty()); //$NON-NLS-1$
		Path invalid = tempDir.resolve("invalid.json"); //$NON-NLS-1$
		Files.writeString(invalid, "not json", StandardCharsets.UTF_8); //$NON-NLS-1$
		assertTrue(KnownRulesStore.load(invalid).getRules().isEmpty());
	}

	private static CommitEvaluation greenEvaluation() {
		return new CommitEvaluation(
				"new-commit", "message", "https://github.com/example/repo", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				Instant.now(), null, true, null, false, null,
				0, 0, 0, TrafficLight.GREEN,
				"Collections", false, null, //$NON-NLS-1$
				true, "$x.size() == 0\n=> $x.isEmpty()\n;;", //$NON-NLS-1$
				"collections.sandbox-hint", //$NON-NLS-1$
				null, null, "Use isEmpty", "VALID", //$NON-NLS-1$ //$NON-NLS-2$
				"class T {}", "class T {}", "class N {}"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}
}
