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
package org.sandbox.mining.core.astdiff;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generalizes concrete values in code fragments into TriggerPattern-style
 * placeholders such as {@code $v}, {@code $s}, or {@code $type}.
 *
 * <p>This enables the derivation of reusable transformation rules from
 * concrete before/after code pairs. For example:</p>
 * <pre>
 *   Before: new String(buf, "UTF-8")
 *   After:  new String(buf, StandardCharsets.UTF_8)
 *
 *   Generalized pattern:  new String($v, "UTF-8")
 *   Generalized replace:  new String($v, StandardCharsets.UTF_8)
 * </pre>
 */
public class PlaceholderGeneralizer {

	/** Pattern matching Java string literals: "..." */
	private static final Pattern STRING_LITERAL = Pattern.compile("\"([^\"]*)\""); //$NON-NLS-1$

	/** Pattern matching Java identifiers used as variable names */
	private static final Pattern IDENTIFIER = Pattern.compile("\\b([a-z][a-zA-Z0-9_]*)\\b"); //$NON-NLS-1$

	/** Pattern matching simple integer literals */
	private static final Pattern INT_LITERAL = Pattern.compile("\\b(\\d+)\\b"); //$NON-NLS-1$

	/**
	 * Result of the generalization process.
	 *
	 * @param pattern         the generalized before-pattern with placeholders
	 * @param replacement     the generalized after-pattern with placeholders
	 * @param placeholderMap  maps placeholder names to their original values
	 */
	public record GeneralizedPair(
			String pattern,
			String replacement,
			Map<String, String> placeholderMap) {
	}

	/**
	 * Generalizes a concrete before/after pair into a pattern/replacement pair
	 * with placeholders.
	 *
	 * <p>Variables that appear in both before and after code are replaced with
	 * numbered placeholders like {@code $v1}, {@code $v2}, etc. This preserves
	 * referential correspondence between the two sides.</p>
	 *
	 * @param before the code before the change
	 * @param after  the code after the change
	 * @return the generalized pair with placeholders
	 */
	public GeneralizedPair generalize(String before, String after) {
		if (before == null || after == null) {
			return new GeneralizedPair(
					before == null ? "" : before, //$NON-NLS-1$
					after == null ? "" : after, //$NON-NLS-1$
					Map.of());
		}

		Map<String, String> placeholderMap = new HashMap<>();
		String genBefore = before;
		String genAfter = after;

		// Find identifiers that appear in both before and after
		int counter = 1;
		Matcher beforeMatcher = IDENTIFIER.matcher(before);
		while (beforeMatcher.find()) {
			String identifier = beforeMatcher.group(1);
			if (isJavaKeyword(identifier)) {
				continue;
			}
			if (after.contains(identifier) && !placeholderMap.containsValue(identifier)) {
				String placeholder = "$v" + counter; //$NON-NLS-1$
				placeholderMap.put(placeholder, identifier);
				genBefore = replaceIdentifier(genBefore, identifier, placeholder);
				genAfter = replaceIdentifier(genAfter, identifier, placeholder);
				counter++;
			}
		}

		return new GeneralizedPair(genBefore, genAfter, placeholderMap);
	}

	/**
	 * Generalizes string literals that differ between before and after.
	 * Literals present in before but not in after get a {@code $s} placeholder.
	 *
	 * @param before the code before the change
	 * @param after  the code after the change
	 * @return the generalized pair with string placeholders
	 */
	public GeneralizedPair generalizeStrings(String before, String after) {
		if (before == null || after == null) {
			return new GeneralizedPair(
					before == null ? "" : before, //$NON-NLS-1$
					after == null ? "" : after, //$NON-NLS-1$
					Map.of());
		}

		Map<String, String> placeholderMap = new HashMap<>();
		String genBefore = before;

		int counter = 1;
		Matcher matcher = STRING_LITERAL.matcher(before);
		while (matcher.find()) {
			String literal = matcher.group(0);
			if (!after.contains(literal)) {
				String placeholder = "$s" + counter; //$NON-NLS-1$
				placeholderMap.put(placeholder, literal);
				genBefore = genBefore.replace(literal, placeholder);
				counter++;
			}
		}

		return new GeneralizedPair(genBefore, after, placeholderMap);
	}

	/**
	 * Generalizes integer literals that differ between before and after.
	 *
	 * @param before the code before the change
	 * @param after  the code after the change
	 * @return the generalized pair with integer placeholders
	 */
	public GeneralizedPair generalizeIntegers(String before, String after) {
		if (before == null || after == null) {
			return new GeneralizedPair(
					before == null ? "" : before, //$NON-NLS-1$
					after == null ? "" : after, //$NON-NLS-1$
					Map.of());
		}

		Map<String, String> placeholderMap = new HashMap<>();
		String genBefore = before;

		int counter = 1;
		Matcher matcher = INT_LITERAL.matcher(before);
		while (matcher.find()) {
			String literal = matcher.group(0);
			if (!after.contains(literal)) {
				String placeholder = "$n" + counter; //$NON-NLS-1$
				placeholderMap.put(placeholder, literal);
				genBefore = genBefore.replace(literal, placeholder);
				counter++;
			}
		}

		return new GeneralizedPair(genBefore, after, placeholderMap);
	}

	private String replaceIdentifier(String text, String identifier, String placeholder) {
		return text.replaceAll("\\b" + Pattern.quote(identifier) + "\\b", //$NON-NLS-1$ //$NON-NLS-2$
				Matcher.quoteReplacement(placeholder));
	}

	@SuppressWarnings("nls")
	private boolean isJavaKeyword(String word) {
		return switch (word) {
			case "abstract", "assert", "boolean", "break", "byte",
					"case", "catch", "char", "class", "const",
					"continue", "default", "do", "double", "else",
					"enum", "extends", "final", "finally", "float",
					"for", "goto", "if", "implements", "import",
					"instanceof", "int", "interface", "long", "native",
					"new", "package", "private", "protected", "public",
					"return", "short", "static", "strictfp", "super",
					"switch", "synchronized", "this", "throw", "throws",
					"transient", "try", "void", "volatile", "while",
					"true", "false", "null", "var", "yield", "record",
					"sealed", "permits", "when" -> true;
			default -> false;
		};
	}
}
