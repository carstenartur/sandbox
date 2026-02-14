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

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.mining.analysis.InferredRule;
import org.sandbox.jdt.triggerpattern.mining.analysis.InferredRuleValidator;
import org.sandbox.jdt.triggerpattern.mining.analysis.InferredRuleValidator.ValidationResult;
import org.sandbox.jdt.triggerpattern.mining.analysis.InferredRuleValidator.ValidationStatus;

/**
 * Tests for {@link InferredRuleValidator}.
 */
public class InferredRuleValidatorTest {

	private final InferredRuleValidator validator = new InferredRuleValidator();

	@Test
	public void testValidRulePassesValidation() {
		InferredRule rule = new InferredRule(
				"Collections.emptyList()", //$NON-NLS-1$
				"List.of()", //$NON-NLS-1$
				PatternKind.METHOD_CALL,
				0.9,
				List.of(),
				null);

		ValidationResult result = validator.validate(rule);
		assertNotNull(result);
		assertEquals(ValidationStatus.VALID, result.status(),
				"Valid rule should pass validation"); //$NON-NLS-1$
	}

	@Test
	public void testLowConfidenceRejected() {
		InferredRule rule = new InferredRule(
				"Collections.emptyList()", //$NON-NLS-1$
				"List.of()", //$NON-NLS-1$
				PatternKind.METHOD_CALL,
				0.1, // below threshold
				List.of(),
				null);

		ValidationResult result = validator.validate(rule);
		assertEquals(ValidationStatus.LOW_CONFIDENCE, result.status(),
				"Low confidence rule should be rejected"); //$NON-NLS-1$
	}

	@Test
	public void testPlaceholderInReplacementNotInSourceRejected() {
		InferredRule rule = new InferredRule(
				"Collections.emptyList()", //$NON-NLS-1$
				"List.of($phantom)", //$NON-NLS-1$
				PatternKind.METHOD_CALL,
				0.9,
				List.of("$phantom"), //$NON-NLS-1$
				null);

		ValidationResult result = validator.validate(rule);
		assertEquals(ValidationStatus.PLACEHOLDER_MISMATCH, result.status(),
				"Placeholder missing from source should be rejected"); //$NON-NLS-1$
	}

	@Test
	public void testPlaceholderInBothSourceAndReplacementAccepted() {
		InferredRule rule = new InferredRule(
				"$x.toString()", //$NON-NLS-1$
				"String.valueOf($x)", //$NON-NLS-1$
				PatternKind.METHOD_CALL,
				0.9,
				List.of("$x"), //$NON-NLS-1$
				null);

		ValidationResult result = validator.validate(rule);
		assertEquals(ValidationStatus.VALID, result.status(),
				"Placeholder in both source and replacement should be valid"); //$NON-NLS-1$
	}

	@Test
	public void testValidationResultHasMessage() {
		InferredRule rule = new InferredRule(
				"Collections.emptyList()", //$NON-NLS-1$
				"List.of()", //$NON-NLS-1$
				PatternKind.METHOD_CALL,
				0.9,
				List.of(),
				null);

		ValidationResult result = validator.validate(rule);
		assertNotNull(result.message(),
				"Validation result should contain a message"); //$NON-NLS-1$
	}
}
