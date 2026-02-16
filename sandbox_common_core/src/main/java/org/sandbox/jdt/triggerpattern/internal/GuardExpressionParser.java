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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.internal;

import java.util.ArrayList;
import java.util.List;

import org.sandbox.jdt.triggerpattern.api.GuardExpression;

/**
 * Recursive descent parser for guard expressions.
 * 
 * <p>Syntax:</p>
 * <pre>
 * expr            = or_expr
 * or_expr         = and_expr ('||' and_expr)*
 * and_expr        = unary_expr ('&amp;&amp;' unary_expr)*
 * unary_expr      = '!' unary_expr | primary
 * primary         = '(' expr ')' | instanceof_expr | function_call
 * function_call   = IDENTIFIER '(' arg_list ')'
 * instanceof_expr = PLACEHOLDER 'instanceof' TYPE
 * arg_list        = (arg (',' arg)*)?
 * arg             = PLACEHOLDER | IDENTIFIER | NUMBER
 * </pre>
 * 
 * <p>Examples:</p>
 * <ul>
 *   <li>{@code sourceVersionGE(11)}</li>
 *   <li>{@code $x instanceof String}</li>
 *   <li>{@code $x instanceof String && sourceVersionGE(11)}</li>
 *   <li>{@code !isStatic($x)}</li>
 *   <li>{@code ($a || $b) && $c}</li>
 * </ul>
 * 
 * @since 1.3.2
 */
public final class GuardExpressionParser {
	
	private static final class ParseState {
		final String input;
		int pos;
		
		ParseState(String input) {
			this.input = input;
			this.pos = 0;
		}
	}
	
	/**
	 * Parses a guard expression string into a {@link GuardExpression} AST.
	 * 
	 * @param guardText the guard expression text
	 * @return the parsed guard expression
	 * @throws IllegalArgumentException if the expression cannot be parsed
	 */
	public GuardExpression parse(String guardText) {
		if (guardText == null || guardText.isBlank()) {
			throw new IllegalArgumentException("Guard expression cannot be null or blank"); //$NON-NLS-1$
		}
		ParseState state = new ParseState(guardText.trim());
		
		GuardExpression expr = parseOrExpr(state);
		skipWhitespace(state);
		if (state.pos < state.input.length()) {
			throw new IllegalArgumentException(
					"Unexpected character at position " + state.pos + ": '" + state.input.charAt(state.pos) + "'"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
		return expr;
	}
	
	/**
	 * or_expr = and_expr ('||' and_expr)*
	 */
	private GuardExpression parseOrExpr(ParseState state) {
		GuardExpression left = parseAndExpr(state);
		
		while (matchToken(state, "||")) { //$NON-NLS-1$
			GuardExpression right = parseAndExpr(state);
			left = new GuardExpression.Or(left, right);
		}
		
		return left;
	}
	
	/**
	 * and_expr = unary_expr ('&&' unary_expr)*
	 */
	private GuardExpression parseAndExpr(ParseState state) {
		GuardExpression left = parseUnaryExpr(state);
		
		while (matchToken(state, "&&")) { //$NON-NLS-1$
			GuardExpression right = parseUnaryExpr(state);
			left = new GuardExpression.And(left, right);
		}
		
		return left;
	}
	
	/**
	 * unary_expr = '!' unary_expr | primary
	 */
	private GuardExpression parseUnaryExpr(ParseState state) {
		skipWhitespace(state);
		
		if (state.pos < state.input.length() && state.input.charAt(state.pos) == '!') {
			state.pos++;
			GuardExpression operand = parseUnaryExpr(state);
			return new GuardExpression.Not(operand);
		}
		
		return parsePrimary(state);
	}
	
	/**
	 * primary = '(' expr ')' | instanceof_expr | function_call
	 */
	private GuardExpression parsePrimary(ParseState state) {
		skipWhitespace(state);
		
		if (state.pos >= state.input.length()) {
			throw new IllegalArgumentException("Unexpected end of expression"); //$NON-NLS-1$
		}
		
		// Parenthesized expression
		if (state.input.charAt(state.pos) == '(') {
			state.pos++;
			GuardExpression expr = parseOrExpr(state);
			skipWhitespace(state);
			if (state.pos >= state.input.length() || state.input.charAt(state.pos) != ')') {
				throw new IllegalArgumentException("Expected ')' at position " + state.pos); //$NON-NLS-1$
			}
			state.pos++;
			return expr;
		}
		
		// Placeholder: might be instanceof expression or matchesAny($x) style
		if (state.input.charAt(state.pos) == '$') {
			String placeholder = readToken(state);
			skipWhitespace(state);
			
			// Check for instanceof
			if (matchKeyword(state, "instanceof")) { //$NON-NLS-1$
				skipWhitespace(state);
				String typeName = readToken(state);
				// Handle array types: Type[]
				skipWhitespace(state);
				if (state.pos + 1 < state.input.length() && state.input.charAt(state.pos) == '[' && state.input.charAt(state.pos + 1) == ']') {
					typeName = typeName + "[]"; //$NON-NLS-1$
					state.pos += 2;
				}
				return new GuardExpression.FunctionCall("instanceof", List.of(placeholder, typeName)); //$NON-NLS-1$
			}
			
			// Standalone placeholder treated as matchesAny($placeholder)
			return new GuardExpression.FunctionCall("matchesAny", List.of(placeholder)); //$NON-NLS-1$
		}
		
		// Function call: IDENTIFIER '(' arg_list ')'
		String name = readToken(state);
		if (name.isEmpty()) {
			throw new IllegalArgumentException("Expected identifier at position " + state.pos); //$NON-NLS-1$
		}
		
		skipWhitespace(state);
		if (state.pos < state.input.length() && state.input.charAt(state.pos) == '(') {
			state.pos++;
			List<String> args = parseArgList(state);
			skipWhitespace(state);
			if (state.pos >= state.input.length() || state.input.charAt(state.pos) != ')') {
				throw new IllegalArgumentException("Expected ')' at position " + state.pos); //$NON-NLS-1$
			}
			state.pos++;
			return new GuardExpression.FunctionCall(name, args);
		}
		
		// Bare identifier treated as zero-arg function call
		return new GuardExpression.FunctionCall(name, List.of());
	}
	
	/**
	 * arg_list = (arg (',' arg)*)?
	 */
	private List<String> parseArgList(ParseState state) {
		List<String> args = new ArrayList<>();
		skipWhitespace(state);
		
		if (state.pos < state.input.length() && state.input.charAt(state.pos) == ')') {
			return args;
		}
		
		args.add(readArg(state));
		
		while (state.pos < state.input.length()) {
			skipWhitespace(state);
			if (state.pos >= state.input.length() || state.input.charAt(state.pos) != ',') {
				break;
			}
			state.pos++;
			args.add(readArg(state));
		}
		
		return args;
	}
	
	/**
	 * Reads a single argument (placeholder, identifier, or number).
	 */
	private String readArg(ParseState state) {
		skipWhitespace(state);
		if (state.pos >= state.input.length()) {
			throw new IllegalArgumentException("Expected argument at position " + state.pos); //$NON-NLS-1$
		}
		return readToken(state);
	}
	
	/**
	 * Reads a token (identifier, placeholder with $ prefix, number, or quoted string literal).
	 * 
	 * <p>Quoted string literals are returned with their surrounding quotes preserved
	 * (e.g., {@code "foo"} is returned as {@code "foo"}). Guard function implementations
	 * use {@code stripQuotes()} during evaluation to extract the literal value.</p>
	 */
	private String readToken(ParseState state) {
		skipWhitespace(state);
		if (state.pos >= state.input.length()) {
			return ""; //$NON-NLS-1$
		}
		
		int start = state.pos;
		char c = state.input.charAt(state.pos);
		
		// Quoted string literal: "..."
		if (c == '"') {
			state.pos++;
			StringBuilder sb = new StringBuilder();
			sb.append('"');
			while (state.pos < state.input.length() && state.input.charAt(state.pos) != '"') {
				if (state.input.charAt(state.pos) == '\\' && state.pos + 1 < state.input.length()) {
					char escaped = state.input.charAt(state.pos + 1);
					// Handle escaped quote: \" becomes " in the output
					if (escaped == '"') {
						sb.append('"');
						state.pos += 2;
						continue;
					}
					// Preserve other escape sequences as-is
					sb.append('\\');
					sb.append(escaped);
					state.pos += 2;
					continue;
				}
				sb.append(state.input.charAt(state.pos));
				state.pos++;
			}
			if (state.pos >= state.input.length()) {
				throw new IllegalArgumentException(
						"Unterminated string literal starting at position " + start); //$NON-NLS-1$
			}
			sb.append('"');
			state.pos++; // consume closing quote
			return sb.toString();
		}
		
		// Placeholder: $identifier
		if (c == '$') {
			state.pos++;
			while (state.pos < state.input.length() && isIdentifierPart(state.input.charAt(state.pos))) {
				state.pos++;
			}
			// Handle multi-placeholder ending with $
			if (state.pos < state.input.length() && state.input.charAt(state.pos) == '$') {
				state.pos++;
			}
			return state.input.substring(start, state.pos);
		}
		
		// Number
		if (Character.isDigit(c)) {
			while (state.pos < state.input.length() && (Character.isDigit(state.input.charAt(state.pos)) || state.input.charAt(state.pos) == '.')) {
				state.pos++;
			}
			return state.input.substring(start, state.pos);
		}
		
		// Identifier (including qualified names like java.lang.String)
		if (isIdentifierStart(c)) {
			while (state.pos < state.input.length() && (isIdentifierPart(state.input.charAt(state.pos)) || state.input.charAt(state.pos) == '.')) {
				state.pos++;
			}
			return state.input.substring(start, state.pos);
		}
		
		throw new IllegalArgumentException(
				"Unexpected character at position " + state.pos + ": '" + c + "'"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}
	
	/**
	 * Tries to match and consume a two-character token.
	 */
	private boolean matchToken(ParseState state, String token) {
		skipWhitespace(state);
		if (state.pos + token.length() <= state.input.length()
				&& state.input.substring(state.pos, state.pos + token.length()).equals(token)) {
			state.pos += token.length();
			return true;
		}
		return false;
	}
	
	/**
	 * Tries to match and consume a keyword (must be followed by non-identifier char).
	 */
	private boolean matchKeyword(ParseState state, String keyword) {
		int savedPos = state.pos;
		if (state.pos + keyword.length() <= state.input.length()
				&& state.input.substring(state.pos, state.pos + keyword.length()).equals(keyword)) {
			int afterKeyword = state.pos + keyword.length();
			if (afterKeyword >= state.input.length() || !isIdentifierPart(state.input.charAt(afterKeyword))) {
				state.pos += keyword.length();
				return true;
			}
		}
		state.pos = savedPos;
		return false;
	}
	
	private void skipWhitespace(ParseState state) {
		while (state.pos < state.input.length() && Character.isWhitespace(state.input.charAt(state.pos))) {
			state.pos++;
		}
	}
	
	private boolean isIdentifierStart(char c) {
		return Character.isJavaIdentifierStart(c);
	}
	
	private boolean isIdentifierPart(char c) {
		return Character.isJavaIdentifierPart(c);
	}
}
