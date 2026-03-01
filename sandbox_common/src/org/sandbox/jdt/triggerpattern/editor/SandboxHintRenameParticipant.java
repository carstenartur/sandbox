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
package org.sandbox.jdt.triggerpattern.editor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

/**
 * Rename participant for {@code .sandbox-hint} files.
 *
 * <p>When a method in a {@code <? ?>} block is renamed, all
 * {@code :: guardName()} references in the same file are updated.</p>
 *
 * <p>Implemented as {@link IDocument}-based text search (no AST needed):
 * finds all occurrences of {@code :: oldName(} in the default content type
 * and replaces them with {@code :: newName(}.</p>
 *
 * @since 1.5.0
 */
public class SandboxHintRenameParticipant {

	/**
	 * Computes text edits for renaming a guard function reference.
	 *
	 * <p>Finds all occurrences of {@code :: oldName(} in the document
	 * and replaces them with {@code :: newName(}. References in comments
	 * (lines starting with {@code //}) are not changed.</p>
	 *
	 * @param document the document to search
	 * @param oldName  the old guard function name
	 * @param newName  the new guard function name
	 * @return a {@link TextEdit} with all replacements, or {@code null} if no changes needed
	 */
	public TextEdit computeRenameEdits(IDocument document, String oldName, String newName) {
		if (document == null || oldName == null || newName == null || oldName.equals(newName)) {
			return null;
		}

		String content;
		try {
			content = document.get();
		} catch (Exception e) {
			return null;
		}

		MultiTextEdit multiEdit = new MultiTextEdit();

		// Pattern: "::" followed by optional whitespace, then oldName followed by "("
		String regex = "::\\s*" + Pattern.quote(oldName) + "\\("; //$NON-NLS-1$ //$NON-NLS-2$
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(content);

		while (matcher.find()) {
			// Check if the line is a comment
			try {
				int lineNum = document.getLineOfOffset(matcher.start());
				int lineOffset = document.getLineOffset(lineNum);
				String linePrefix = content.substring(lineOffset, matcher.start()).trim();
				if (linePrefix.startsWith("//")) { //$NON-NLS-1$
					continue; // Skip comment lines
				}
			} catch (BadLocationException e) {
				continue;
			}

			// Replace the matched portion: ":: oldName(" → ":: newName("
			String matched = matcher.group();
			String replacement = matched.replace(oldName, newName);
			multiEdit.addChild(new ReplaceEdit(matcher.start(), matched.length(), replacement));
		}

		if (!multiEdit.hasChildren()) {
			return null;
		}
		return multiEdit;
	}
}
