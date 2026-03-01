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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation;
import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation.TrafficLight;
import org.sandbox.mining.core.comparison.ErrorFeedbackCollector;

/**
 * Tests for {@link ErrorFeedbackCollector}.
 */
class ErrorFeedbackCollectorTest {

	@Test
	void testCollectValidationErrors() {
		ErrorFeedbackCollector collector = new ErrorFeedbackCollector();
		CommitEvaluation eval = createEvalWithValidation("hash1", "XML parse error: <trigger> found");
		collector.collect(List.of(eval));
		assertEquals(1, collector.getErrorCount());
	}

	@Test
	void testNoErrorsForValidEvals() {
		ErrorFeedbackCollector collector = new ErrorFeedbackCollector();
		CommitEvaluation eval = createEvalWithValidation("hash1", "VALID");
		collector.collect(List.of(eval));
		assertEquals(0, collector.getErrorCount());
	}

	@Test
	void testFormatFeedbackEmpty() {
		ErrorFeedbackCollector collector = new ErrorFeedbackCollector();
		assertEquals("", collector.formatFeedback());
	}

	@Test
	void testFormatFeedbackWithErrors() {
		ErrorFeedbackCollector collector = new ErrorFeedbackCollector();
		CommitEvaluation eval = createEvalWithValidation("hash1", "Used <trigger> XML tags");
		collector.collect(List.of(eval));
		String feedback = collector.formatFeedback();
		assertTrue(feedback.contains("Common Errors"));
		assertTrue(feedback.contains("XML"));
	}

	@Test
	void testErrorPatternCategorization() {
		assertEquals("Used XML/HTML tags instead of plain DSL",
				ErrorFeedbackCollector.categorizeError("Found <trigger> XML tag"));
		assertEquals("Used isType() instead of instanceof()",
				ErrorFeedbackCollector.categorizeError("isType() is not supported"));
		assertEquals("DSL syntax error",
				ErrorFeedbackCollector.categorizeError("Parse error at line 5"));
	}

	@Test
	void testCategorizeNullError() {
		assertEquals("Unknown", ErrorFeedbackCollector.categorizeError(null));
	}

	private CommitEvaluation createEvalWithValidation(String hash, String validationResult) {
		return new CommitEvaluation(
				hash, "commit message", "https://github.com/test/repo",
				Instant.now(), null, true, null, false, null,
				4, 3, 2, TrafficLight.GREEN,
				"Collections", false, "reason",
				true, "rule text", "file.sandbox-hint",
				null, null, "summary", validationResult);
	}
}
