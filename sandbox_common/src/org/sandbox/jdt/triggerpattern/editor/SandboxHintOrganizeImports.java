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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

/**
 * Organizes imports in {@code <? ?>} blocks of a {@code .sandbox-hint} file.
 *
 * <p>Strategy:</p>
 * <ol>
 *   <li>Find all {@code <? ?>} blocks in the document</li>
 *   <li>Extract import statements from each block</li>
 *   <li>Sort imports alphabetically</li>
 *   <li>Remove duplicate imports</li>
 *   <li>Write organized imports back into the {@code <? ?>} block</li>
 * </ol>
 *
 * <p>Imports outside of {@code <? ?>} blocks are not changed.</p>
 *
 * @since 1.5.0
 */
public class SandboxHintOrganizeImports {

	private static final Pattern IMPORT_PATTERN =
			Pattern.compile("^\\s*import\\s+(?:static\\s+)?[\\w.]+(?:\\.\\*)?\\s*;\\s*$"); //$NON-NLS-1$

	private static final Pattern EMBEDDED_BLOCK_PATTERN =
			Pattern.compile("<\\?([\\s\\S]*?)\\?>"); //$NON-NLS-1$

	/**
	 * Organizes imports in all {@code <? ?>} blocks of the document.
	 *
	 * @param document the document to organize imports in
	 * @return a {@link TextEdit} with all changes, or {@code null} if no changes needed
	 */
	public TextEdit organizeImports(IDocument document) {
		if (document == null) {
			return null;
		}

		String content;
		try {
			content = document.get();
		} catch (RuntimeException e) {
			return null;
		}

		MultiTextEdit multiEdit = new MultiTextEdit();
		Matcher blockMatcher = EMBEDDED_BLOCK_PATTERN.matcher(content);

		while (blockMatcher.find()) {
			int blockStart = blockMatcher.start(1);
			String blockContent = blockMatcher.group(1);

			TextEdit blockEdit = organizeBlockImports(blockContent, blockStart);
			if (blockEdit != null) {
				multiEdit.addChild(blockEdit);
			}
		}

		if (!multiEdit.hasChildren()) {
			return null;
		}
		return multiEdit;
	}

	/**
	 * Organizes imports within a single {@code <? ?>} block.
	 */
	private TextEdit organizeBlockImports(String blockContent, int blockStartOffset) {
		String[] lines = blockContent.split("\\n"); //$NON-NLS-1$
		List<String> imports = new ArrayList<>();
		List<String> nonImports = new ArrayList<>();
		int importRegionStart = -1;
		int importRegionEnd = -1;
		int currentOffset = 0;

		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			if (IMPORT_PATTERN.matcher(line).matches()) {
				String importText = line.trim();
				if (!imports.contains(importText)) {
					imports.add(importText);
				}
				if (importRegionStart == -1) {
					importRegionStart = currentOffset;
				}
				importRegionEnd = currentOffset + line.length();
			}
			currentOffset += line.length() + 1; // +1 for newline
		}

		if (imports.isEmpty() || importRegionStart == -1) {
			return null;
		}

		// Sort imports alphabetically
		imports.sort(String::compareTo);

		String organizedImports = String.join("\n", imports); //$NON-NLS-1$
		String originalRegion = blockContent.substring(importRegionStart, importRegionEnd);

		if (organizedImports.equals(originalRegion)) {
			return null; // No changes needed
		}

		return new ReplaceEdit(blockStartOffset + importRegionStart,
				importRegionEnd - importRegionStart, organizedImports);
	}
}
