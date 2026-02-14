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
package org.sandbox.jdt.triggerpattern.mining.analysis;

import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.internal.PatternParser;

/**
 * Validates an {@link InferredRule} by checking that its source and replacement
 * patterns are parseable and that all placeholders are consistent.
 *
 * @since 1.2.6
 */
public class InferredRuleValidator {

	/**
	 * Validation outcome.
	 */
	public enum ValidationStatus {
		/** The rule is valid. */
		VALID,
		/** The source pattern cannot be parsed. */
		SOURCE_UNPARSEABLE,
		/** The replacement pattern cannot be parsed. */
		REPLACEMENT_UNPARSEABLE,
		/** A placeholder in the replacement is not present in the source. */
		PLACEHOLDER_MISMATCH,
		/** The confidence is below the minimum threshold. */
		LOW_CONFIDENCE
	}

	/**
	 * Result of validating an inferred rule.
	 *
	 * @param status  the validation status
	 * @param message a human-readable description
	 */
	public record ValidationResult(ValidationStatus status, String message) {
	}

	private static final double MIN_CONFIDENCE = 0.3;
	private final PatternParser parser = new PatternParser();

	/**
	 * Validates the given inferred rule.
	 *
	 * @param rule the rule to validate
	 * @return the validation result
	 */
	public ValidationResult validate(InferredRule rule) {
		if (rule.confidence() < MIN_CONFIDENCE) {
			return new ValidationResult(ValidationStatus.LOW_CONFIDENCE,
					"Confidence " + rule.confidence() + " is below threshold " + MIN_CONFIDENCE); //$NON-NLS-1$ //$NON-NLS-2$
		}

		// Check that source pattern is parseable
		try {
			Pattern sourcePattern = new Pattern(rule.sourcePattern(), rule.kind());
			parser.parse(sourcePattern);
		} catch (Exception e) {
			return new ValidationResult(ValidationStatus.SOURCE_UNPARSEABLE,
					"Source pattern is not parseable: " + e.getMessage()); //$NON-NLS-1$
		}

		// Check that replacement pattern is parseable
		try {
			Pattern replacementPattern = new Pattern(rule.replacementPattern(), rule.kind());
			parser.parse(replacementPattern);
		} catch (Exception e) {
			return new ValidationResult(ValidationStatus.REPLACEMENT_UNPARSEABLE,
					"Replacement pattern is not parseable: " + e.getMessage()); //$NON-NLS-1$
		}

		// Check placeholder consistency: all placeholders used in replacement
		// must also appear in the source
		for (String placeholder : rule.placeholderNames()) {
			if (rule.replacementPattern().contains(placeholder)
					&& !rule.sourcePattern().contains(placeholder)) {
				return new ValidationResult(ValidationStatus.PLACEHOLDER_MISMATCH,
						"Placeholder " + placeholder //$NON-NLS-1$
								+ " used in replacement but not in source"); //$NON-NLS-1$
			}
		}

		return new ValidationResult(ValidationStatus.VALID, "Rule is valid"); //$NON-NLS-1$
	}
}
