/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.sandbox.jdt.triggerpattern.api.RewriteActionCatalog;
import org.sandbox.jdt.triggerpattern.api.RewriteActionValue;
import org.sandbox.jdt.triggerpattern.api.SemanticPlanValue;
import org.sandbox.jdt.triggerpattern.api.StructuredRewriteAction;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser.HintParseException;

/** Recursive-descent parser for {@code =>! action(...)} alternatives. */
final class StructuredRewriteActionParser {

	private final String source;
	private final int lineNumber;
	private final RewriteActionCatalog catalog;
	private int offset;

	private StructuredRewriteActionParser(String source, int lineNumber,
			RewriteActionCatalog catalog) {
		this.source= source == null ? "" : source; //$NON-NLS-1$
		this.lineNumber= lineNumber;
		this.catalog= catalog;
	}

	static List<StructuredRewriteAction> parse(String source, int lineNumber,
			RewriteActionCatalog catalog) throws HintParseException {
		StructuredRewriteActionParser parser= new StructuredRewriteActionParser(source, lineNumber, catalog);
		return parser.parseSequence();
	}

	private List<StructuredRewriteAction> parseSequence() throws HintParseException {
		List<StructuredRewriteAction> actions= new ArrayList<>();
		skipWhitespace();
		while (!atEnd()) {
			StructuredRewriteAction action= parseAction();
			try {
				catalog.validate(action);
			} catch (IllegalArgumentException exception) {
				throw error(exception.getMessage());
			}
			actions.add(action);
			skipWhitespace();
			if (atEnd()) {
				break;
			}
			expect(';');
			skipWhitespace();
			if (atEnd()) {
				throw error("Structured action sequence ends after ';'"); //$NON-NLS-1$
			}
		}
		if (actions.isEmpty()) {
			throw error("Structured rewrite alternative contains no action"); //$NON-NLS-1$
		}
		return List.copyOf(actions);
	}

	private StructuredRewriteAction parseAction() throws HintParseException {
		String name= parseIdentifier("action name"); //$NON-NLS-1$
		skipWhitespace();
		expect('(');
		skipWhitespace();
		Map<String, RewriteActionValue> arguments= new LinkedHashMap<>();
		if (!peek(')')) {
			while (true) {
				String argumentName= parseIdentifier("action argument"); //$NON-NLS-1$
				skipWhitespace();
				expect('=');
				skipWhitespace();
				RewriteActionValue value= parseValue();
				if (arguments.putIfAbsent(argumentName, value) != null) {
					throw error("Duplicate action argument " + argumentName); //$NON-NLS-1$
				}
				skipWhitespace();
				if (!peek(',')) {
					break;
				}
				offset++;
				skipWhitespace();
			}
		}
		expect(')');
		try {
			return new StructuredRewriteAction(name, arguments, lineNumber);
		} catch (IllegalArgumentException exception) {
			throw error(exception.getMessage());
		}
	}

	private RewriteActionValue parseValue() throws HintParseException {
		if (atEnd()) {
			throw error("Missing action argument value"); //$NON-NLS-1$
		}
		char current= source.charAt(offset);
		if (current == '"') {
			return RewriteActionValue.literal(SemanticPlanValue.string(parseString()));
		}
		if (current == '$') {
			return RewriteActionValue.binding(parsePlaceholder());
		}
		if (current == '-' || Character.isDigit(current)) {
			return parseIntegerLiteral();
		}
		String token= parseQualifiedIdentifier();
		skipWhitespace();
		if (peek('(')) {
			return parseValueFunction(token);
		}
		if ("true".equals(token) || "false".equals(token)) { //$NON-NLS-1$ //$NON-NLS-2$
			return RewriteActionValue.literal(SemanticPlanValue.bool(Boolean.parseBoolean(token)));
		}
		return RewriteActionValue.literal(SemanticPlanValue.string(token));
	}

	private RewriteActionValue parseValueFunction(String function) throws HintParseException {
		expect('(');
		skipWhitespace();
		List<RewriteActionValue> arguments= new ArrayList<>();
		if (!peek(')')) {
			while (true) {
				arguments.add(parseValue());
				skipWhitespace();
				if (!peek(',')) {
					break;
				}
				offset++;
				skipWhitespace();
			}
		}
		expect(')');
		return switch (function) {
			case "planValue" -> planValue(arguments); //$NON-NLS-1$
			case "classLiteral" -> unary(function, arguments, RewriteActionValue::classLiteral); //$NON-NLS-1$
			case "name" -> unary(function, arguments, RewriteActionValue::name); //$NON-NLS-1$
			case "list" -> new RewriteActionValue.ListValue(arguments); //$NON-NLS-1$
			default -> throw error("Unknown action value function " + function); //$NON-NLS-1$
		};
	}

	private RewriteActionValue planValue(List<RewriteActionValue> arguments) throws HintParseException {
		if (arguments.size() == 1) {
			return RewriteActionValue.planValue(stringLiteral(arguments.get(0), "planValue fact name")); //$NON-NLS-1$
		}
		if (arguments.size() == 2 && arguments.get(0) instanceof RewriteActionValue.Binding binding) {
			return RewriteActionValue.planValue(binding.placeholder(),
					stringLiteral(arguments.get(1), "planValue fact name")); //$NON-NLS-1$
		}
		throw error("planValue expects a fact name or one binding and a fact name"); //$NON-NLS-1$
	}

	private RewriteActionValue unary(String function, List<RewriteActionValue> arguments,
			java.util.function.Function<RewriteActionValue, RewriteActionValue> factory)
			throws HintParseException {
		if (arguments.size() != 1) {
			throw error(function + " expects exactly one value"); //$NON-NLS-1$
		}
		return factory.apply(arguments.get(0));
	}

	private String stringLiteral(RewriteActionValue value, String label) throws HintParseException {
		if (value instanceof RewriteActionValue.Literal literal
				&& literal.value() instanceof SemanticPlanValue.StringValue text) {
			return text.value();
		}
		throw error(label + " must be a string literal"); //$NON-NLS-1$
	}

	private RewriteActionValue parseIntegerLiteral() throws HintParseException {
		int start= offset;
		if (source.charAt(offset) == '-') {
			offset++;
		}
		int digitStart= offset;
		while (!atEnd() && Character.isDigit(source.charAt(offset))) {
			offset++;
		}
		if (digitStart == offset) {
			throw error("Invalid integer action value"); //$NON-NLS-1$
		}
		try {
			return RewriteActionValue.literal(
					SemanticPlanValue.integer(Long.parseLong(source.substring(start, offset))));
		} catch (NumberFormatException exception) {
			throw error("Integer action value is out of range"); //$NON-NLS-1$
		}
	}

	private String parseString() throws HintParseException {
		expect('"');
		StringBuilder value= new StringBuilder();
		while (!atEnd()) {
			char current= source.charAt(offset++);
			if (current == '"') {
				return value.toString();
			}
			if (current != '\\') {
				value.append(current);
				continue;
			}
			if (atEnd()) {
				throw error("Unterminated escape in action string"); //$NON-NLS-1$
			}
			char escaped= source.charAt(offset++);
			value.append(switch (escaped) {
				case 'n' -> '\n';
				case 'r' -> '\r';
				case 't' -> '\t';
				case '"' -> '"';
				case '\\' -> '\\';
				default -> throw error("Unsupported action string escape \\" + escaped); //$NON-NLS-1$
			});
		}
		throw error("Unterminated action string"); //$NON-NLS-1$
	}

	private String parsePlaceholder() throws HintParseException {
		int start= offset++;
		if (atEnd() || !Character.isJavaIdentifierStart(source.charAt(offset))) {
			throw error("Invalid binding placeholder"); //$NON-NLS-1$
		}
		offset++;
		while (!atEnd() && Character.isJavaIdentifierPart(source.charAt(offset))) {
			offset++;
		}
		if (!atEnd() && source.charAt(offset) == '$') {
			offset++;
		}
		return source.substring(start, offset);
	}

	private String parseIdentifier(String label) throws HintParseException {
		skipWhitespace();
		if (atEnd() || !Character.isJavaIdentifierStart(source.charAt(offset))) {
			throw error("Expected " + label); //$NON-NLS-1$
		}
		int start= offset++;
		while (!atEnd() && Character.isJavaIdentifierPart(source.charAt(offset))) {
			offset++;
		}
		return source.substring(start, offset);
	}

	private String parseQualifiedIdentifier() throws HintParseException {
		int start= offset;
		parseIdentifier("action value"); //$NON-NLS-1$
		while (!atEnd() && source.charAt(offset) == '.') {
			offset++;
			parseIdentifier("qualified action value segment"); //$NON-NLS-1$
		}
		return source.substring(start, offset);
	}

	private void expect(char expected) throws HintParseException {
		skipWhitespace();
		if (atEnd() || source.charAt(offset) != expected) {
			throw error("Expected '" + expected + "'"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		offset++;
	}

	private boolean peek(char expected) {
		skipWhitespace();
		return !atEnd() && source.charAt(offset) == expected;
	}

	private void skipWhitespace() {
		while (!atEnd() && Character.isWhitespace(source.charAt(offset))) {
			offset++;
		}
	}

	private boolean atEnd() {
		return offset >= source.length();
	}

	private HintParseException error(String message) {
		return new HintParseException(message + " at action offset " + offset, lineNumber); //$NON-NLS-1$
	}
}
