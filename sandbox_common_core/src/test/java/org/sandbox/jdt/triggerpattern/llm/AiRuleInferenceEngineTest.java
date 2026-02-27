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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AiRuleInferenceEngine}.
 *
 * <p>Uses a mock {@link LlmClient} to verify prompt construction,
 * DSL validation, and error handling without requiring a real API key.</p>
 */
class AiRuleInferenceEngineTest {

	@Test
	void testInferRuleWithRelevantResult() throws IOException {
		CommitEvaluation mockEval = createMockEvaluation(true,
				"<!id: test>\nnew Boolean(true)\n=> Boolean.TRUE\n;;\n", //$NON-NLS-1$
				CommitEvaluation.TrafficLight.GREEN);
		LlmClient mockClient = new MockLlmClient(mockEval);
		AiRuleInferenceEngine engine = new AiRuleInferenceEngine(mockClient);

		Optional<CommitEvaluation> result = engine.inferRule(
				"new Boolean(true)", //$NON-NLS-1$
				"Boolean.TRUE"); //$NON-NLS-1$

		assertTrue(result.isPresent());
		CommitEvaluation eval = result.get();
		assertTrue(eval.relevant());
		assertNotNull(eval.dslRule());
		assertNotNull(eval.dslValidationResult());
	}

	@Test
	void testInferRuleWithIrrelevantResult() throws IOException {
		CommitEvaluation mockEval = createMockEvaluation(false, null,
				CommitEvaluation.TrafficLight.NOT_APPLICABLE);
		LlmClient mockClient = new MockLlmClient(mockEval);
		AiRuleInferenceEngine engine = new AiRuleInferenceEngine(mockClient);

		Optional<CommitEvaluation> result = engine.inferRule("int x = 1;", "int x = 2;"); //$NON-NLS-1$ //$NON-NLS-2$

		assertTrue(result.isPresent());
		assertFalse(result.get().relevant());
	}

	@Test
	void testInferRuleWithNullInputReturnsEmpty() {
		LlmClient mockClient = new MockLlmClient(null);
		AiRuleInferenceEngine engine = new AiRuleInferenceEngine(mockClient);

		assertTrue(engine.inferRule(null, "code").isEmpty()); //$NON-NLS-1$
		assertTrue(engine.inferRule("code", null).isEmpty()); //$NON-NLS-1$
	}

	@Test
	void testInferRuleFromDiffWithBlankDiffReturnsEmpty() {
		LlmClient mockClient = new MockLlmClient(null);
		AiRuleInferenceEngine engine = new AiRuleInferenceEngine(mockClient);

		assertTrue(engine.inferRuleFromDiff(null).isEmpty());
		assertTrue(engine.inferRuleFromDiff("").isEmpty()); //$NON-NLS-1$
		assertTrue(engine.inferRuleFromDiff("   ").isEmpty()); //$NON-NLS-1$
	}

	@Test
	void testInferRuleWhenLlmReturnsNull() throws IOException {
		LlmClient mockClient = new MockLlmClient(null);
		AiRuleInferenceEngine engine = new AiRuleInferenceEngine(mockClient);

		Optional<CommitEvaluation> result = engine.inferRule("a", "b"); //$NON-NLS-1$ //$NON-NLS-2$

		assertTrue(result.isEmpty());
	}

	@Test
	void testInferRuleWhenQuotaExhausted() {
		MockLlmClient mockClient = new MockLlmClient(null);
		mockClient.setHasRemainingQuota(false);
		AiRuleInferenceEngine engine = new AiRuleInferenceEngine(mockClient);

		Optional<CommitEvaluation> result = engine.inferRule("a", "b"); //$NON-NLS-1$ //$NON-NLS-2$

		assertTrue(result.isEmpty());
	}

	@Test
	void testInferRuleWhenApiUnavailable() {
		MockLlmClient mockClient = new MockLlmClient(null);
		mockClient.setApiUnavailable(true);
		AiRuleInferenceEngine engine = new AiRuleInferenceEngine(mockClient);

		Optional<CommitEvaluation> result = engine.inferRule("a", "b"); //$NON-NLS-1$ //$NON-NLS-2$

		assertTrue(result.isEmpty());
	}

	@Test
	void testInferRulePassesPromptToLlm() throws IOException {
		CommitEvaluation mockEval = createMockEvaluation(true, null,
				CommitEvaluation.TrafficLight.YELLOW);
		MockLlmClient mockClient = new MockLlmClient(mockEval);
		AiRuleInferenceEngine engine = new AiRuleInferenceEngine(mockClient);

		engine.inferRule("old code", "new code"); //$NON-NLS-1$ //$NON-NLS-2$

		assertNotNull(mockClient.getLastPrompt());
		assertTrue(mockClient.getLastPrompt().contains("old code")); //$NON-NLS-1$
		assertTrue(mockClient.getLastPrompt().contains("new code")); //$NON-NLS-1$
	}

	@Test
	void testBuildSimpleDiffFormat() {
		String diff = AiRuleInferenceEngine.buildSimpleDiff(
				"old line", "new line"); //$NON-NLS-1$ //$NON-NLS-2$

		assertTrue(diff.contains("--- a/snippet.java")); //$NON-NLS-1$
		assertTrue(diff.contains("+++ b/snippet.java")); //$NON-NLS-1$
		assertTrue(diff.contains("-old line")); //$NON-NLS-1$
		assertTrue(diff.contains("+new line")); //$NON-NLS-1$
	}

	@Test
	void testInferRuleFromDiffWithValidDiff() throws IOException {
		CommitEvaluation mockEval = createMockEvaluation(true,
				"<!id: r1>\nold\n=> newer\n;;\n", //$NON-NLS-1$
				CommitEvaluation.TrafficLight.GREEN);
		LlmClient mockClient = new MockLlmClient(mockEval);
		AiRuleInferenceEngine engine = new AiRuleInferenceEngine(mockClient);

		String diff = "--- a/Test.java\n+++ b/Test.java\n@@ -1,1 +1,1 @@\n-old\n+new\n"; //$NON-NLS-1$
		Optional<CommitEvaluation> result = engine.inferRuleFromDiff(diff);

		assertTrue(result.isPresent());
		assertTrue(result.get().relevant());
	}

	@Test
	void testInferRuleValidatesDslRule() throws IOException {
		// An invalid DSL rule should still return the evaluation, with validation result set
		CommitEvaluation mockEval = createMockEvaluation(true,
				"not a valid rule", //$NON-NLS-1$
				CommitEvaluation.TrafficLight.GREEN);
		LlmClient mockClient = new MockLlmClient(mockEval);
		AiRuleInferenceEngine engine = new AiRuleInferenceEngine(mockClient);

		Optional<CommitEvaluation> result = engine.inferRule("a", "b"); //$NON-NLS-1$ //$NON-NLS-2$

		assertTrue(result.isPresent());
		CommitEvaluation eval = result.get();
		assertNotNull(eval.dslValidationResult());
		// "not a valid rule" won't parse correctly
		assertNotEquals("VALID", eval.dslValidationResult()); //$NON-NLS-1$
	}

	@Test
	void testInferRuleWithIOExceptionReturnsEmpty() throws IOException {
		LlmClient failingClient = new MockLlmClient(null) {
			@Override
			public CommitEvaluation evaluate(String prompt, String commitHash,
					String commitMessage, String repoUrl) throws IOException {
				throw new IOException("Network error"); //$NON-NLS-1$
			}
		};
		AiRuleInferenceEngine engine = new AiRuleInferenceEngine(failingClient);

		Optional<CommitEvaluation> result = engine.inferRule("a", "b"); //$NON-NLS-1$ //$NON-NLS-2$

		assertTrue(result.isEmpty());
	}

	// ---- helpers ----

	private static CommitEvaluation createMockEvaluation(boolean relevant,
			String dslRule, CommitEvaluation.TrafficLight trafficLight) {
		return new CommitEvaluation(
				"inline", "AI rule inference", "local", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				Instant.now(), null, relevant, null, false, null,
				5, 5, 3, trafficLight,
				"TestCategory", false, null, //$NON-NLS-1$
				dslRule != null, dslRule, null,
				null, null, "Test summary", null); //$NON-NLS-1$
	}

	/**
	 * A minimal mock LLM client for testing.
	 */
	private static class MockLlmClient implements LlmClient {

		private final CommitEvaluation response;
		private String lastPrompt;
		private boolean hasQuota = true;
		private boolean apiUnavailable = false;

		MockLlmClient(CommitEvaluation response) {
			this.response = response;
		}

		void setHasRemainingQuota(boolean hasQuota) {
			this.hasQuota = hasQuota;
		}

		void setApiUnavailable(boolean unavailable) {
			this.apiUnavailable = unavailable;
		}

		String getLastPrompt() {
			return lastPrompt;
		}

		@Override
		public CommitEvaluation evaluate(String prompt, String commitHash,
				String commitMessage, String repoUrl) throws IOException {
			this.lastPrompt = prompt;
			return response;
		}

		@Override
		public List<CommitEvaluation> evaluateBatch(String prompt,
				List<String> commitHashes, List<String> commitMessages,
				String repoUrl) throws IOException {
			this.lastPrompt = prompt;
			return response != null ? List.of(response) : List.of();
		}

		@Override
		public boolean hasRemainingQuota() {
			return hasQuota;
		}

		@Override
		public boolean isApiUnavailable() {
			return apiUnavailable;
		}

		@Override
		public int getDailyRequestCount() {
			return 0;
		}

		@Override
		public Duration getMaxFailureDuration() {
			return Duration.ofSeconds(300);
		}

		@Override
		public void setMaxFailureDuration(Duration duration) {
			// no-op
		}

		@Override
		public boolean wasLastResponseTruncated() {
			return false;
		}

		@Override
		public String getModel() {
			return "mock-model"; //$NON-NLS-1$
		}

		@Override
		public void close() {
			// no-op
		}
	}
}
