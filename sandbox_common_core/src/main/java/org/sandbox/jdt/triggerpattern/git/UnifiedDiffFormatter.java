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
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.HistogramDiff;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;

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
	 * @return the unified diff, or an empty string when the content is unchanged
	 */
	public static String format(String relativePath, byte[] before, byte[] after) {
		Objects.requireNonNull(relativePath, "relativePath"); //$NON-NLS-1$
		if (relativePath.isBlank()) {
			throw new IllegalArgumentException("relativePath must not be blank"); //$NON-NLS-1$
		}
		byte[] original= before == null ? new byte[0] : before.clone();
		byte[] updated= after == null ? new byte[0] : after.clone();
		if (Arrays.equals(original, updated)) {
			return ""; //$NON-NLS-1$
		}
		RawText beforeText= new RawText(original);
		RawText afterText= new RawText(updated);
		EditList edits= new HistogramDiff().diff(RawTextComparator.DEFAULT, beforeText, afterText);
		if (edits.isEmpty()) {
			return ""; //$NON-NLS-1$
		}
		ByteArrayOutputStream output= new ByteArrayOutputStream();
		writeHeader(output, relativePath);
		try (DiffFormatter formatter= new DiffFormatter(output)) {
			formatter.setContext(CONTEXT_LINES);
			formatter.format(edits, beforeText, afterText);
			formatter.flush();
		} catch (IOException e) {
			throw new UncheckedIOException("Cannot format unified diff for " + relativePath, e); //$NON-NLS-1$
		}
		return output.toString(StandardCharsets.UTF_8);
	}

	private static void writeHeader(ByteArrayOutputStream output, String relativePath) {
		output.writeBytes(("--- a/" + relativePath + '\n').getBytes(StandardCharsets.UTF_8)); //$NON-NLS-1$
		output.writeBytes(("+++ b/" + relativePath + '\n').getBytes(StandardCharsets.UTF_8)); //$NON-NLS-1$
	}
}
