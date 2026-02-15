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
package org.sandbox.jdt.triggerpattern.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.TransformationRule;
import org.sandbox.jdt.triggerpattern.mining.analysis.InferredRule;
import org.sandbox.jdt.triggerpattern.mining.analysis.RuleInferenceEngine;

/**
 * End-to-end tests for {@link RuleInferenceEngine}.
 */
public class RuleInferenceEngineTest {

	private final RuleInferenceEngine engine = new RuleInferenceEngine();

	@Test
	public void testInferConstructorRewrite() {
		String before = "new String(bytes, \"UTF-8\")"; //$NON-NLS-1$
		String after = "new String(bytes, StandardCharsets.UTF_8)"; //$NON-NLS-1$

		Optional<InferredRule> result = engine.inferRule(before, after, PatternKind.CONSTRUCTOR);

		assertTrue(result.isPresent(), "Should infer a rule for constructor rewrite"); //$NON-NLS-1$
		InferredRule rule = result.get();
		assertNotNull(rule.sourcePattern());
		assertNotNull(rule.replacementPattern());
		assertTrue(rule.confidence() > 0, "Confidence should be positive"); //$NON-NLS-1$
	}

	@Test
	public void testInferMethodCallRewrite() {
		String before = "Collections.emptyList()"; //$NON-NLS-1$
		String after = "List.of()"; //$NON-NLS-1$

		Optional<InferredRule> result = engine.inferRule(before, after, PatternKind.METHOD_CALL);

		assertTrue(result.isPresent(), "Should infer a rule for method call rewrite"); //$NON-NLS-1$
		InferredRule rule = result.get();
		assertNotNull(rule.sourcePattern());
		assertNotNull(rule.replacementPattern());
	}

	@Test
	public void testToTransformationRule() {
		String before = "new String(bytes, \"UTF-8\")"; //$NON-NLS-1$
		String after = "new String(bytes, StandardCharsets.UTF_8)"; //$NON-NLS-1$

		Optional<InferredRule> result = engine.inferRule(before, after, PatternKind.CONSTRUCTOR);
		assertTrue(result.isPresent());

		TransformationRule trule = engine.toTransformationRule(result.get());
		assertNotNull(trule);
		assertNotNull(trule.sourcePattern());
		assertFalse(trule.alternatives().isEmpty(), "Should have at least one alternative"); //$NON-NLS-1$
	}

	@Test
	public void testToHintFileString() {
		String before = "Collections.emptyList()"; //$NON-NLS-1$
		String after = "List.of()"; //$NON-NLS-1$

		Optional<InferredRule> result = engine.inferRule(before, after, PatternKind.METHOD_CALL);
		assertTrue(result.isPresent());

		String hintContent = engine.toHintFileString(List.of(result.get()));
		assertNotNull(hintContent);
		assertTrue(hintContent.contains("<!id: inferred-rules>"), //$NON-NLS-1$
				"Hint file should contain id header"); //$NON-NLS-1$
		assertTrue(hintContent.contains("=>"), //$NON-NLS-1$
				"Hint file should contain => arrow"); //$NON-NLS-1$
		assertTrue(hintContent.contains(";;"), //$NON-NLS-1$
				"Hint file should contain ;; terminator"); //$NON-NLS-1$
	}

	@Test
	public void testToHintFile() {
		String before = "Collections.emptyList()"; //$NON-NLS-1$
		String after = "List.of()"; //$NON-NLS-1$

		Optional<InferredRule> result = engine.inferRule(before, after, PatternKind.METHOD_CALL);
		assertTrue(result.isPresent());

		var hintFile = engine.toHintFile(List.of(result.get()));
		assertNotNull(hintFile);
		assertEquals("inferred-rules", hintFile.getId()); //$NON-NLS-1$
		assertFalse(hintFile.getRules().isEmpty(), "Hint file should contain rules"); //$NON-NLS-1$
	}

	@Test
	public void testInferFromCodeChangePair() {
		var pair = new org.sandbox.jdt.triggerpattern.mining.analysis.CodeChangePair(
				"Test.java", 42, //$NON-NLS-1$
				"Collections.emptyList()", //$NON-NLS-1$
				"List.of()", //$NON-NLS-1$
				null, null,
				PatternKind.METHOD_CALL);

		Optional<InferredRule> result = engine.inferRule(pair);
		assertTrue(result.isPresent(), "Should infer rule from CodeChangePair"); //$NON-NLS-1$
	}
}
