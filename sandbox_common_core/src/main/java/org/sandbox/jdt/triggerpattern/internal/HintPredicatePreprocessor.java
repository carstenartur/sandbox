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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sandbox.jdt.triggerpattern.api.GuardExpression;
import org.sandbox.jdt.triggerpattern.api.HintPredicateDefinition;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser.HintParseException;

/** Extracts and expands declarative {@code <!predicate ...>} definitions. */
public final class HintPredicatePreprocessor {

	private static final String PREDICATE_KEYWORD= "predicate"; //$NON-NLS-1$

	/** Preprocessed source plus the immutable local predicate model. */
	public record Result(String expandedSource, List<HintPredicateDefinition> predicates) {
		public Result {
			predicates= List.copyOf(predicates);
		}
	}

	private record Extraction(String sourceWithoutDefinitions,
			Map<String, HintPredicateDefinition> definitions) {
	}

	private HintPredicatePreprocessor() {
	}

	public static Result preprocess(String source) throws HintParseException {
		if (source == null || source.isBlank()) {
			throw new HintParseException("Hint file content is empty", 0); //$NON-NLS-1$
		}
		Extraction extraction= extractDefinitions(source);
		ParsedPredicates predicates= new ParsedPredicates(extraction.definitions());
		predicates.validate();
		String expanded= expandGuardFragments(extraction.sourceWithoutDefinitions(), predicates);
		return new Result(expanded, new ArrayList<>(extraction.definitions().values()));
	}

	/** Returns local definitions without requiring the remaining program to be valid. */
	public static List<HintPredicateDefinition> discover(String source) {
		if (source == null || source.isBlank()) {
			return List.of();
		}
		try {
			return new ArrayList<>(extractDefinitions(source).definitions().values());
		} catch (HintParseException exception) {
			return List.of();
		}
	}

	private static Extraction extractDefinitions(String source) throws HintParseException {
		StringBuilder cleaned= new StringBuilder(source);
		Map<String, HintPredicateDefinition> definitions= new LinkedHashMap<>();
		int cursor= 0;
		while (cursor < source.length()) {
			int start= nextPredicateDirective(source, cursor);
			if (start < 0) {
				break;
			}
			int end= directiveEnd(source, start + 2);
			int line= lineNumber(source, start);
			if (end < 0) {
				throw new HintParseException("Unterminated predicate directive", line); //$NON-NLS-1$
			}
			HintPredicateDefinition definition= parseDefinition(source.substring(start + 2, end), line);
			if (definitions.putIfAbsent(definition.name(), definition) != null) {
				throw new HintParseException("Duplicate predicate " + definition.name(), line); //$NON-NLS-1$
			}
			blankExceptLineDelimiters(cleaned, start, end);
			cursor= end + 1;
		}
		return new Extraction(cleaned.toString(),
				Collections.unmodifiableMap(new LinkedHashMap<>(definitions)));
	}

	private static HintPredicateDefinition parseDefinition(String directive, int line)
			throws HintParseException {
		String text= directive.trim();
		int nameStart= PREDICATE_KEYWORD.length();
		boolean keywordBoundary= text.length() > nameStart
				&& Character.isWhitespace(text.charAt(nameStart));
		int open= text.indexOf('(', nameStart);
		int close= open < 0 ? -1 : text.indexOf(')', open + 1);
		int colon= close < 0 ? -1 : text.indexOf(':', close + 1);
		if (!text.startsWith(PREDICATE_KEYWORD) || !keywordBoundary
				|| open <= nameStart || close < open || colon < close) {
			throw new HintParseException(
					"Predicate syntax is <!predicate name($arg): guard-expression>", line); //$NON-NLS-1$
		}
		String name= text.substring(nameStart, open).trim();
		List<String> parameters= new ArrayList<>();
		String parameterText= text.substring(open + 1, close).trim();
		if (!parameterText.isEmpty()) {
			for (String parameter : parameterText.split(",")) { //$NON-NLS-1$
				parameters.add(parameter.trim());
			}
		}
		String expression= text.substring(colon + 1).trim();
		try {
			return new HintPredicateDefinition(name, parameters, expression, line);
		} catch (IllegalArgumentException exception) {
			throw parseFailure(exception.getMessage(), line, exception);
		}
	}

	private static String expandGuardFragments(String source, ParsedPredicates predicates)
			throws HintParseException {
		if (predicates.isEmpty()) {
			return source;
		}
		StringBuilder result= new StringBuilder(source.length());
		int offset= 0;
		int lineNumber= 1;
		while (offset < source.length()) {
			int lineEnd= source.indexOf('\n', offset);
			if (lineEnd < 0) {
				lineEnd= source.length();
			}
			result.append(expandLine(source.substring(offset, lineEnd), predicates, lineNumber));
			if (lineEnd < source.length()) {
				result.append('\n');
			}
			offset= lineEnd + 1;
			lineNumber++;
		}
		return result.toString();
	}

	private static String expandLine(String line, ParsedPredicates predicates, int lineNumber)
			throws HintParseException {
		int comment= commentStart(line);
		String code= comment < 0 ? line : line.substring(0, comment);
		String suffix= comment < 0 ? "" : line.substring(comment); //$NON-NLS-1$
		StringBuilder result= new StringBuilder(code.length());
		int cursor= 0;
		while (cursor < code.length()) {
			int separator= findOutsideString(code, "::", cursor); //$NON-NLS-1$
			if (separator < 0) {
				result.append(code, cursor, code.length());
				break;
			}
			result.append(code, cursor, separator + 2);
			int guardStart= separator + 2;
			int guardEnd= nextBoundary(code, guardStart);
			String guard= code.substring(guardStart, guardEnd);
			result.append(predicates.expandAndFormat(guard, lineNumber));
			cursor= guardEnd;
		}
		return result.append(suffix).toString();
	}

	private static int nextBoundary(String line, int start) {
		int arrow= findOutsideString(line, "=>", start); //$NON-NLS-1$
		int terminator= findOutsideString(line, ";;", start); //$NON-NLS-1$
		if (arrow < 0) {
			return terminator < 0 ? line.length() : terminator;
		}
		return terminator < 0 ? arrow : Math.min(arrow, terminator);
	}

	private static final class ParsedPredicates {
		private final Map<String, HintPredicateDefinition> definitions;
		private final Map<String, GuardExpression> expressions;
		private final GuardExpressionParser parser= new GuardExpressionParser();

		ParsedPredicates(Map<String, HintPredicateDefinition> definitions) throws HintParseException {
			this.definitions= Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
			this.expressions= new LinkedHashMap<>(mapCapacity(this.definitions.size()));
			for (HintPredicateDefinition definition : this.definitions.values()) {
				try {
					GuardExpression expression= parser.parse(definition.expression());
					validateParameterContract(definition, expression);
					expressions.put(definition.name(), expression);
				} catch (IllegalArgumentException exception) {
					throw parseFailure("Invalid predicate " + definition.signature() + ": " //$NON-NLS-1$ //$NON-NLS-2$
							+ exception.getMessage(), definition.lineNumber(), exception);
				}
			}
		}

		boolean isEmpty() {
			return definitions.isEmpty();
		}

		void validate() throws HintParseException {
			for (HintPredicateDefinition definition : definitions.values()) {
				expand(expressions.get(definition.name()), Map.of(),
						new ArrayDeque<>(List.of(definition.name())), definition.lineNumber());
			}
		}

		String expandAndFormat(String guard, int lineNumber) throws HintParseException {
			if (guard.isBlank()) {
				return guard;
			}
			try {
				GuardExpression parsed= parser.parse(guard.trim());
				return " " + format(expand(parsed, Map.of(), new ArrayDeque<>(), lineNumber)) + " "; //$NON-NLS-1$ //$NON-NLS-2$
			} catch (IllegalArgumentException exception) {
				throw parseFailure("Invalid guard expression: " + exception.getMessage(), //$NON-NLS-1$
						lineNumber, exception);
			}
		}

		private GuardExpression expand(GuardExpression expression, Map<String, String> substitutions,
				Deque<String> stack, int lineNumber) throws HintParseException {
			return switch (expression) {
				case GuardExpression.And and -> new GuardExpression.And(
						expand(and.left(), substitutions, stack, lineNumber),
						expand(and.right(), substitutions, stack, lineNumber));
				case GuardExpression.Or or -> new GuardExpression.Or(
						expand(or.left(), substitutions, stack, lineNumber),
						expand(or.right(), substitutions, stack, lineNumber));
				case GuardExpression.Not not -> new GuardExpression.Not(
						expand(not.operand(), substitutions, stack, lineNumber));
				case GuardExpression.FunctionCall call -> expandCall(call, substitutions, stack, lineNumber);
			};
		}

		private GuardExpression expandCall(GuardExpression.FunctionCall call,
				Map<String, String> substitutions, Deque<String> stack, int lineNumber)
				throws HintParseException {
			List<String> arguments= call.args().stream()
					.map(argument -> substitutions.getOrDefault(argument, argument)).toList();
			HintPredicateDefinition definition= definitions.get(call.name());
			if (definition == null) {
				return new GuardExpression.FunctionCall(call.name(), arguments);
			}
			if (arguments.size() != definition.parameters().size()) {
				throw new HintParseException("Predicate " + definition.signature() + " expects " //$NON-NLS-1$ //$NON-NLS-2$
						+ definition.parameters().size() + " arguments but received " + arguments.size(), lineNumber); //$NON-NLS-1$
			}
			if (stack.contains(definition.name())) {
				List<String> cycle= new ArrayList<>(stack);
				cycle.add(definition.name());
				throw new HintParseException("Recursive predicate cycle: " + String.join(" -> ", cycle), //$NON-NLS-1$ //$NON-NLS-2$
						definition.lineNumber());
			}
			Map<String, String> nested= new LinkedHashMap<>(mapCapacity(arguments.size()));
			for (int index= 0; index < arguments.size(); index++) {
				nested.put(definition.parameters().get(index), arguments.get(index));
			}
			stack.addLast(definition.name());
			GuardExpression expanded= expand(expressions.get(definition.name()), nested, stack,
					definition.lineNumber());
			stack.removeLast();
			return expanded;
		}

		private static void validateParameterContract(HintPredicateDefinition definition,
				GuardExpression expression) {
			Set<String> referenced= new LinkedHashSet<>();
			collectPlaceholders(expression, referenced);
			Set<String> undeclared= new LinkedHashSet<>(referenced);
			undeclared.removeAll(definition.parameters());
			if (!undeclared.isEmpty()) {
				throw new IllegalArgumentException("undeclared placeholder references " + undeclared); //$NON-NLS-1$
			}
			Set<String> unused= new LinkedHashSet<>(definition.parameters());
			unused.removeAll(referenced);
			if (!unused.isEmpty()) {
				throw new IllegalArgumentException("unused parameters " + unused); //$NON-NLS-1$
			}
		}

		private static void collectPlaceholders(GuardExpression expression, Set<String> target) {
			switch (expression) {
				case GuardExpression.And and -> {
					collectPlaceholders(and.left(), target);
					collectPlaceholders(and.right(), target);
				}
				case GuardExpression.Or or -> {
					collectPlaceholders(or.left(), target);
					collectPlaceholders(or.right(), target);
				}
				case GuardExpression.Not not -> collectPlaceholders(not.operand(), target);
				case GuardExpression.FunctionCall call -> call.args().stream()
						.filter(argument -> argument.startsWith("$")) //$NON-NLS-1$
						.forEach(target::add);
			}
		}
	}

	private static String format(GuardExpression expression) {
		return switch (expression) {
			case GuardExpression.And and -> '(' + format(and.left()) + " && " + format(and.right()) + ')'; //$NON-NLS-1$
			case GuardExpression.Or or -> '(' + format(or.left()) + " || " + format(or.right()) + ')'; //$NON-NLS-1$
			case GuardExpression.Not not -> "!(" + format(not.operand()) + ')'; //$NON-NLS-1$
			case GuardExpression.FunctionCall call -> call.name() + '(' + String.join(", ", call.args()) + ')'; //$NON-NLS-1$
		};
	}

	private static int nextPredicateDirective(String source, int start) {
		boolean inString= false;
		boolean escaped= false;
		boolean lineComment= false;
		boolean blockComment= false;
		for (int index= start; index < source.length(); index++) {
			char current= source.charAt(index);
			char next= index + 1 < source.length() ? source.charAt(index + 1) : '\0';
			if (lineComment) {
				if (current == '\n' || current == '\r') {
					lineComment= false;
				}
			} else if (blockComment) {
				if (current == '*' && next == '/') {
					blockComment= false;
					index++;
				}
			} else if (inString) {
				if (escaped) {
					escaped= false;
				} else if (current == '\\') {
					escaped= true;
				} else if (current == '"') {
					inString= false;
				}
			} else if (current == '"') {
				inString= true;
			} else if (current == '/' && next == '/') {
				lineComment= true;
				index++;
			} else if (current == '/' && next == '*') {
				blockComment= true;
				index++;
			} else if (source.startsWith("<!" + PREDICATE_KEYWORD, index)) { //$NON-NLS-1$
				int boundary= index + 2 + PREDICATE_KEYWORD.length();
				if (boundary < source.length() && Character.isWhitespace(source.charAt(boundary))) {
					return index;
				}
			}
		}
		return -1;
	}

	private static int directiveEnd(String source, int start) {
		return findOutsideString(source, ">", start); //$NON-NLS-1$
	}

	private static int findOutsideString(String source, String token, int start) {
		boolean inString= false;
		boolean escaped= false;
		for (int index= start; index + token.length() <= source.length(); index++) {
			char current= source.charAt(index);
			if (inString) {
				if (escaped) {
					escaped= false;
				} else if (current == '\\') {
					escaped= true;
				} else if (current == '"') {
					inString= false;
				}
			} else if (current == '"') {
				inString= true;
			} else if (source.startsWith(token, index)) {
				return index;
			}
		}
		return -1;
	}

	private static int commentStart(String line) {
		return findOutsideString(line, "//", 0); //$NON-NLS-1$
	}

	private static void blankExceptLineDelimiters(StringBuilder source, int start, int end) {
		for (int index= start; index <= end; index++) {
			char character= source.charAt(index);
			if (character != '\n' && character != '\r') {
				source.setCharAt(index, ' ');
			}
		}
	}

	private static int lineNumber(String source, int offset) {
		int line= 1;
		for (int index= 0; index < Math.min(offset, source.length()); index++) {
			if (source.charAt(index) == '\n') {
				line++;
			}
		}
		return line;
	}

	private static int mapCapacity(int expectedSize) {
		return Math.max(1, (int) Math.ceil(expectedSize / 0.75d));
	}

	private static HintParseException parseFailure(String message, int line, Throwable cause) {
		HintParseException failure= new HintParseException(message, line);
		failure.initCause(cause);
		return failure;
	}
}
