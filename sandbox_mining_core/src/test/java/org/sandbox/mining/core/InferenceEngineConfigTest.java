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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.sandbox.mining.core.inference.InferenceEngineConfig;
import org.sandbox.mining.core.llm.LlmProvider;

/**
 * Tests for {@link InferenceEngineConfig}.
 */
class InferenceEngineConfigTest {

	@Test
	void testDefaults() {
		InferenceEngineConfig config = new InferenceEngineConfig();
		assertEquals(LlmProvider.GEMINI, config.getLlmProvider());
		assertEquals(0.7, config.getMinConfidence(), 0.001);
		assertEquals(false, config.isAiAnalysisEnabled());
		assertEquals(true, config.isValidateDsl());
		assertEquals(5, config.getMaxRulesPerPair());
	}

	@Test
	void testFluentConfiguration() {
		InferenceEngineConfig config = new InferenceEngineConfig()
				.llmProvider(LlmProvider.OPENAI)
				.minConfidence(0.9)
				.enableAiAnalysis(true)
				.validateDsl(false)
				.maxRulesPerPair(10)
				.targetHintFile("test.sandbox-hint");

		assertEquals(LlmProvider.OPENAI, config.getLlmProvider());
		assertEquals(0.9, config.getMinConfidence(), 0.001);
		assertTrue(config.isAiAnalysisEnabled());
		assertEquals(false, config.isValidateDsl());
		assertEquals(10, config.getMaxRulesPerPair());
		assertEquals("test.sandbox-hint", config.getTargetHintFile());
	}

	@Test
	void testAllLlmProviders() {
		for (LlmProvider provider : LlmProvider.values()) {
			InferenceEngineConfig config = new InferenceEngineConfig()
					.llmProvider(provider);
			assertEquals(provider, config.getLlmProvider());
		}
	}

	@Test
	void testMinConfidenceBoundaryLow() {
		InferenceEngineConfig config = new InferenceEngineConfig()
				.minConfidence(0.0);
		assertEquals(0.0, config.getMinConfidence(), 0.001);
	}

	@Test
	void testMinConfidenceBoundaryHigh() {
		InferenceEngineConfig config = new InferenceEngineConfig()
				.minConfidence(1.0);
		assertEquals(1.0, config.getMinConfidence(), 0.001);
	}

	@Test
	void testMinConfidenceOutOfRangeThrows() {
		assertThrows(IllegalArgumentException.class,
				() -> new InferenceEngineConfig().minConfidence(1.5));
		assertThrows(IllegalArgumentException.class,
				() -> new InferenceEngineConfig().minConfidence(-0.1));
	}

	@Test
	void testMaxRulesPerPairInvalidThrows() {
		assertThrows(IllegalArgumentException.class,
				() -> new InferenceEngineConfig().maxRulesPerPair(0));
	}

	@Test
	void testTargetHintFileNull() {
		InferenceEngineConfig config = new InferenceEngineConfig();
		assertNotNull(config);
		// Default is null
		assertEquals(null, config.getTargetHintFile());
	}
}
