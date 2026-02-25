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

import java.util.ArrayList;
import java.util.List;

import org.sandbox.mining.core.astdiff.AstNodeChange.ChangeType;

/**
 * Analyzes before/after code pairs and produces {@link AstDiff} results.
 *
 * <p>This implementation performs a line-level diff and classifies each change
 * as a REPLACE, INSERT, or DELETE. A future enhancement may use a full AST
 * parser (e.g. Eclipse JDT ASTParser) for deeper structural analysis.</p>
 */
public class AstDiffAnalyzer {

	/**
	 * Analyzes a single {@link CodeChangePair} and returns an {@link AstDiff}.
	 *
	 * @param pair the before/after code pair
	 * @return the diff result
	 */
	public AstDiff analyze(CodeChangePair pair) {
		if (pair == null) {
			return new AstDiff(null, List.of());
		}

		String before = pair.before() == null ? "" : pair.before().strip(); //$NON-NLS-1$
		String after = pair.after() == null ? "" : pair.after().strip(); //$NON-NLS-1$

		if (before.equals(after)) {
			return new AstDiff(pair, List.of());
		}

		List<AstNodeChange> changes = computeChanges(before, after);
		return new AstDiff(pair, changes);
	}

	/**
	 * Analyzes multiple pairs in batch.
	 *
	 * @param pairs the code change pairs
	 * @return list of diff results
	 */
	public List<AstDiff> analyzeBatch(List<CodeChangePair> pairs) {
		if (pairs == null) {
			return List.of();
		}
		return pairs.stream().map(this::analyze).toList();
	}

	private List<AstNodeChange> computeChanges(String before, String after) {
		List<AstNodeChange> changes = new ArrayList<>();

		String[] beforeLines = before.split("\\R"); //$NON-NLS-1$
		String[] afterLines = after.split("\\R"); //$NON-NLS-1$

		if (before.isEmpty() && !after.isEmpty()) {
			changes.add(new AstNodeChange(ChangeType.INSERT, inferNodeType(after), "", after)); //$NON-NLS-1$
			return changes;
		}
		if (!before.isEmpty() && after.isEmpty()) {
			changes.add(new AstNodeChange(ChangeType.DELETE, inferNodeType(before), before, "")); //$NON-NLS-1$
			return changes;
		}

		// Simple line-by-line comparison for REPLACE detection
		int maxLines = Math.max(beforeLines.length, afterLines.length);
		List<String> removedLines = new ArrayList<>();
		List<String> addedLines = new ArrayList<>();

		for (int i = 0; i < maxLines; i++) {
			String bLine = i < beforeLines.length ? beforeLines[i].strip() : null;
			String aLine = i < afterLines.length ? afterLines[i].strip() : null;

			if (bLine != null && aLine != null) {
				if (!bLine.equals(aLine)) {
					removedLines.add(bLine);
					addedLines.add(aLine);
				}
			} else if (bLine != null) {
				removedLines.add(bLine);
			} else if (aLine != null) {
				addedLines.add(aLine);
			}
		}

		if (!removedLines.isEmpty() && !addedLines.isEmpty()) {
			String removedText = String.join("\n", removedLines); //$NON-NLS-1$
			String addedText = String.join("\n", addedLines); //$NON-NLS-1$
			changes.add(new AstNodeChange(ChangeType.REPLACE,
					inferNodeType(removedText), removedText, addedText));
		} else if (!removedLines.isEmpty()) {
			String removedText = String.join("\n", removedLines); //$NON-NLS-1$
			changes.add(new AstNodeChange(ChangeType.DELETE,
					inferNodeType(removedText), removedText, "")); //$NON-NLS-1$
		} else if (!addedLines.isEmpty()) {
			String addedText = String.join("\n", addedLines); //$NON-NLS-1$
			changes.add(new AstNodeChange(ChangeType.INSERT,
					inferNodeType(addedText), "", addedText)); //$NON-NLS-1$
		}

		return changes;
	}

	/**
	 * Infers a rough node type from a code fragment.
	 * A full AST parser would provide precise type information.
	 */
	static String inferNodeType(String code) {
		if (code == null || code.isBlank()) {
			return "Unknown"; //$NON-NLS-1$
		}
		String trimmed = code.strip();
		if (trimmed.startsWith("import ")) { //$NON-NLS-1$
			return "ImportDeclaration"; //$NON-NLS-1$
		}
		if (trimmed.startsWith("new ")) { //$NON-NLS-1$
			return "ClassInstanceCreation"; //$NON-NLS-1$
		}
		if (trimmed.contains("(") && trimmed.contains(")")) { //$NON-NLS-1$ //$NON-NLS-2$
			return "MethodInvocation"; //$NON-NLS-1$
		}
		if (trimmed.startsWith("@")) { //$NON-NLS-1$
			return "Annotation"; //$NON-NLS-1$
		}
		if (trimmed.startsWith("//") || trimmed.startsWith("/*")) { //$NON-NLS-1$ //$NON-NLS-2$
			return "Comment"; //$NON-NLS-1$
		}
		return "Statement"; //$NON-NLS-1$
	}
}
