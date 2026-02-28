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
package org.sandbox.jdt.triggerpattern.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Structured logger for embedded Java guard/fix function execution.
 *
 * <p>Captures execution traces when hint rules with embedded Java code are
 * evaluated during cleanup/quickfix operations. Each trace records:</p>
 * <ul>
 *   <li>Which guard/fix function was called</li>
 *   <li>Input bindings (placeholder → value mappings)</li>
 *   <li>Return value or exception</li>
 *   <li>Execution time in milliseconds</li>
 * </ul>
 *
 * <p>Backed by {@link java.util.logging.Logger} for consistency with the
 * existing {@link HintFileParser} logging.</p>
 *
 * @since 1.5.0
 */
public final class EmbeddedJavaExecutionLogger {

	private static final Logger LOGGER = Logger.getLogger(EmbeddedJavaExecutionLogger.class.getName());

	private final List<ExecutionTrace> traces = new ArrayList<>();

	/**
	 * An execution trace record capturing details of a single guard/fix invocation.
	 *
	 * @param ruleId        the hint rule ID
	 * @param functionName  the guard/fix function name
	 * @param bindings      input placeholder bindings (may be empty)
	 * @param result        the return value (for guards: Boolean; for fix: null)
	 * @param exception     the exception if execution failed, or {@code null}
	 * @param durationMs    execution time in milliseconds
	 */
	public record ExecutionTrace(
			String ruleId,
			String functionName,
			Map<String, String> bindings,
			Object result,
			Throwable exception,
			long durationMs) {

		/**
		 * Returns {@code true} if the execution succeeded without exception.
		 *
		 * @return {@code true} if successful
		 */
		public boolean isSuccess() {
			return exception == null;
		}
	}

	/**
	 * Logs a successful guard function execution.
	 *
	 * @param ruleId       the hint rule ID
	 * @param functionName the guard function name
	 * @param bindings     input placeholder bindings
	 * @param result       the boolean result of the guard
	 * @param durationMs   execution time in milliseconds
	 */
	public void logGuardExecution(String ruleId, String functionName,
			Map<String, String> bindings, boolean result, long durationMs) {
		ExecutionTrace trace = new ExecutionTrace(
				ruleId, functionName, bindings, result, null, durationMs);
		traces.add(trace);

		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE,
					"Guard {0}::{1} returned {2} in {3}ms (bindings: {4})", //$NON-NLS-1$
					new Object[] { ruleId, functionName, result, durationMs, bindings });
		}
	}

	/**
	 * Logs a failed guard/fix function execution.
	 *
	 * @param ruleId       the hint rule ID
	 * @param functionName the function name
	 * @param bindings     input placeholder bindings
	 * @param exception    the exception that occurred
	 * @param durationMs   execution time in milliseconds
	 */
	public void logExecutionFailure(String ruleId, String functionName,
			Map<String, String> bindings, Throwable exception, long durationMs) {
		ExecutionTrace trace = new ExecutionTrace(
				ruleId, functionName, bindings, null, exception, durationMs);
		traces.add(trace);

		LOGGER.log(Level.WARNING,
				"Guard {0}::{1} failed after {2}ms: {3}", //$NON-NLS-1$
				new Object[] { ruleId, functionName, durationMs, exception.getMessage() });
	}

	/**
	 * Logs a successful fix function execution.
	 *
	 * @param ruleId       the hint rule ID
	 * @param functionName the fix function name
	 * @param bindings     input placeholder bindings
	 * @param durationMs   execution time in milliseconds
	 */
	public void logFixExecution(String ruleId, String functionName,
			Map<String, String> bindings, long durationMs) {
		ExecutionTrace trace = new ExecutionTrace(
				ruleId, functionName, bindings, null, null, durationMs);
		traces.add(trace);

		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE,
					"Fix {0}::{1} executed in {2}ms (bindings: {3})", //$NON-NLS-1$
					new Object[] { ruleId, functionName, durationMs, bindings });
		}
	}

	/**
	 * Returns all recorded execution traces.
	 *
	 * @return unmodifiable list of traces
	 */
	public List<ExecutionTrace> getTraces() {
		return Collections.unmodifiableList(traces);
	}

	/**
	 * Returns the number of successful guard executions.
	 *
	 * @return the count of successful guard traces
	 */
	public long getSuccessCount() {
		return traces.stream().filter(ExecutionTrace::isSuccess).count();
	}

	/**
	 * Returns the number of failed executions.
	 *
	 * @return the count of failed traces
	 */
	public long getFailureCount() {
		return traces.stream().filter(t -> !t.isSuccess()).count();
	}

	/**
	 * Clears all recorded traces.
	 */
	public void clear() {
		traces.clear();
	}
}
