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
package org.sandbox.jdt.internal.css.core;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small dependency-free JSON parser used for CSS tool configuration and reports. */
final class SimpleJsonParser {

	private final String input;
	private int offset;

	private SimpleJsonParser(String input) {
		this.input = input;
	}

	static Object parse(String input) {
		if (input == null) {
			throw new IllegalArgumentException("JSON input must not be null"); //$NON-NLS-1$
		}
		SimpleJsonParser parser = new SimpleJsonParser(input);
		Object value = parser.parseValue();
		parser.skipWhitespace();
		if (parser.offset != input.length()) {
			throw parser.error("Unexpected trailing content"); //$NON-NLS-1$
		}
		return value;
	}

	@SuppressWarnings("unchecked")
	static Map<String, Object> parseObject(String input) {
		Object value = parse(input);
		if (!(value instanceof Map<?, ?>)) {
			throw new IllegalArgumentException("Expected a JSON object"); //$NON-NLS-1$
		}
		return (Map<String, Object>) value;
	}

	private Object parseValue() {
		skipWhitespace();
		if (offset >= input.length()) {
			throw error("Expected JSON value"); //$NON-NLS-1$
		}
		return switch (input.charAt(offset)) {
		case '{' -> parseObjectValue();
		case '[' -> parseArray();
		case '"' -> parseString();
		case 't' -> parseLiteral("true", Boolean.TRUE); //$NON-NLS-1$
		case 'f' -> parseLiteral("false", Boolean.FALSE); //$NON-NLS-1$
		case 'n' -> parseLiteral("null", null); //$NON-NLS-1$
		default -> parseNumber();
		};
	}

	private Map<String, Object> parseObjectValue() {
		expect('{');
		Map<String, Object> result = new LinkedHashMap<>();
		skipWhitespace();
		if (consume('}')) {
			return result;
		}
		while (true) {
			skipWhitespace();
			if (offset >= input.length() || input.charAt(offset) != '"') {
				throw error("Expected object key"); //$NON-NLS-1$
			}
			String key = parseString();
			skipWhitespace();
			expect(':');
			result.put(key, parseValue());
			skipWhitespace();
			if (consume('}')) {
				return result;
			}
			expect(',');
		}
	}

	private List<Object> parseArray() {
		expect('[');
		List<Object> result = new ArrayList<>();
		skipWhitespace();
		if (consume(']')) {
			return result;
		}
		while (true) {
			result.add(parseValue());
			skipWhitespace();
			if (consume(']')) {
				return result;
			}
			expect(',');
		}
	}

	private String parseString() {
		expect('"');
		StringBuilder result = new StringBuilder();
		while (offset < input.length()) {
			char current = input.charAt(offset++);
			if (current == '"') {
				return result.toString();
			}
			if (current != '\\') {
				if (current < 0x20) {
					throw error("Control character in JSON string"); //$NON-NLS-1$
				}
				result.append(current);
				continue;
			}
			if (offset >= input.length()) {
				throw error("Unterminated JSON escape"); //$NON-NLS-1$
			}
			char escaped = input.charAt(offset++);
			switch (escaped) {
			case '"', '\\', '/' -> result.append(escaped);
			case 'b' -> result.append('\b');
			case 'f' -> result.append('\f');
			case 'n' -> result.append('\n');
			case 'r' -> result.append('\r');
			case 't' -> result.append('\t');
			case 'u' -> result.append(parseUnicodeEscape());
			default -> throw error("Unsupported JSON escape: \\" + escaped); //$NON-NLS-1$
			}
		}
		throw error("Unterminated JSON string"); //$NON-NLS-1$
	}

	private char parseUnicodeEscape() {
		if (offset + 4 > input.length()) {
			throw error("Incomplete Unicode escape"); //$NON-NLS-1$
		}
		int value = 0;
		for (int i = 0; i < 4; i++) {
			int digit = Character.digit(input.charAt(offset++), 16);
			if (digit < 0) {
				throw error("Invalid Unicode escape"); //$NON-NLS-1$
			}
			value = (value << 4) | digit;
		}
		return (char) value;
	}

	private Object parseLiteral(String literal, Object value) {
		if (!input.startsWith(literal, offset)) {
			throw error("Expected " + literal); //$NON-NLS-1$
		}
		offset += literal.length();
		return value;
	}

	private BigDecimal parseNumber() {
		int start = offset;
		consume('-');
		if (consume('0')) {
			// A leading zero is complete unless a fraction/exponent follows.
		} else {
			consumeDigits(true);
		}
		if (consume('.')) {
			consumeDigits(true);
		}
		if (consume('e') || consume('E')) {
			if (!consume('+')) {
				consume('-');
			}
			consumeDigits(true);
		}
		if (start == offset) {
			throw error("Expected JSON value"); //$NON-NLS-1$
		}
		try {
			return new BigDecimal(input.substring(start, offset));
		} catch (NumberFormatException e) {
			throw error("Invalid JSON number"); //$NON-NLS-1$
		}
	}

	private void consumeDigits(boolean atLeastOne) {
		int start = offset;
		while (offset < input.length() && Character.isDigit(input.charAt(offset))) {
			offset++;
		}
		if (atLeastOne && start == offset) {
			throw error("Expected digit"); //$NON-NLS-1$
		}
	}

	private boolean consume(char expected) {
		if (offset < input.length() && input.charAt(offset) == expected) {
			offset++;
			return true;
		}
		return false;
	}

	private void expect(char expected) {
		if (!consume(expected)) {
			throw error("Expected '" + expected + "'"); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	private void skipWhitespace() {
		while (offset < input.length() && Character.isWhitespace(input.charAt(offset))) {
			offset++;
		}
	}

	private IllegalArgumentException error(String message) {
		return new IllegalArgumentException(message + " at character " + offset); //$NON-NLS-1$
	}
}
