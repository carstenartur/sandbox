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

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.sandbox.jdt.triggerpattern.internal.HintFileParser;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser.HintParseException;

/**
 * Validates DSL rules using the {@link HintFileParser} from sandbox_common_core.
 *
 * <p>Parses a DSL rule string and reports whether it is syntactically valid.</p>
 *
 * <p>Can be used as a CLI tool: {@code java ... DslValidator <file>}</p>
 */
public class DslValidator {

	private final HintFileParser parser;

	public DslValidator() {
		this.parser = new HintFileParser();
	}

	/**
	 * CLI entry point that reads a file and validates its DSL content.
	 *
	 * @param args the file path to validate
	 */
	public static void main(String[] args) {
		if (args.length < 1) {
			System.err.println("Usage: DslValidator <file>");
			System.exit(1);
		}
		Path filePath = Path.of(args[0]);
		if (!Files.exists(filePath)) {
			System.err.println("File not found: " + filePath);
			System.exit(1);
		}
		try {
			String content = Files.readString(filePath, StandardCharsets.UTF_8);
			DslValidator validator = new DslValidator();
			ValidationResult result = validator.validate(content);
			if (result.valid()) {
				System.out.println("Valid: " + filePath);
			} else {
				System.err.println("Invalid: " + result.message());
				System.exit(1);
			}
		} catch (IOException e) {
			System.err.println("Error reading file: " + e.getMessage());
			System.exit(1);
		}
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
