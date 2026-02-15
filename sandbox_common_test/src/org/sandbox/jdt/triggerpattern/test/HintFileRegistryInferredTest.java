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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.api.ImportDirective;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.RewriteAlternative;
import org.sandbox.jdt.triggerpattern.api.TransformationRule;
import org.sandbox.jdt.triggerpattern.internal.HintFileRegistry;
import org.sandbox.jdt.triggerpattern.mining.analysis.InferredRule;
import org.sandbox.jdt.triggerpattern.mining.analysis.RuleInferenceEngine;

/**
 * Tests for {@link HintFileRegistry} inferred rule registration (Phase 5.1).
 */
public class HintFileRegistryInferredTest {

	private HintFileRegistry registry;
	private RuleInferenceEngine engine;

	@BeforeEach
	void setUp() {
		registry = HintFileRegistry.getInstance();
		engine = new RuleInferenceEngine();
		// Clean up any previously registered inferred rules
		for (HintFile hf : registry.getInferredHintFiles()) {
			// Remove via promote (which removes the old key)
			registry.promoteToManual(hf.getId());
		}
	}

	@Test
	void testRegisterInferredRules() {
		HintFile hintFile = createTestHintFile();

		registry.registerInferredRules(hintFile, "abc123"); //$NON-NLS-1$

		assertEquals("inferred:abc123", hintFile.getId()); //$NON-NLS-1$

		List<HintFile> inferred = registry.getInferredHintFiles();
		assertFalse(inferred.isEmpty(), "Should have at least one inferred hint file"); //$NON-NLS-1$

		HintFile found = registry.getHintFile("inferred:abc123"); //$NON-NLS-1$
		assertNotNull(found, "Should find the registered hint file"); //$NON-NLS-1$
	}

	@Test
	void testGetInferredHintFilesEmpty() {
		// After cleanup in setUp, should be empty or minimal
		List<HintFile> inferred = registry.getInferredHintFiles();
		assertNotNull(inferred, "Should never return null"); //$NON-NLS-1$
	}

	@Test
	void testPromoteToManual() {
		HintFile hintFile = createTestHintFile();
		registry.registerInferredRules(hintFile, "def456"); //$NON-NLS-1$

		assertTrue(registry.getInferredHintFiles().stream()
				.anyMatch(hf -> "inferred:def456".equals(hf.getId())), //$NON-NLS-1$
				"Should find inferred rule before promotion"); //$NON-NLS-1$

		registry.promoteToManual("inferred:def456"); //$NON-NLS-1$

		assertFalse(registry.getInferredHintFiles().stream()
				.anyMatch(hf -> "inferred:def456".equals(hf.getId())), //$NON-NLS-1$
				"Should not find inferred rule after promotion"); //$NON-NLS-1$

		// Should now be findable as manual
		HintFile manual = registry.getHintFile("manual:def456"); //$NON-NLS-1$
		assertNotNull(manual, "Should find promoted hint file"); //$NON-NLS-1$
	}

	@Test
	void testRegisterSetsTagsWhenEmpty() {
		HintFile hintFile = createTestHintFile();
		assertTrue(hintFile.getTags().isEmpty(), "Tags should be empty initially"); //$NON-NLS-1$

		registry.registerInferredRules(hintFile, "ghi789"); //$NON-NLS-1$

		List<String> tags = hintFile.getTags();
		assertTrue(tags.contains("inferred"), "Tags should contain 'inferred'"); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(tags.contains("mining"), "Tags should contain 'mining'"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	void testRuleInferenceEngineToHintFile() {
		InferredRule rule = new InferredRule(
				"new String($b, \"UTF-8\")", //$NON-NLS-1$
				"new String($b, StandardCharsets.UTF_8)", //$NON-NLS-1$
				PatternKind.EXPRESSION,
				0.95,
				List.of("$b"), //$NON-NLS-1$
				new ImportDirective());

		HintFile hintFile = engine.toHintFile(List.of(rule));

		assertNotNull(hintFile);
		assertEquals(1, hintFile.getRules().size());
		assertEquals("inferred-rules", hintFile.getId()); //$NON-NLS-1$
	}

	private HintFile createTestHintFile() {
		HintFile hintFile = new HintFile();
		hintFile.setDescription("Test inferred rules"); //$NON-NLS-1$
		Pattern source = new Pattern("Collections.emptyList()", PatternKind.EXPRESSION); //$NON-NLS-1$
		RewriteAlternative alt = RewriteAlternative.otherwise("List.of()"); //$NON-NLS-1$
		TransformationRule rule = new TransformationRule(
				"Collections.emptyList() => List.of()", //$NON-NLS-1$
				source, null, List.of(alt), new ImportDirective());
		hintFile.addRule(rule);
		return hintFile;
	}
}
