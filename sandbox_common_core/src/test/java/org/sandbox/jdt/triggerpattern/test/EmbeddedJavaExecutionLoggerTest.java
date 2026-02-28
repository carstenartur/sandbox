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
package org.sandbox.jdt.triggerpattern.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.internal.EmbeddedJavaExecutionLogger;
import org.sandbox.jdt.triggerpattern.internal.EmbeddedJavaExecutionLogger.ExecutionTrace;

/**
 * Tests for {@link EmbeddedJavaExecutionLogger}.
 *
 * @since 1.5.0
 */
public class EmbeddedJavaExecutionLoggerTest {

	private EmbeddedJavaExecutionLogger logger;

	@BeforeEach
	public void setUp() {
		logger = new EmbeddedJavaExecutionLogger();
	}

	@Test
	public void testLogGuardExecution() {
		Map<String, String> bindings = Map.of("$x", "foo", "$y", "bar");

		logger.logGuardExecution("rule.1", "isPositive", bindings, true, 5);

		assertEquals(1, logger.getTraces().size());
		ExecutionTrace trace = logger.getTraces().get(0);
		assertEquals("rule.1", trace.ruleId());
		assertEquals("isPositive", trace.functionName());
		assertEquals(true, trace.result());
		assertTrue(trace.isSuccess());
		assertEquals(5, trace.durationMs());
		assertEquals(bindings, trace.bindings());
	}

	@Test
	public void testLogExecutionFailure() {
		RuntimeException exception = new RuntimeException("test error");
		Map<String, String> bindings = Map.of("$x", "foo");

		logger.logExecutionFailure("rule.2", "brokenGuard", bindings, exception, 10);

		assertEquals(1, logger.getTraces().size());
		ExecutionTrace trace = logger.getTraces().get(0);
		assertEquals("rule.2", trace.ruleId());
		assertEquals("brokenGuard", trace.functionName());
		assertNull(trace.result());
		assertFalse(trace.isSuccess());
		assertNotNull(trace.exception());
		assertEquals("test error", trace.exception().getMessage());
		assertEquals(10, trace.durationMs());
	}

	@Test
	public void testLogFixExecution() {
		Map<String, String> bindings = Map.of("$x", "value");

		logger.logFixExecution("rule.3", "applyFix", bindings, 15);

		assertEquals(1, logger.getTraces().size());
		ExecutionTrace trace = logger.getTraces().get(0);
		assertEquals("rule.3", trace.ruleId());
		assertEquals("applyFix", trace.functionName());
		assertNull(trace.result());
		assertTrue(trace.isSuccess());
		assertEquals(15, trace.durationMs());
	}

	@Test
	public void testSuccessAndFailureCounts() {
		logger.logGuardExecution("r1", "g1", Map.of(), true, 1);
		logger.logGuardExecution("r2", "g2", Map.of(), false, 2);
		logger.logExecutionFailure("r3", "g3", Map.of(),
				new RuntimeException("error"), 3);
		logger.logFixExecution("r4", "f1", Map.of(), 4);

		assertEquals(3, logger.getSuccessCount());
		assertEquals(1, logger.getFailureCount());
		assertEquals(4, logger.getTraces().size());
	}

	@Test
	public void testClear() {
		logger.logGuardExecution("r1", "g1", Map.of(), true, 1);
		logger.logGuardExecution("r2", "g2", Map.of(), false, 2);

		assertEquals(2, logger.getTraces().size());

		logger.clear();

		assertEquals(0, logger.getTraces().size());
		assertEquals(0, logger.getSuccessCount());
		assertEquals(0, logger.getFailureCount());
	}

	@Test
	public void testEmptyLogger() {
		assertEquals(0, logger.getTraces().size());
		assertEquals(0, logger.getSuccessCount());
		assertEquals(0, logger.getFailureCount());
	}

	@Test
	public void testTracesAreUnmodifiable() {
		logger.logGuardExecution("r1", "g1", Map.of(), true, 1);

		assertThrows(UnsupportedOperationException.class, () -> logger.getTraces().clear());

		// Original traces should still be there
		assertEquals(1, logger.getTraces().size());
	}

	@Test
	public void testEmptyBindings() {
		logger.logGuardExecution("r1", "g1", Map.of(), true, 0);

		ExecutionTrace trace = logger.getTraces().get(0);
		assertTrue(trace.bindings().isEmpty());
		assertEquals(0, trace.durationMs());
	}
}
