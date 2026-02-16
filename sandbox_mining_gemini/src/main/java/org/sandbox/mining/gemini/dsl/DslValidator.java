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
package org.sandbox.mining.gemini.dsl;

import java.io.StringReader;

import org.sandbox.jdt.triggerpattern.internal.HintFileParser;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser.HintParseException;

/**
 * Validates DSL rules using the {@link HintFileParser} from sandbox_common_core.
 *
 * <p>Parses a DSL rule string and reports whether it is syntactically valid.</p>
 */
public class DslValidator {

	private final HintFileParser parser;

	public DslValidator() {
		this.parser = new HintFileParser();
	}

	/**
	 * Validates a DSL rule string.
	 *
	 * @param dslRule the DSL rule to validate
	 * @return validation result
	 */
	public ValidationResult validate(String dslRule) {
		if (dslRule == null || dslRule.isBlank()) {
			return new ValidationResult(false, "DSL rule is empty");
		}

		try {
			parser.parse(new StringReader(dslRule));
			return new ValidationResult(true, "Valid DSL rule");
		} catch (HintParseException e) {
			return new ValidationResult(false,
					"Parse error at line " + e.getLineNumber() + ": " + e.getMessage());
		} catch (Exception e) {
			return new ValidationResult(false, "Validation error: " + e.getMessage());
		}
	}

	/**
	 * Result of a DSL validation.
	 *
	 * @param valid   whether the rule is valid
	 * @param message description or error message
	 */
	public record ValidationResult(boolean valid, String message) {
	}
}
