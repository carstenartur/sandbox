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
package org.sandbox.jdt.triggerpattern.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link RuleUsageTracker}.
 */
class RuleUsageTrackerTest {

	private RuleUsageTracker tracker;

	@BeforeEach
	void setUp() {
		tracker = new RuleUsageTracker();
	}

	@Test
	void testRecordAndGetMatchCount() {
		tracker.recordMatch("rule1");
		tracker.recordMatch("rule1");
		tracker.recordMatch("rule2");

		assertEquals(2, tracker.getMatchCount("rule1"));
		assertEquals(1, tracker.getMatchCount("rule2"));
		assertEquals(0, tracker.getMatchCount("rule3"));
	}

	@Test
	void testRecordMultipleMatches() {
		tracker.recordMatches("rule1", 10);

		assertEquals(10, tracker.getMatchCount("rule1"));
	}

	@Test
	void testRecordMatchesZeroCountIgnored() {
		tracker.recordMatches("rule1", 0);

		assertEquals(0, tracker.getMatchCount("rule1"));
	}

	@Test
	void testRecordMatchesNegativeCountIgnored() {
		tracker.recordMatches("rule1", -5);

		assertEquals(0, tracker.getMatchCount("rule1"));
	}

	@Test
	void testGetAllMatchCounts() {
		tracker.recordMatch("rule1");
		tracker.recordMatches("rule2", 3);

		Map<String, Integer> counts = tracker.getAllMatchCounts();

		assertEquals(1, counts.get("rule1"));
		assertEquals(3, counts.get("rule2"));
	}

	@Test
	void testGetRulesWithNoMatches() {
		tracker.trackRule("rule1");
		tracker.recordMatch("rule2");
		tracker.trackRule("rule3");

		List<String> noMatches = tracker.getRulesWithNoMatches(
				List.of("rule1", "rule2", "rule3"));

		assertEquals(2, noMatches.size());
		assertTrue(noMatches.contains("rule1"));
		assertTrue(noMatches.contains("rule3"));
	}

	@Test
	void testGetRulesForPromotion() {
		tracker.setPromotionThreshold(3);
		tracker.recordMatches("rule1", 5);
		tracker.recordMatches("rule2", 1);
		tracker.recordMatches("rule3", 3);

		List<String> candidates = tracker.getRulesForPromotion(
				List.of("rule1", "rule2", "rule3"));

		assertEquals(2, candidates.size());
		assertTrue(candidates.contains("rule1"));
		assertTrue(candidates.contains("rule3"));
	}

	@Test
	void testDefaultPromotionThreshold() {
		assertEquals(5, tracker.getPromotionThreshold());
	}

	@Test
	void testSetPromotionThresholdRejectsZero() {
		tracker.setPromotionThreshold(0);
		assertEquals(5, tracker.getPromotionThreshold()); // unchanged
	}

	@Test
	void testGetSuggestionRemove() {
		tracker.trackRule("rule1");
		assertEquals(RuleUsageTracker.Suggestion.REMOVE, tracker.getSuggestion("rule1"));
	}

	@Test
	void testGetSuggestionKeep() {
		tracker.recordMatches("rule1", 2);
		assertEquals(RuleUsageTracker.Suggestion.KEEP, tracker.getSuggestion("rule1"));
	}

	@Test
	void testGetSuggestionPromote() {
		tracker.recordMatches("rule1", 10);
		assertEquals(RuleUsageTracker.Suggestion.PROMOTE, tracker.getSuggestion("rule1"));
	}

	@Test
	void testReset() {
		tracker.recordMatch("rule1");
		tracker.reset();

		assertEquals(0, tracker.getMatchCount("rule1"));
		assertTrue(tracker.getAllMatchCounts().isEmpty());
	}

	@Test
	void testTrackRuleDoesNotOverwriteExisting() {
		tracker.recordMatches("rule1", 3);
		tracker.trackRule("rule1");

		assertEquals(3, tracker.getMatchCount("rule1"));
	}

	@Test
	void testThreadSafety() throws InterruptedException {
		// Simple concurrency test
		Runnable task = () -> {
			for (int i = 0; i < 100; i++) {
				tracker.recordMatch("concurrent-rule");
			}
		};

		Thread t1 = new Thread(task);
		Thread t2 = new Thread(task);
		t1.start();
		t2.start();
		t1.join();
		t2.join();

		assertEquals(200, tracker.getMatchCount("concurrent-rule"));
	}
}
