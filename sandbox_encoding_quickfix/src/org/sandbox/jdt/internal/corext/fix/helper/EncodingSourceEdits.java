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
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.Comparator;
import java.util.List;

/** Offset-based source edits; never searches Java source for matching literal text. */
final class EncodingSourceEdits {

	record Edit(int offset, int length, String text) {
	}

	private EncodingSourceEdits() {
	}

	static String apply(String buffer, int start, int length, List<Edit> edits) {
		StringBuilder result= new StringBuilder(buffer.substring(start, start + length));
		int previousStart= start + length;
		for (Edit edit : edits.stream().sorted(Comparator.comparingInt(Edit::offset).reversed()).toList()) {
			if (edit.offset() < start || edit.length() < 0 || edit.offset() + edit.length() > previousStart) {
				throw new IllegalArgumentException("Overlapping or out-of-range encoding edit"); //$NON-NLS-1$
			}
			result.replace(edit.offset() - start, edit.offset() - start + edit.length(), edit.text());
			previousStart= edit.offset();
		}
		return result.toString();
	}

	/** Edits are relative to a real line comment and refer only to scanner-proven NLS tags. */
	static String rewriteComment(String comment, List<Edit> edits) {
		StringBuilder result= new StringBuilder();
		int cursor= 0;
		for (Edit edit : edits.stream().sorted(Comparator.comparingInt(Edit::offset)).toList()) {
			if (edit.offset() < cursor || edit.length() < 0 || edit.offset() + edit.length() > comment.length()) {
				throw new IllegalArgumentException("Overlapping or out-of-range NLS tag edit"); //$NON-NLS-1$
			}
			result.append(comment, cursor, edit.offset());
			if (edit.text().isEmpty()) {
				while (!result.isEmpty() && isHorizontalSpace(result.charAt(result.length() - 1))) {
					result.setLength(result.length() - 1);
				}
			} else {
				result.append(edit.text());
			}
			cursor= edit.offset() + edit.length();
		}
		result.append(comment, cursor, comment.length());
		if (result.toString().isBlank()) {
			return ""; //$NON-NLS-1$
		}
		// Removing the first tag must not turn remaining explanatory text into Java code.
		int first= 0;
		while (first < result.length() && isHorizontalSpace(result.charAt(first))) {
			first++;
		}
		String text= result.substring(first);
		return text.startsWith("//") ? text : "// " + text; //$NON-NLS-1$ //$NON-NLS-2$
	}

	/** Remove only the source node's common indentation, retaining continuation indentation. */
	static String relativeIndent(String buffer, int start, String source) {
		int lineStart= start;
		while (lineStart > 0 && buffer.charAt(lineStart - 1) != '\n' && buffer.charAt(lineStart - 1) != '\r') {
			lineStart--;
		}
		String indent= buffer.substring(lineStart, start);
		if (indent.isEmpty() || !indent.chars().allMatch(ch -> ch == ' ' || ch == '\t')) {
			return source;
		}
		StringBuilder result= new StringBuilder();
		for (int i= 0; i < source.length(); i++) {
			char ch= source.charAt(i);
			result.append(ch);
			if (ch == '\r' && i + 1 < source.length() && source.charAt(i + 1) == '\n') {
				result.append(source.charAt(++i));
			}
			if ((ch == '\n' || ch == '\r') && source.startsWith(indent, i + 1)) {
				i+= indent.length();
			}
		}
		return result.toString();
	}

	static boolean isHorizontalSpace(char ch) {
		return ch == ' ' || ch == '\t';
	}
}
