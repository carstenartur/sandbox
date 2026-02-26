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
import java.util.List;

import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;
import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation;
import org.sandbox.jdt.triggerpattern.mining.analysis.CommitAnalysisResult.AnalysisStatus;

/**
 * Label provider for the commit table columns.
 *
 * <p>Column indices:</p>
 * <ul>
 *   <li>0 — Commit (short hash)</li>
 *   <li>1 — Message (first line)</li>
 *   <li>2 — Files (count)</li>
 *   <li>3 — AI Status (traffic light)</li>
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

	/** Column index for AI analysis status. */
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
	 * Returns a tooltip text for the given element.
	 *
	 * @param entry the commit table entry
	 * @return tooltip text
	 */
	public String getToolTipText(CommitTableEntry entry) {
		if (entry.hasRules()) {
			StringBuilder sb = new StringBuilder();
			List<CommitEvaluation> evals = entry.getEvaluations();
			if (!evals.isEmpty()) {
				sb.append(entry.getRuleCount()).append(" AI rule(s):\n"); //$NON-NLS-1$
				evals.stream()
						.filter(e -> e.dslRule() != null && !e.dslRule().isBlank())
						.forEach(e -> sb.append("  [").append(e.trafficLight()).append("] ") //$NON-NLS-1$ //$NON-NLS-2$
								.append(firstLine(e.summary())).append('\n'));
			} else {
				sb.append(entry.getRuleCount()).append(" rule(s) inferred:\n"); //$NON-NLS-1$
				entry.getInferredRules().forEach(rule -> sb.append("  ").append(rule.sourcePattern()) //$NON-NLS-1$
						.append(" => ").append(rule.replacementPattern()) //$NON-NLS-1$
						.append('\n'));
			}
			return sb.toString();
		}
		return entry.getCommitInfo().shortId() + " \u2014 " + entry.getCommitInfo().message(); //$NON-NLS-1$
	}

	private static String statusText(CommitTableEntry entry) {
		AnalysisStatus status = entry.getStatus();
		return switch (status) {
		case PENDING -> "\u23F3"; //$NON-NLS-1$
		case ANALYZING -> "\u23F3 ..."; //$NON-NLS-1$
		case DONE -> entry.hasRules()
				? trafficLightIcon(entry) + " " + entry.getRuleCount() //$NON-NLS-1$
				: "\u274C"; //$NON-NLS-1$
		case FAILED -> "\u274C"; //$NON-NLS-1$
		case NO_RULES -> "\u2298"; //$NON-NLS-1$
		};
	}

	private static String trafficLightIcon(CommitTableEntry entry) {
		List<CommitEvaluation> evals = entry.getEvaluations();
		if (evals.isEmpty()) {
			return "\u2705"; //$NON-NLS-1$
		}
		// Use the best traffic light from all evaluations
		boolean hasGreen = evals.stream()
				.anyMatch(e -> e.trafficLight() == CommitEvaluation.TrafficLight.GREEN);
		if (hasGreen) {
			return "\uD83D\uDFE2"; //$NON-NLS-1$ — green circle
		}
		boolean hasYellow = evals.stream()
				.anyMatch(e -> e.trafficLight() == CommitEvaluation.TrafficLight.YELLOW);
		if (hasYellow) {
			return "\uD83D\uDFE1"; //$NON-NLS-1$ — yellow circle
		}
		return "\uD83D\uDD34"; //$NON-NLS-1$ — red circle
	}

	private static String firstLine(String message) {
		if (message == null) {
			return ""; //$NON-NLS-1$
		}
		int nl = message.indexOf('\n');
		return nl > 0 ? message.substring(0, nl) : message;
	}
}
