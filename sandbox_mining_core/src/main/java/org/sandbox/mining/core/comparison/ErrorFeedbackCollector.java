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
package org.sandbox.mining.core.comparison;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation;

/**
 * Collects error patterns from mining evaluations to provide
 * feedback for prompt improvement in subsequent runs.
 *
 * <p>Tracks common DSL validation errors and patterns that
 * the LLM repeatedly gets wrong.</p>
 */
public class ErrorFeedbackCollector {

	private final List<String> validationErrors = new ArrayList<>();
	private final Map<String, Integer> errorPatterns = new LinkedHashMap<>();

	/**
	 * Collects errors from a list of evaluations.
	 *
	 * @param evaluations the evaluations to scan for errors
	 */
	public void collect(List<CommitEvaluation> evaluations) {
		for (CommitEvaluation eval : evaluations) {
			if (eval.dslValidationResult() != null
					&& !"VALID".equals(eval.dslValidationResult())) { //$NON-NLS-1$
				validationErrors.add(eval.dslValidationResult());
				errorPatterns.merge(categorizeError(eval.dslValidationResult()), 1, Integer::sum);
			}
		}
	}

	/**
	 * Formats collected errors as a feedback section for prompt inclusion.
	 *
	 * @return formatted error feedback string
	 */
	public String formatFeedback() {
		if (validationErrors.isEmpty()) {
			return ""; //$NON-NLS-1$
		}
		StringBuilder sb = new StringBuilder();
		sb.append("## Common Errors from Previous Runs\n\n"); //$NON-NLS-1$
		sb.append("The following errors occurred in previous mining runs. "); //$NON-NLS-1$
		sb.append("Please avoid these patterns:\n\n"); //$NON-NLS-1$
		for (Map.Entry<String, Integer> entry : errorPatterns.entrySet()) {
			sb.append("- **").append(entry.getKey()).append("** (").append(entry.getValue()) //$NON-NLS-1$ //$NON-NLS-2$
					.append(" occurrences)\n"); //$NON-NLS-1$
		}
		sb.append("\n"); //$NON-NLS-1$
		return sb.toString();
	}

	/**
	 * Returns the total number of validation errors collected.
	 *
	 * @return error count
	 */
	public int getErrorCount() {
		return validationErrors.size();
	}

	/**
	 * Returns the error pattern counts.
	 *
	 * @return unmodifiable map of pattern to count
	 */
	public Map<String, Integer> getErrorPatterns() {
		return Map.copyOf(errorPatterns);
	}

	static String categorizeError(String error) {
		if (error == null) {
			return "Unknown"; //$NON-NLS-1$
		}
		String lower = error.toLowerCase();
		if (lower.contains("xml") || lower.contains("<trigger") || lower.contains("<import")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			return "Used XML/HTML tags instead of plain DSL"; //$NON-NLS-1$
		}
		if (lower.contains("istype")) { //$NON-NLS-1$
			return "Used isType() instead of instanceof()"; //$NON-NLS-1$
		}
		if (lower.contains("parse") || lower.contains("syntax")) { //$NON-NLS-1$ //$NON-NLS-2$
			return "DSL syntax error"; //$NON-NLS-1$
		}
		return "Other validation error: " + error.substring(0, Math.min(80, error.length())); //$NON-NLS-1$
	}
}
