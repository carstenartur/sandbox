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

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.sandbox.mining.core.llm.CommitEvaluation;
import org.sandbox.mining.core.llm.CommitEvaluation.TrafficLight;
import org.sandbox.mining.core.report.StatisticsCollector;

/**
 * Tests for {@link StatisticsCollector}.
 */
class StatisticsCollectorTest {

	@Test
	void testRecordRelevantGreen() {
		StatisticsCollector stats = new StatisticsCollector();

		CommitEvaluation eval = new CommitEvaluation(
				"abc123", "msg", "url", Instant.now(),
				true, null, false, null,
				4, 3, 2, TrafficLight.GREEN,
				"Collections", false, "reason",
				true, "rule", "file.sandbox-hint",
				null, null, "summary");

		stats.record(eval);

		assertEquals(1, stats.getTotalProcessed());
		assertEquals(1, stats.getRelevant());
		assertEquals(0, stats.getIrrelevant());
		assertEquals(0, stats.getDuplicates());
		assertEquals(1, stats.getGreen());
		assertEquals(0, stats.getYellow());
		assertEquals(0, stats.getRed());
	}

	@Test
	void testRecordIrrelevant() {
		StatisticsCollector stats = new StatisticsCollector();

		CommitEvaluation eval = new CommitEvaluation(
				"def456", "msg", "url", Instant.now(),
				false, "Not a code change", false, null,
				0, 0, 0, TrafficLight.NOT_APPLICABLE,
				null, false, null,
				false, null, null,
				null, null, "summary");

		stats.record(eval);

		assertEquals(1, stats.getTotalProcessed());
		assertEquals(0, stats.getRelevant());
		assertEquals(1, stats.getIrrelevant());
		assertEquals("Not a code change",
				stats.getIrrelevantReasons().keySet().iterator().next());
	}

	@Test
	void testRecordMultiple() {
		StatisticsCollector stats = new StatisticsCollector();

		stats.record(createEval(true, TrafficLight.GREEN, false));
		stats.record(createEval(true, TrafficLight.YELLOW, false));
		stats.record(createEval(true, TrafficLight.RED, true));
		stats.record(createEval(false, TrafficLight.NOT_APPLICABLE, false));

		assertEquals(4, stats.getTotalProcessed());
		assertEquals(3, stats.getRelevant());
		assertEquals(1, stats.getIrrelevant());
		assertEquals(1, stats.getDuplicates());
		assertEquals(1, stats.getGreen());
		assertEquals(1, stats.getYellow());
		assertEquals(1, stats.getRed());
	}

	private CommitEvaluation createEval(boolean relevant, TrafficLight light, boolean duplicate) {
		return new CommitEvaluation(
				"hash", "msg", "url", Instant.now(),
				relevant, relevant ? null : "reason",
				duplicate, null,
				0, 0, 0, light,
				null, false, null,
				false, null, null,
				null, null, "summary");
	}
}
