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
package org.sandbox.mining.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sandbox.mining.core.engine.LlmDslSequenceEngine;
import org.sandbox.mining.core.llm.CommitEvaluation;
import org.sandbox.mining.core.llm.CommitEvaluation.TrafficLight;
import org.sandbox.mining.core.llm.LlmClient;

/**
 * Tests for {@link LlmDslSequenceEngine}.
 */
class LlmDslSequenceEngineTest {

	@Test
	void testEngineType() {
		LlmClient mock = createMockClient();
		try (LlmDslSequenceEngine engine = new LlmDslSequenceEngine(mock)) {
			assertEquals("LLM", engine.getEngineType()); //$NON-NLS-1$
		}
	}

	@Test
	void testModelName() {
		LlmClient mock = createMockClient();
		try (LlmDslSequenceEngine engine = new LlmDslSequenceEngine(mock)) {
			assertEquals("test-model", engine.getModelName()); //$NON-NLS-1$
		}
	}

	@Test
	void testHasRemainingCapacity() {
		LlmClient mock = createMockClient();
		try (LlmDslSequenceEngine engine = new LlmDslSequenceEngine(mock)) {
			assertTrue(engine.hasRemainingCapacity());
		}
	}

	@Test
	void testIsUnavailable() {
		LlmClient mock = createMockClient();
		try (LlmDslSequenceEngine engine = new LlmDslSequenceEngine(mock)) {
			assertFalse(engine.isUnavailable());
		}
	}

	@Test
	void testGetRequestCount() {
		LlmClient mock = createMockClient();
		try (LlmDslSequenceEngine engine = new LlmDslSequenceEngine(mock)) {
			assertEquals(0, engine.getRequestCount());
		}
	}

	@Test
	void testWasLastResponseTruncated() {
		LlmClient mock = createMockClient();
		try (LlmDslSequenceEngine engine = new LlmDslSequenceEngine(mock)) {
			assertFalse(engine.wasLastResponseTruncated());
		}
	}

	@Test
	void testMaxFailureDuration() {
		LlmClient mock = createMockClient();
		try (LlmDslSequenceEngine engine = new LlmDslSequenceEngine(mock)) {
			Duration d = Duration.ofSeconds(600);
			engine.setMaxFailureDuration(d);
			assertEquals(d, engine.getMaxFailureDuration());
		}
	}

	@Test
	void testClientClassName() {
		LlmClient mock = createMockClient();
		try (LlmDslSequenceEngine engine = new LlmDslSequenceEngine(mock)) {
			assertNotNull(engine.getClientClassName());
		}
	}

	@Test
	void testNullClientThrows() {
		assertThrows(IllegalArgumentException.class, () -> new LlmDslSequenceEngine(null));
	}

	@Test
	void testEvaluateDelegates() throws IOException {
		LlmClient mock = createMockClient();
		try (LlmDslSequenceEngine engine = new LlmDslSequenceEngine(mock)) {
			CommitEvaluation result = engine.evaluate("prompt", "abc123", "msg", "url"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			assertNotNull(result);
			assertEquals("abc123", result.commitHash()); //$NON-NLS-1$
		}
	}

	@Test
	void testEvaluateBatchDelegates() throws IOException {
		LlmClient mock = createMockClient();
		try (LlmDslSequenceEngine engine = new LlmDslSequenceEngine(mock)) {
			List<CommitEvaluation> results = engine.evaluateBatch(
					"prompt", List.of("hash1"), List.of("msg1"), "url"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			assertNotNull(results);
			assertEquals(1, results.size());
		}
	}

	private static LlmClient createMockClient() {
		return new LlmClient() {
			private Duration maxFailureDuration = Duration.ofSeconds(300);

			@Override
			public CommitEvaluation evaluate(String prompt, String commitHash,
					String commitMessage, String repoUrl) {
				return new CommitEvaluation(commitHash, commitMessage, repoUrl,
						null, true, null, false, null,
						5, 5, 5, TrafficLight.GREEN, "test", false, null, true, //$NON-NLS-1$
						null, null, null, null, "test summary", null); //$NON-NLS-1$
			}

			@Override
			public List<CommitEvaluation> evaluateBatch(String prompt,
					List<String> commitHashes, List<String> commitMessages, String repoUrl) {
				return commitHashes.stream()
						.map(h -> evaluate(prompt, h, "msg", repoUrl)) //$NON-NLS-1$
						.toList();
			}

			@Override
			public boolean hasRemainingQuota() { return true; }

			@Override
			public boolean isApiUnavailable() { return false; }

			@Override
			public int getDailyRequestCount() { return 0; }

			@Override
			public Duration getMaxFailureDuration() { return maxFailureDuration; }

			@Override
			public void setMaxFailureDuration(Duration duration) {
				this.maxFailureDuration = duration;
			}

			@Override
			public boolean wasLastResponseTruncated() { return false; }

			@Override
			public String getModel() { return "test-model"; } //$NON-NLS-1$

			@Override
			public void close() { }
		};
	}
}
