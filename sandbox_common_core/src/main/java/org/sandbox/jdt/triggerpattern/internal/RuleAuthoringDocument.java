/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.internal;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The complete, unmodified text of an authored rule file. The wizard, clipboard,
 * project preview and file writer use this same document instead of extracting
 * a first rule or a first replacement from it.
 *
 * <p>Parsing is deliberately separate: successful parsing does not prove that a
 * transformation preserves behavior.</p>
 *
 * @since 1.6.0
 */
public record RuleAuthoringDocument(String content) {

	private static final Pattern METADATA = Pattern.compile(
			"(?m)^[\\t ]*<!([A-Za-z][A-Za-z0-9-]*):[^>]*>[\\t ]*(?:\\r\\n|\\r|\\n|$)"); //$NON-NLS-1$

	public RuleAuthoringDocument {
		Objects.requireNonNull(content, "content"); //$NON-NLS-1$
	}

	/** Builds an exact before/after draft; names and literals are never inferred. */
	public static RuleAuthoringDocument fromChange(String before, String after, String guard) {
		Objects.requireNonNull(before, "before"); //$NON-NLS-1$
		Objects.requireNonNull(after, "after"); //$NON-NLS-1$
		if (containsJavaTextBlock(before) || containsJavaTextBlock(after)) {
			throw new IllegalArgumentException(textBlockDiagnostic());
		}
		StringBuilder dsl = new StringBuilder("<!binding-policy: required>\n\n"); //$NON-NLS-1$
		dsl.append(before);
		if (guard != null && !guard.isBlank()) {
			if (!endsWithLineBreak(dsl)) {
				dsl.append('\n');
			}
			dsl.append(":: ").append(guard.strip()); //$NON-NLS-1$
		}
		if (!endsWithLineBreak(dsl)) {
			dsl.append('\n');
		}
		if (!after.isEmpty()) {
			dsl.append("=> ").append(after); //$NON-NLS-1$
			if (!endsWithLineBreak(dsl)) {
				dsl.append('\n');
			}
		}
		dsl.append(";;\n"); //$NON-NLS-1$
		return new RuleAuthoringDocument(dsl.toString());
	}

	/**
	 * Changes only explicitly edited metadata. All other bytes, including unknown
	 * directives, comments, multiline patterns and alternative guards, survive.
	 */
	public RuleAuthoringDocument withMetadata(Map<String, String> overrides) {
		if (overrides.isEmpty()) {
			return this;
		}
		Map<String, String> remaining = new LinkedHashMap<>(overrides);
		remaining.forEach((key, value) -> {
			if (!key.matches("[A-Za-z][A-Za-z0-9-]*") || value == null //$NON-NLS-1$
					|| value.indexOf('>') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
				throw new IllegalArgumentException("Metadata must fit in a single directive"); //$NON-NLS-1$
			}
		});
		Matcher matcher = METADATA.matcher(content);
		StringBuilder result = new StringBuilder();
		while (matcher.find()) {
			String key = matcher.group(1);
			if (overrides.containsKey(key) && isActiveDirective(content, matcher.start())) {
				String original = matcher.group();
				String lineEnd = original.endsWith("\r\n") ? "\r\n" //$NON-NLS-1$ //$NON-NLS-2$
						: original.endsWith("\r") ? "\r" : original.endsWith("\n") ? "\n" : ""; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
				String value = overrides.get(key);
				remaining.remove(key);
				matcher.appendReplacement(result, Matcher.quoteReplacement(value.isEmpty() ? "" //$NON-NLS-1$
						: "<!" + key + ": " + value + ">" + lineEnd)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}
		}
		matcher.appendTail(result);
		String lineEnd = content.contains("\r\n") ? "\r\n" : "\n"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		StringBuilder prefix = new StringBuilder();
		remaining.forEach((key, value) -> {
			if (!value.isEmpty()) {
				prefix.append("<!").append(key).append(": ").append(value).append('>').append(lineEnd); //$NON-NLS-1$ //$NON-NLS-2$
			}
		});
		return new RuleAuthoringDocument(prefix.append(result).toString());
	}

	/**
	 * Finds the actual saved-to-editor change and expands it to complete lines.
	 * Several edits produce one visible before/after range which the author can
	 * narrow in the wizard; unchanged documents never create a made-up example.
	 */
	public static Optional<ChangeExample> changedLines(String before, String after) {
		Objects.requireNonNull(before, "before"); //$NON-NLS-1$
		Objects.requireNonNull(after, "after"); //$NON-NLS-1$
		int prefix = 0;
		int shortest = Math.min(before.length(), after.length());
		while (prefix < shortest && before.charAt(prefix) == after.charAt(prefix)) {
			prefix++;
		}
		if (prefix == before.length() && prefix == after.length()) {
			return Optional.empty();
		}
		int suffix = 0;
		while (suffix < shortest - prefix
				&& before.charAt(before.length() - suffix - 1) == after.charAt(after.length() - suffix - 1)) {
			suffix++;
		}
		int start = prefix;
		while (start > 0 && before.charAt(start - 1) != '\n' && before.charAt(start - 1) != '\r') {
			start--;
		}
		int beforeEnd = lineEnd(before, before.length() - suffix);
		int afterEnd = lineEnd(after, after.length() - suffix);
		return Optional.of(new ChangeExample(before.substring(start, beforeEnd), after.substring(start, afterEnd)));
	}

	private static int lineEnd(String source, int offset) {
		// If a changed range already ends at a line boundary, keep following lines out.
		if (offset > 0 && source.charAt(offset - 1) == '\n') {
			return offset;
		}
		while (offset < source.length() && source.charAt(offset) != '\r' && source.charAt(offset) != '\n') {
			offset++;
		}
		if (offset < source.length() && source.charAt(offset) == '\r') {
			offset++;
		}
		if (offset < source.length() && source.charAt(offset) == '\n') {
			offset++;
		}
		return offset;
	}

	private static boolean endsWithLineBreak(CharSequence value) {
		return !value.isEmpty() && (value.charAt(value.length() - 1) == '\n' || value.charAt(value.length() - 1) == '\r');
	}

	/** Detects text-block delimiters outside comments and ordinary Java literals. */
	public static boolean containsJavaTextBlock(String code) {
		int offset = -3;
		while ((offset = code.indexOf("\"\"\"", offset + 3)) >= 0) { //$NON-NLS-1$
			if (isActiveDirective(code, offset)) {
				return true;
			}
		}
		return false;
	}

	/** A bounded fail-closed limitation of the current DSL whitespace handling. */
	public static String textBlockDiagnostic() {
		return "Java text blocks are not supported in rule patterns or replacements because their whitespace cannot yet be preserved safely"; //$NON-NLS-1$
	}

	private static boolean isActiveDirective(String source, int offset) {
		boolean blockComment = false;
		boolean lineComment = false;
		boolean embeddedJava = false;
		boolean textBlock = false;
		char quote = 0;
		for (int i = 0; i < offset; i++) {
			char ch = source.charAt(i);
			if (lineComment) {
				lineComment = ch != '\r' && ch != '\n';
			} else if (blockComment) {
				if (source.startsWith("*/", i)) { //$NON-NLS-1$
					blockComment = false;
					i++;
				}
			} else if (textBlock) {
				if (source.startsWith("\"\"\"", i)) { //$NON-NLS-1$
					textBlock = false;
					i += 2;
				} else if (ch == '\\') {
					i++;
				}
			} else if (quote != 0) {
				if (ch == '\\') {
					i++;
				} else if (ch == quote) {
					quote = 0;
				}
			} else if (source.startsWith("<?", i)) { //$NON-NLS-1$
				embeddedJava = true;
				i++;
			} else if (source.startsWith("?>", i)) { //$NON-NLS-1$
				embeddedJava = false;
				i++;
			} else if (source.startsWith("/*", i)) { //$NON-NLS-1$
				blockComment = true;
				i++;
			} else if (source.startsWith("//", i)) { //$NON-NLS-1$
				lineComment = true;
				i++;
			} else if (source.startsWith("<!", i)) { //$NON-NLS-1$
				// Metadata prose is not Java: apostrophes and paths there must not
				// start character literals or comments that hide later directives.
				int end = source.indexOf('>', i + 2);
				if (end >= 0 && end < offset) {
					i = end;
				}
			} else if (source.startsWith("\"\"\"", i)) { //$NON-NLS-1$
				textBlock = true;
				i += 2;
			} else if (ch == '\'' || ch == '"') {
				quote = ch;
			}
		}
		return !blockComment && !lineComment && !embeddedJava && !textBlock && quote == 0;
	}

	/** Editable evidence from a real source change, with all selected lines intact. */
	public record ChangeExample(String before, String after) {
	}
}
