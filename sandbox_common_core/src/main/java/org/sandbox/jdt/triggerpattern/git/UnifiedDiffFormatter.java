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
package org.sandbox.jdt.triggerpattern.git;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.HistogramDiff;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.util.QuotedString;

/**
 * Formats text changes as a unified diff.
 */
public final class UnifiedDiffFormatter {

	private static final int CONTEXT_LINES= 3;

	private UnifiedDiffFormatter() {
		// Utility class.
	}

	/**
	 * Formats the given file content change as a unified diff.
	 *
	 * @param relativePath the project-relative file path
	 * @param before the original file bytes
	 * @param after the updated file bytes
	 * @return the unified diff bytes, or an empty array when the content is unchanged
	 * @throws IOException if formatting fails
	 */
	public static byte[] format(String relativePath, byte[] before, byte[] after) throws IOException {
		Objects.requireNonNull(relativePath, "relativePath"); //$NON-NLS-1$
		if (relativePath.isBlank()) {
			throw new IllegalArgumentException("relativePath must not be blank"); //$NON-NLS-1$
		}
		byte[] original= before == null ? new byte[0] : before.clone();
		byte[] updated= after == null ? new byte[0] : after.clone();
		if (Arrays.equals(original, updated)) {
			return new byte[0];
		}
		RawText beforeText= new RawText(original);
		RawText afterText= new RawText(updated);
		EditList edits= new HistogramDiff().diff(RawTextComparator.DEFAULT, beforeText, afterText);
		if (edits.isEmpty()) {
			return new byte[0];
		}
		ByteArrayOutputStream output= new ByteArrayOutputStream();
		writeHeader(output, relativePath);
		try (DiffFormatter formatter= new DiffFormatter(output)) {
			formatter.setContext(CONTEXT_LINES);
			formatter.format(edits, beforeText, afterText);
			formatter.flush();
		}
		return output.toByteArray();
	}

	private static void writeHeader(ByteArrayOutputStream output, String relativePath) {
		output.writeBytes(("--- " + QuotedString.GIT_PATH.quote("a/" + relativePath) + '\n') //$NON-NLS-1$ //$NON-NLS-2$
				.getBytes(StandardCharsets.UTF_8));
		output.writeBytes(("+++ " + QuotedString.GIT_PATH.quote("b/" + relativePath) + '\n') //$NON-NLS-1$ //$NON-NLS-2$
				.getBytes(StandardCharsets.UTF_8));
	}
}
