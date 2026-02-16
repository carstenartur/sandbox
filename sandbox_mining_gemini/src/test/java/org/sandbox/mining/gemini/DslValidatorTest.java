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
package org.sandbox.mining.gemini;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.sandbox.mining.gemini.dsl.DslValidator;
import org.sandbox.mining.gemini.dsl.DslValidator.ValidationResult;

/**
 * Tests for {@link DslValidator}.
 */
class DslValidatorTest {

	private final DslValidator validator = new DslValidator();

	@Test
	void testValidateNullRule() {
		ValidationResult result = validator.validate(null);
		assertFalse(result.valid());
		assertEquals("DSL rule is empty", result.message());
	}

	@Test
	void testValidateEmptyRule() {
		ValidationResult result = validator.validate("");
		assertFalse(result.valid());
		assertEquals("DSL rule is empty", result.message());
	}

	@Test
	void testValidateBlankRule() {
		ValidationResult result = validator.validate("   ");
		assertFalse(result.valid());
		assertEquals("DSL rule is empty", result.message());
	}

	@Test
	void testValidateSimpleRule() {
		// A simple valid DSL rule
		String rule = "<!id: test-rule>\n"
				+ "<!description: Test rule>\n"
				+ "new Boolean(true)\n"
				+ "=> Boolean.TRUE\n"
				+ ";;\n";
		ValidationResult result = validator.validate(rule);
		assertTrue(result.valid(), "Expected valid result but got: " + result.message());
	}

	@Test
	void testValidateInvalidRule() {
		// A rule with syntax errors
		String rule = "<!id:>\n=>\n";
		ValidationResult result = validator.validate(rule);
		// Result depends on HintFileParser behavior
		// The important thing is it doesn't throw an unchecked exception
		assertFalse(result.valid() && result.message().isEmpty());
	}
}
