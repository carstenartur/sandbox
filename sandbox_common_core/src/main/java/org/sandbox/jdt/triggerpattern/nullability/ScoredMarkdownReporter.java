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
package org.sandbox.jdt.triggerpattern.nullability;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Generates a severity-grouped Markdown report from scored match entries.
 *
 * <p>The report groups matches by severity level (WARNING, CLEANUP, QUICKASSIST,
 * INFO, IGNORE) and sorts them by descending {@code trivialChange} within each
 * group.</p>
 *
 * @since 1.2.6
 */
public class ScoredMarkdownReporter {

	private static final Map<MatchSeverity, String> SEVERITY_HEADERS = new EnumMap<>(MatchSeverity.class);
	static {
		SEVERITY_HEADERS.put(MatchSeverity.WARNING, "\u26a0\ufe0f Warnings"); //$NON-NLS-1$
		SEVERITY_HEADERS.put(MatchSeverity.CLEANUP, "\ud83d\udd27 Cleanup"); //$NON-NLS-1$
		SEVERITY_HEADERS.put(MatchSeverity.QUICKASSIST, "\ud83d\udca1 QuickAssist"); //$NON-NLS-1$
		SEVERITY_HEADERS.put(MatchSeverity.INFO, "\u2139\ufe0f Info"); //$NON-NLS-1$
		SEVERITY_HEADERS.put(MatchSeverity.IGNORE, "\ud83d\udeab Ignored"); //$NON-NLS-1$
	}

	private static final Map<MatchSeverity, String> SEVERITY_DESCRIPTIONS = new EnumMap<>(MatchSeverity.class);
	static {
		SEVERITY_DESCRIPTIONS.put(MatchSeverity.WARNING,
				"These locations have a high risk of NullPointerException:"); //$NON-NLS-1$
		SEVERITY_DESCRIPTIONS.put(MatchSeverity.CLEANUP,
				"Recommended changes, null safety unclear:"); //$NON-NLS-1$
		SEVERITY_DESCRIPTIONS.put(MatchSeverity.QUICKASSIST,
				"Optional, developer should decide:"); //$NON-NLS-1$
		SEVERITY_DESCRIPTIONS.put(MatchSeverity.INFO,
				"Probably safe locations, for information only:"); //$NON-NLS-1$
		SEVERITY_DESCRIPTIONS.put(MatchSeverity.IGNORE,
				"Provably non-null, no change required:"); //$NON-NLS-1$
	}

	/**
	 * Generates a Markdown report from scored entries.
	 *
	 * @param entries the scored match entries
	 * @return the Markdown content
	 */
	public String generate(List<ScoredMatchEntry> entries) {
		Objects.requireNonNull(entries, "entries"); //$NON-NLS-1$
		StringBuilder sb = new StringBuilder();
		sb.append("# Refactoring Mining Report \u2014 ").append(LocalDate.now()).append("\n\n"); //$NON-NLS-1$ //$NON-NLS-2$

		// Group by severity
		Map<MatchSeverity, List<ScoredMatchEntry>> bySeverity = groupBySeverity(entries);

		// Summary
		sb.append("## Summary\n\n"); //$NON-NLS-1$
		sb.append("| Severity | Count |\n"); //$NON-NLS-1$
		sb.append("|----------|-------|\n"); //$NON-NLS-1$
		for (MatchSeverity sev : MatchSeverity.values()) {
			List<ScoredMatchEntry> group = bySeverity.getOrDefault(sev, List.of());
			if (!group.isEmpty()) {
				sb.append("| ").append(SEVERITY_HEADERS.get(sev)) //$NON-NLS-1$
						.append(" | ").append(group.size()).append(" |\n"); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		sb.append("\n"); //$NON-NLS-1$

		// Reverse order: WARNING first (highest severity), IGNORE last
		MatchSeverity[] displayOrder = {
				MatchSeverity.WARNING, MatchSeverity.CLEANUP,
				MatchSeverity.QUICKASSIST, MatchSeverity.INFO, MatchSeverity.IGNORE };

		for (MatchSeverity sev : displayOrder) {
			List<ScoredMatchEntry> group = bySeverity.getOrDefault(sev, List.of());
			if (group.isEmpty()) {
				continue;
			}

			sb.append("## ").append(SEVERITY_HEADERS.get(sev)) //$NON-NLS-1$
					.append(" \u2014 ").append(group.size()).append(" Matches\n\n"); //$NON-NLS-1$ //$NON-NLS-2$
			sb.append(SEVERITY_DESCRIPTIONS.get(sev)).append("\n\n"); //$NON-NLS-1$

			if (sev == MatchSeverity.IGNORE) {
				// For IGNORE, show a compact summary
				appendIgnoredSummary(sb, group);
			} else {
				// Sort by trivialChange descending
				List<ScoredMatchEntry> sorted = new ArrayList<>(group);
				sorted.sort(Comparator.comparingInt(
						(ScoredMatchEntry e) -> e.score().trivialChange()).reversed());

				for (ScoredMatchEntry entry : sorted) {
					sb.append("- `").append(entry.file()).append(":").append(entry.line()) //$NON-NLS-1$ //$NON-NLS-2$
							.append("` \u2014 `").append(truncate(entry.matchedCode(), 80)) //$NON-NLS-1$
							.append("` \u2014 ").append(entry.score().reason()); //$NON-NLS-1$
					if (entry.suggestedReplacement() != null) {
						sb.append(" \u2192 `").append(truncate(entry.suggestedReplacement(), 60)).append("`"); //$NON-NLS-1$ //$NON-NLS-2$
					}
					sb.append("\n"); //$NON-NLS-1$
				}
			}
			sb.append("\n"); //$NON-NLS-1$
		}

		return sb.toString();
	}

	private void appendIgnoredSummary(StringBuilder sb, List<ScoredMatchEntry> group) {
		// Group by reason category for compact display
		Map<String, Integer> reasonCounts = new LinkedHashMap<>();
		for (ScoredMatchEntry entry : group) {
			String key = summarizeReason(entry.score().reason());
			reasonCounts.merge(key, 1, Integer::sum);
		}
		for (Map.Entry<String, Integer> entry : reasonCounts.entrySet()) {
			sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("x\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
	}

	private String summarizeReason(String reason) {
		if (reason.contains("toString_never_null")) { //$NON-NLS-1$
			return "StringBuilder/StringBuffer.toString()"; //$NON-NLS-1$
		}
		if (reason.contains("enum")) { //$NON-NLS-1$
			return "Enum.toString()"; //$NON-NLS-1$
		}
		if (reason.contains("this")) { //$NON-NLS-1$
			return "this.toString()"; //$NON-NLS-1$
		}
		if (reason.contains("primitive")) { //$NON-NLS-1$
			return "Primitive type"; //$NON-NLS-1$
		}
		if (reason.contains("parsed_node_valid") || reason.contains("structural_child")) { //$NON-NLS-1$ //$NON-NLS-2$
			return "AST-Node.toString()"; //$NON-NLS-1$
		}
		if (reason.contains("factory_never_null")) { //$NON-NLS-1$
			return "Factory method (java.time, etc.)"; //$NON-NLS-1$
		}
		if (reason.contains("new")) { //$NON-NLS-1$
			return "'new' expression"; //$NON-NLS-1$
		}
		if (reason.contains("null guard")) { //$NON-NLS-1$
			return "Inside null guard"; //$NON-NLS-1$
		}
		return reason;
	}

	private Map<MatchSeverity, List<ScoredMatchEntry>> groupBySeverity(List<ScoredMatchEntry> entries) {
		Map<MatchSeverity, List<ScoredMatchEntry>> result = new EnumMap<>(MatchSeverity.class);
		for (ScoredMatchEntry entry : entries) {
			result.computeIfAbsent(entry.score().severity(), k -> new ArrayList<>()).add(entry);
		}
		return result;
	}

	private static String truncate(String s, int maxLen) {
		if (s == null) {
			return ""; //$NON-NLS-1$
		}
		String cleaned = s.replace("\n", " ").replace("\r", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		if (cleaned.length() <= maxLen) {
			return cleaned;
		}
		return cleaned.substring(0, maxLen - 3) + "..."; //$NON-NLS-1$
	}
}
