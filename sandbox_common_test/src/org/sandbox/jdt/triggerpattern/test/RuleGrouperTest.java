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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.ImportDirective;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.mining.analysis.InferredRule;
import org.sandbox.jdt.triggerpattern.mining.analysis.RuleGroup;
import org.sandbox.jdt.triggerpattern.mining.analysis.RuleGrouper;

/**
 * Tests for {@link RuleGrouper}.
 */
public class RuleGrouperTest {

	private final RuleGrouper grouper = new RuleGrouper();

	@Test
	public void testSingleRuleGroupedAlone() {
		InferredRule rule = new InferredRule(
				"new String($bytes, \"UTF-8\")", //$NON-NLS-1$
				"new String($bytes, StandardCharsets.UTF_8)", //$NON-NLS-1$
				PatternKind.CONSTRUCTOR,
				0.9,
				List.of("$bytes"), //$NON-NLS-1$
				null);

		List<RuleGroup> groups = grouper.groupSimilar(List.of(rule));

		assertEquals(1, groups.size(), "Single rule should form one group"); //$NON-NLS-1$
		assertEquals(1, groups.get(0).occurrenceCount());
		assertEquals(rule, groups.get(0).generalizedRule());
	}

	@Test
	public void testThreeSimilarRulesGroupedTogether() {
		// Three rules with same pattern structure but different placeholder names
		InferredRule rule1 = new InferredRule(
				"new String($data, \"UTF-8\")", //$NON-NLS-1$
				"new String($data, StandardCharsets.UTF_8)", //$NON-NLS-1$
				PatternKind.CONSTRUCTOR,
				0.85,
				List.of("$data"), //$NON-NLS-1$
				null);
		InferredRule rule2 = new InferredRule(
				"new String($data, \"UTF-8\")", //$NON-NLS-1$
				"new String($data, StandardCharsets.UTF_8)", //$NON-NLS-1$
				PatternKind.CONSTRUCTOR,
				0.90,
				List.of("$data"), //$NON-NLS-1$
				null);
		InferredRule rule3 = new InferredRule(
				"new String($data, \"UTF-8\")", //$NON-NLS-1$
				"new String($data, StandardCharsets.UTF_8)", //$NON-NLS-1$
				PatternKind.CONSTRUCTOR,
				0.80,
				List.of("$data"), //$NON-NLS-1$
				null);

		List<RuleGroup> groups = grouper.groupSimilar(List.of(rule1, rule2, rule3));

		assertEquals(1, groups.size(), "Three similar rules should form one group"); //$NON-NLS-1$
		assertEquals(3, groups.get(0).occurrenceCount());
		assertEquals(3, groups.get(0).instances().size());
	}

	@Test
	public void testDifferentRulesGroupedSeparately() {
		InferredRule rule1 = new InferredRule(
				"new String($bytes, \"UTF-8\")", //$NON-NLS-1$
				"new String($bytes, StandardCharsets.UTF_8)", //$NON-NLS-1$
				PatternKind.CONSTRUCTOR,
				0.9,
				List.of("$bytes"), //$NON-NLS-1$
				null);
		InferredRule rule2 = new InferredRule(
				"Collections.emptyList()", //$NON-NLS-1$
				"List.of()", //$NON-NLS-1$
				PatternKind.METHOD_CALL,
				0.8,
				List.of(),
				null);

		List<RuleGroup> groups = grouper.groupSimilar(List.of(rule1, rule2));

		assertEquals(2, groups.size(), "Different rules should form separate groups"); //$NON-NLS-1$
	}

	@Test
	public void testAggregatedConfidenceBoostForMultipleOccurrences() {
		InferredRule rule1 = new InferredRule(
				"old()", "new_()", PatternKind.METHOD_CALL, //$NON-NLS-1$ //$NON-NLS-2$
				0.85, List.of(), null);
		InferredRule rule2 = new InferredRule(
				"old()", "new_()", PatternKind.METHOD_CALL, //$NON-NLS-1$ //$NON-NLS-2$
				0.90, List.of(), null);

		List<RuleGroup> groups = grouper.groupSimilar(List.of(rule1, rule2));

		assertEquals(1, groups.size());
		// Aggregated confidence should be >= max confidence
		assertTrue(groups.get(0).aggregatedConfidence() >= 0.90,
				"Aggregated confidence should be at least the max"); //$NON-NLS-1$
	}

	@Test
	public void testEmptyInputReturnsEmptyGroups() {
		List<RuleGroup> groups = grouper.groupSimilar(List.of());
		assertNotNull(groups);
		assertTrue(groups.isEmpty(), "Empty input should produce empty groups"); //$NON-NLS-1$
	}
}
