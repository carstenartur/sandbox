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
package org.sandbox.jdt.triggerpattern.cleanup;

import java.util.Comparator;
import java.util.List;

/** Scanner-positioned NLS tag edits that retain explanatory comment text. */
final class EncodingSourceEdits {

	record Edit(int offset, int length, String text) {
	}

	private EncodingSourceEdits() {
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

	static boolean isHorizontalSpace(char ch) {
		return ch == ' ' || ch == '\t';
	}
}
