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
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.views.mining;

import java.time.format.DateTimeFormatter;

import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;
import org.sandbox.jdt.triggerpattern.mining.analysis.CommitAnalysisResult.AnalysisStatus;

/**
 * Label provider for the commit table columns.
 *
 * <p>Column indices:</p>
 * <ul>
 *   <li>0 — Commit (short hash)</li>
 *   <li>1 — Message (first line)</li>
 *   <li>2 — Files (count)</li>
 *   <li>3 — DSL Status</li>
 * </ul>
 *
 * @since 1.2.6
 */
public class CommitTableLabelProvider extends LabelProvider implements ITableLabelProvider {

	/** Column index for commit hash. */
	public static final int COL_COMMIT = 0;

	/** Column index for commit message. */
	public static final int COL_MESSAGE = 1;

	/** Column index for file count. */
	public static final int COL_FILES = 2;

	/** Column index for DSL analysis status. */
	public static final int COL_STATUS = 3;

	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"); //$NON-NLS-1$

	@Override
	public Image getColumnImage(Object element, int columnIndex) {
		return null;
	}

	@Override
	public String getColumnText(Object element, int columnIndex) {
		if (!(element instanceof CommitTableEntry entry)) {
			return ""; //$NON-NLS-1$
		}

		return switch (columnIndex) {
		case COL_COMMIT -> entry.getCommitInfo().shortId();
		case COL_MESSAGE -> firstLine(entry.getCommitInfo().message());
		case COL_FILES -> String.valueOf(entry.getCommitInfo().changedFileCount());
		case COL_STATUS -> statusText(entry);
		default -> ""; //$NON-NLS-1$
		};
	}

	/**
	 * Returns a tooltip text for the given element, suitable for the DSL status
	 * column.
	 *
	 * @param entry the commit table entry
	 * @return tooltip text
	 */
	public String getToolTipText(CommitTableEntry entry) {
		if (entry.hasRules()) {
			StringBuilder sb = new StringBuilder();
			sb.append(entry.getRuleCount()).append(" rule(s) inferred:\n"); //$NON-NLS-1$
			entry.getInferredRules().forEach(rule -> sb.append("  ").append(rule.sourcePattern()) //$NON-NLS-1$
					.append(" => ").append(rule.replacementPattern()) //$NON-NLS-1$
					.append('\n'));
			return sb.toString();
		}
		return entry.getCommitInfo().shortId() + " — " + entry.getCommitInfo().message(); //$NON-NLS-1$
	}

	private static String statusText(CommitTableEntry entry) {
		AnalysisStatus status = entry.getStatus();
		return switch (status) {
		case PENDING -> "\u23F3"; //$NON-NLS-1$ — hourglass
		case ANALYZING -> "\u23F3 ..."; //$NON-NLS-1$ — hourglass with dots
		case DONE -> entry.hasRules()
				? "\u2705 " + entry.getRuleCount() //$NON-NLS-1$ — checkmark + count
				: "\u274C"; //$NON-NLS-1$ — red cross
		case FAILED -> "\u274C"; //$NON-NLS-1$ — red cross
		case NO_RULES -> "\u2298"; //$NON-NLS-1$ — circled not sign
		};
	}

	private static String firstLine(String message) {
		if (message == null) {
			return ""; //$NON-NLS-1$
		}
		int nl = message.indexOf('\n');
		return nl > 0 ? message.substring(0, nl) : message;
	}
}
