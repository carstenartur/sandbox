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
package org.sandbox.mining.core.inference;

/**
 * Represents a transformation rule inferred from a before/after code pair.
 *
 * @param pattern       the DSL pattern (before-side with placeholders)
 * @param replacement   the DSL replacement (after-side with placeholders)
 * @param confidence    confidence score from 0.0 to 1.0
 * @param occurrences   how many times this pattern was observed
 * @param sourceCommit  the commit this rule was derived from (may be null)
 * @param category      the category of the transformation (e.g. "encoding", "modernize")
 * @param dslRule       the complete DSL rule string in .sandbox-hint format
 */
public record TransformationRule(
		String pattern,
		String replacement,
		double confidence,
		int occurrences,
		String sourceCommit,
		String category,
		String dslRule) {

	/**
	 * Creates a simple rule without metadata.
	 */
	public static TransformationRule of(String pattern, String replacement, double confidence) {
		return new TransformationRule(pattern, replacement, confidence, 1, null, null, null);
	}

	/**
	 * Returns a copy with the given DSL rule string.
	 */
	public TransformationRule withDslRule(String dsl) {
		return new TransformationRule(pattern, replacement, confidence,
				occurrences, sourceCommit, category, dsl);
	}

	/**
	 * Returns a copy with the given category.
	 */
	public TransformationRule withCategory(String cat) {
		return new TransformationRule(pattern, replacement, confidence,
				occurrences, sourceCommit, cat, dslRule);
	}

	/**
	 * Returns a copy with incremented occurrence count.
	 */
	public TransformationRule withIncrementedOccurrences() {
		return new TransformationRule(pattern, replacement, confidence,
				occurrences + 1, sourceCommit, category, dslRule);
	}
}
