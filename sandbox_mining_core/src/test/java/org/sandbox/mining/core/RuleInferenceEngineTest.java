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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sandbox.mining.core.astdiff.CodeChangePair;
import org.sandbox.mining.core.inference.InferenceEngineConfig;
import org.sandbox.mining.core.inference.RuleInferenceEngine;
import org.sandbox.mining.core.inference.TransformationRule;
import org.sandbox.mining.core.llm.LlmProvider;

/**
 * Tests for {@link RuleInferenceEngine}.
 */
class RuleInferenceEngineTest {

	@Test
	void testInferRulesSimpleReplace() {
		RuleInferenceEngine engine = new RuleInferenceEngine(
				new InferenceEngineConfig().validateDsl(false));

		List<TransformationRule> rules = engine.inferRules(
				"new String(buf, \"UTF-8\")",
				"new String(buf, StandardCharsets.UTF_8)");

		assertNotNull(rules);
		assertFalse(rules.isEmpty());

		TransformationRule rule = rules.get(0);
		assertNotNull(rule.pattern());
		assertNotNull(rule.replacement());
		assertTrue(rule.confidence() > 0);
	}

	@Test
	void testInferRulesIdenticalCode() {
		RuleInferenceEngine engine = new RuleInferenceEngine();

		List<TransformationRule> rules = engine.inferRules(
				"int x = 1;", "int x = 1;");

		assertNotNull(rules);
		assertTrue(rules.isEmpty());
	}

	@Test
	void testInferRulesNullInput() {
		RuleInferenceEngine engine = new RuleInferenceEngine();

		List<TransformationRule> rules = engine.inferRules(null, null);
		assertNotNull(rules);
		assertTrue(rules.isEmpty());
	}

	@Test
	void testInferRulesMethodInvocationChange() {
		RuleInferenceEngine engine = new RuleInferenceEngine(
				new InferenceEngineConfig().validateDsl(false));

		List<TransformationRule> rules = engine.inferRules(
				"obj.getBytes(\"UTF-8\")",
				"obj.getBytes(StandardCharsets.UTF_8)");

		assertNotNull(rules);
		assertFalse(rules.isEmpty());
	}

	@Test
	void testInferRulesBatch() {
		RuleInferenceEngine engine = new RuleInferenceEngine(
				new InferenceEngineConfig().validateDsl(false));

		List<CodeChangePair> pairs = List.of(
				CodeChangePair.of("foo()", "bar()"),
				CodeChangePair.of("x.toString()", "String.valueOf(x)"));

		List<TransformationRule> rules = engine.inferRulesBatch(pairs);
		assertNotNull(rules);
		assertFalse(rules.isEmpty());
	}

	@Test
	void testInferRulesBatchEmpty() {
		RuleInferenceEngine engine = new RuleInferenceEngine();

		List<TransformationRule> rules = engine.inferRulesBatch(List.of());
		assertNotNull(rules);
		assertTrue(rules.isEmpty());
	}

	@Test
	void testInferRulesBatchNull() {
		RuleInferenceEngine engine = new RuleInferenceEngine();

		List<TransformationRule> rules = engine.inferRulesBatch(null);
		assertNotNull(rules);
		assertTrue(rules.isEmpty());
	}

	@Test
	void testMaxRulesPerPairRespected() {
		InferenceEngineConfig config = new InferenceEngineConfig()
				.maxRulesPerPair(1)
				.validateDsl(false);
		RuleInferenceEngine engine = new RuleInferenceEngine(config);

		List<TransformationRule> rules = engine.inferRules(
				"new String(buf, \"UTF-8\")",
				"new String(buf, StandardCharsets.UTF_8)");

		assertTrue(rules.size() <= 1);
	}

	@Test
	void testMinConfidenceFilter() {
		InferenceEngineConfig config = new InferenceEngineConfig()
				.minConfidence(0.99)
				.validateDsl(false);
		RuleInferenceEngine engine = new RuleInferenceEngine(config);

		List<TransformationRule> rules = engine.inferRules(
				"x = 1;", "x = 2;");

		// Very high confidence threshold should filter out most heuristic rules
		assertNotNull(rules);
	}

	@Test
	void testGenerateHintFile() {
		RuleInferenceEngine engine = new RuleInferenceEngine();

		List<TransformationRule> rules = List.of(
				TransformationRule.of("old()", "newMethod()", 0.9));

		String hintFile = engine.generateHintFile(rules, "test-rules", "Test rules");

		assertNotNull(hintFile);
		assertTrue(hintFile.contains("<!id: test-rules>"));
		assertTrue(hintFile.contains("<!description: Test rules>"));
		assertTrue(hintFile.contains("<!tags: [inferred, mining]>"));
		assertTrue(hintFile.contains("old()"));
		assertTrue(hintFile.contains("=> newMethod()"));
	}

	@Test
	void testGenerateHintFileWithDslRule() {
		RuleInferenceEngine engine = new RuleInferenceEngine();
		String customDsl = "custom()\n=> improved()\n;;\n";
		TransformationRule rule = TransformationRule.of("custom()", "improved()", 0.9)
				.withDslRule(customDsl);

		String hintFile = engine.generateHintFile(List.of(rule), "custom", "Custom rules");
		assertTrue(hintFile.contains(customDsl));
	}

	@Test
	void testConfigurableLlmProvider() {
		for (LlmProvider provider : LlmProvider.values()) {
			InferenceEngineConfig config = new InferenceEngineConfig()
					.llmProvider(provider);
			RuleInferenceEngine engine = new RuleInferenceEngine(config);

			assertEquals(provider, engine.getConfig().getLlmProvider());
		}
	}

	@Test
	void testTransformationRuleWithCategory() {
		TransformationRule rule = TransformationRule.of("a()", "b()", 0.8)
				.withCategory("encoding");
		assertEquals("encoding", rule.category());
	}

	@Test
	void testTransformationRuleIncrementOccurrences() {
		TransformationRule rule = TransformationRule.of("a()", "b()", 0.8);
		assertEquals(1, rule.occurrences());

		TransformationRule incremented = rule.withIncrementedOccurrences();
		assertEquals(2, incremented.occurrences());
	}

	@Test
	void testDefaultEngineConfig() {
		RuleInferenceEngine engine = new RuleInferenceEngine();
		assertNotNull(engine.getConfig());
		assertEquals(LlmProvider.GEMINI, engine.getConfig().getLlmProvider());
	}
}
