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
	
	private String input;
	private int pos;
	
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
		this.input = guardText.trim();
		this.pos = 0;
		
		GuardExpression expr = parseOrExpr();
		skipWhitespace();
		if (pos < input.length()) {
			throw new IllegalArgumentException(
					"Unexpected character at position " + pos + ": '" + input.charAt(pos) + "'"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
		return expr;
	}
	
	/**
	 * or_expr = and_expr ('||' and_expr)*
	 */
	private GuardExpression parseOrExpr() {
		GuardExpression left = parseAndExpr();
		
		while (matchToken("||")) { //$NON-NLS-1$
			GuardExpression right = parseAndExpr();
			left = new GuardExpression.Or(left, right);
		}
		
		return left;
	}
	
	/**
	 * and_expr = unary_expr ('&&' unary_expr)*
	 */
	private GuardExpression parseAndExpr() {
		GuardExpression left = parseUnaryExpr();
		
		while (matchToken("&&")) { //$NON-NLS-1$
			GuardExpression right = parseUnaryExpr();
			left = new GuardExpression.And(left, right);
		}
		
		return left;
	}
	
	/**
	 * unary_expr = '!' unary_expr | primary
	 */
	private GuardExpression parseUnaryExpr() {
		skipWhitespace();
		
		if (pos < input.length() && input.charAt(pos) == '!') {
			pos++;
			GuardExpression operand = parseUnaryExpr();
			return new GuardExpression.Not(operand);
		}
		
		return parsePrimary();
	}
	
	/**
	 * primary = '(' expr ')' | instanceof_expr | function_call
	 */
	private GuardExpression parsePrimary() {
		skipWhitespace();
		
		if (pos >= input.length()) {
			throw new IllegalArgumentException("Unexpected end of expression"); //$NON-NLS-1$
		}
		
		// Parenthesized expression
		if (input.charAt(pos) == '(') {
			pos++;
			GuardExpression expr = parseOrExpr();
			skipWhitespace();
			if (pos >= input.length() || input.charAt(pos) != ')') {
				throw new IllegalArgumentException("Expected ')' at position " + pos); //$NON-NLS-1$
			}
			pos++;
			return expr;
		}
		
		// Placeholder: might be instanceof expression or matchesAny($x) style
		if (input.charAt(pos) == '$') {
			String placeholder = readToken();
			skipWhitespace();
			
			// Check for instanceof
			if (matchKeyword("instanceof")) { //$NON-NLS-1$
				skipWhitespace();
				String typeName = readToken();
				// Handle array types: Type[]
				skipWhitespace();
				if (pos + 1 < input.length() && input.charAt(pos) == '[' && input.charAt(pos + 1) == ']') {
					typeName = typeName + "[]"; //$NON-NLS-1$
					pos += 2;
				}
				return new GuardExpression.FunctionCall("instanceof", List.of(placeholder, typeName)); //$NON-NLS-1$
			}
			
			// Standalone placeholder treated as matchesAny($placeholder)
			return new GuardExpression.FunctionCall("matchesAny", List.of(placeholder)); //$NON-NLS-1$
		}
		
		// Function call: IDENTIFIER '(' arg_list ')'
		String name = readToken();
		if (name.isEmpty()) {
			throw new IllegalArgumentException("Expected identifier at position " + pos); //$NON-NLS-1$
		}
		
		skipWhitespace();
		if (pos < input.length() && input.charAt(pos) == '(') {
			pos++;
			List<String> args = parseArgList();
			skipWhitespace();
			if (pos >= input.length() || input.charAt(pos) != ')') {
				throw new IllegalArgumentException("Expected ')' at position " + pos); //$NON-NLS-1$
			}
			pos++;
			return new GuardExpression.FunctionCall(name, args);
		}
		
		// Bare identifier treated as zero-arg function call
		return new GuardExpression.FunctionCall(name, List.of());
	}
	
	/**
	 * arg_list = (arg (',' arg)*)?
	 */
	private List<String> parseArgList() {
		List<String> args = new ArrayList<>();
		skipWhitespace();
		
		if (pos < input.length() && input.charAt(pos) == ')') {
			return args;
		}
		
		args.add(readArg());
		
		while (pos < input.length()) {
			skipWhitespace();
			if (pos >= input.length() || input.charAt(pos) != ',') {
				break;
			}
			pos++;
			args.add(readArg());
		}
		
		return args;
	}
	
	/**
	 * Reads a single argument (placeholder, identifier, or number).
	 */
	private String readArg() {
		skipWhitespace();
		if (pos >= input.length()) {
			throw new IllegalArgumentException("Expected argument at position " + pos); //$NON-NLS-1$
		}
		return readToken();
	}
	
	/**
	 * Reads a token (identifier, placeholder with $ prefix, number, or quoted string literal).
	 * 
	 * <p>Quoted string literals are returned with their surrounding quotes preserved
	 * (e.g., {@code "foo"} is returned as {@code "foo"}). Guard function implementations
	 * use {@code stripQuotes()} during evaluation to extract the literal value.</p>
	 */
	private String readToken() {
		skipWhitespace();
		if (pos >= input.length()) {
			return ""; //$NON-NLS-1$
		}
		
		int start = pos;
		char c = input.charAt(pos);
		
		// Quoted string literal: "..."
		if (c == '"') {
			pos++;
			StringBuilder sb = new StringBuilder();
			sb.append('"');
			while (pos < input.length() && input.charAt(pos) != '"') {
				if (input.charAt(pos) == '\\' && pos + 1 < input.length()) {
					char escaped = input.charAt(pos + 1);
					// Handle escaped quote: \" becomes " in the output
					if (escaped == '"') {
						sb.append('"');
						pos += 2;
						continue;
					}
					// Preserve other escape sequences as-is
					sb.append('\\');
					sb.append(escaped);
					pos += 2;
					continue;
				}
				sb.append(input.charAt(pos));
				pos++;
			}
			if (pos >= input.length()) {
				throw new IllegalArgumentException(
						"Unterminated string literal starting at position " + start); //$NON-NLS-1$
			}
			sb.append('"');
			pos++; // consume closing quote
			return sb.toString();
		}
		
		// Placeholder: $identifier
		if (c == '$') {
			pos++;
			while (pos < input.length() && isIdentifierPart(input.charAt(pos))) {
				pos++;
			}
			// Handle multi-placeholder ending with $
			if (pos < input.length() && input.charAt(pos) == '$') {
				pos++;
			}
			return input.substring(start, pos);
		}
		
		// Number
		if (Character.isDigit(c)) {
			while (pos < input.length() && (Character.isDigit(input.charAt(pos)) || input.charAt(pos) == '.')) {
				pos++;
			}
			return input.substring(start, pos);
		}
		
		// Identifier (including qualified names like java.lang.String)
		if (isIdentifierStart(c)) {
			while (pos < input.length() && (isIdentifierPart(input.charAt(pos)) || input.charAt(pos) == '.')) {
				pos++;
			}
			return input.substring(start, pos);
		}
		
		throw new IllegalArgumentException(
				"Unexpected character at position " + pos + ": '" + c + "'"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}
	
	/**
	 * Tries to match and consume a two-character token.
	 */
	private boolean matchToken(String token) {
		skipWhitespace();
		if (pos + token.length() <= input.length()
				&& input.substring(pos, pos + token.length()).equals(token)) {
			pos += token.length();
			return true;
		}
		return false;
	}
	
	/**
	 * Tries to match and consume a keyword (must be followed by non-identifier char).
	 */
	private boolean matchKeyword(String keyword) {
		int savedPos = pos;
		if (pos + keyword.length() <= input.length()
				&& input.substring(pos, pos + keyword.length()).equals(keyword)) {
			int afterKeyword = pos + keyword.length();
			if (afterKeyword >= input.length() || !isIdentifierPart(input.charAt(afterKeyword))) {
				pos += keyword.length();
				return true;
			}
		}
		pos = savedPos;
		return false;
	}
	
	private void skipWhitespace() {
		while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
			pos++;
		}
	}
	
	private boolean isIdentifierStart(char c) {
		return Character.isJavaIdentifierStart(c);
	}
	
	private boolean isIdentifierPart(char c) {
		return Character.isJavaIdentifierPart(c);
	}
}
