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
package org.sandbox.mining.core.comparison;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Holds the results of comparing Gemini mining results against
 * a reference evaluation. Provides gap analysis and suggestions
 * for improving the mining pipeline.
 */
public class DeltaReport {

	private final List<GapEntry> gaps = new ArrayList<>();

	/**
	 * Adds a gap entry to the report.
	 *
	 * @param entry the gap entry
	 */
	public void addGap(GapEntry entry) {
		if (entry != null) {
			gaps.add(entry);
		}
	}

	/**
	 * Returns all gap entries.
	 *
	 * @return unmodifiable list of all gaps
	 */
	public List<GapEntry> getGaps() {
		return Collections.unmodifiableList(gaps);
	}

	/**
	 * Groups gaps by category.
	 *
	 * @return map of category to list of gaps
	 */
	public Map<GapCategory, List<GapEntry>> groupByCategory() {
		return gaps.stream()
				.collect(Collectors.groupingBy(
						GapEntry::category,
						LinkedHashMap::new,
						Collectors.toList()));
	}

	/**
	 * Returns a summary of gaps per category.
	 *
	 * @return map of category to count
	 */
	public Map<GapCategory, Long> summarize() {
		return gaps.stream()
				.collect(Collectors.groupingBy(
						GapEntry::category,
						LinkedHashMap::new,
						Collectors.counting()));
	}

	/**
	 * Returns the total number of gaps.
	 *
	 * @return gap count
	 */
	public int getTotalGaps() {
		return gaps.size();
	}

	/**
	 * Formats the delta report as a human-readable string.
	 *
	 * @return formatted report
	 */
	public String format() {
		StringBuilder sb = new StringBuilder();
		sb.append("=== Delta Report ===\n"); //$NON-NLS-1$
		sb.append("Total gaps: ").append(gaps.size()).append("\n\n"); //$NON-NLS-1$ //$NON-NLS-2$
		Map<GapCategory, List<GapEntry>> grouped = groupByCategory();
		for (Map.Entry<GapCategory, List<GapEntry>> entry : grouped.entrySet()) {
			sb.append("## ").append(entry.getKey()).append(" (") //$NON-NLS-1$ //$NON-NLS-2$
					.append(entry.getValue().size()).append(")\n"); //$NON-NLS-1$
			for (GapEntry gap : entry.getValue()) {
				sb.append("  - ").append(gap.commitHash(), 0, Math.min(7, gap.commitHash().length())); //$NON-NLS-1$
				if (gap.suggestion() != null) {
					sb.append(": ").append(gap.suggestion()); //$NON-NLS-1$
				}
				sb.append("\n"); //$NON-NLS-1$
			}
			sb.append("\n"); //$NON-NLS-1$
		}
		return sb.toString();
	}

	/**
	 * Formats the delta report as Markdown with actionable suggestions per category.
	 *
	 * @return Markdown-formatted report
	 */
	public String formatMarkdown() {
		StringBuilder sb = new StringBuilder();
		sb.append("# Delta Report\n\n"); //$NON-NLS-1$
		sb.append("**Total gaps:** ").append(gaps.size()).append("\n\n"); //$NON-NLS-1$ //$NON-NLS-2$

		if (gaps.isEmpty()) {
			sb.append("No gaps found — mining results match the reference evaluation.\n"); //$NON-NLS-1$
			return sb.toString();
		}

		sb.append("## Gap Distribution\n\n"); //$NON-NLS-1$
		sb.append("| Category | Count | Suggested Action |\n"); //$NON-NLS-1$
		sb.append("|----------|-------|------------------|\n"); //$NON-NLS-1$
		Map<GapCategory, Long> summary = summarize();
		for (Map.Entry<GapCategory, Long> entry : summary.entrySet()) {
			sb.append("| ").append(entry.getKey()) //$NON-NLS-1$
					.append(" | ").append(entry.getValue()) //$NON-NLS-1$
					.append(" | ").append(entry.getKey().suggestedAction()) //$NON-NLS-1$
					.append(" |\n"); //$NON-NLS-1$
		}
		sb.append("\n"); //$NON-NLS-1$

		sb.append("## Gap Details\n\n"); //$NON-NLS-1$
		Map<GapCategory, List<GapEntry>> grouped = groupByCategory();
		for (Map.Entry<GapCategory, List<GapEntry>> entry : grouped.entrySet()) {
			sb.append("### ").append(entry.getKey()) //$NON-NLS-1$
					.append(" (").append(entry.getValue().size()).append(")\n\n"); //$NON-NLS-1$ //$NON-NLS-2$
			for (GapEntry gap : entry.getValue()) {
				String shortHash = gap.commitHash().substring(0,
						Math.min(7, gap.commitHash().length()));
				sb.append("- **").append(shortHash).append("**"); //$NON-NLS-1$ //$NON-NLS-2$
				if (gap.suggestion() != null) {
					sb.append(": ").append(gap.suggestion()); //$NON-NLS-1$
				}
				if (gap.geminiValue() != null && gap.referenceValue() != null) {
					sb.append(" (gemini=`").append(gap.geminiValue()) //$NON-NLS-1$
							.append("` → reference=`").append(gap.referenceValue()).append("`)"); //$NON-NLS-1$ //$NON-NLS-2$
				}
				sb.append("\n"); //$NON-NLS-1$
			}
			sb.append("\n"); //$NON-NLS-1$
		}

		return sb.toString();
	}

	/**
	 * Serializes the delta report as a JSON string.
	 *
	 * @return JSON representation of the report
	 */
	public String toJson() {
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		Map<String, Object> reportMap = new LinkedHashMap<>();
		reportMap.put("totalGaps", gaps.size()); //$NON-NLS-1$
		reportMap.put("summary", summarize()); //$NON-NLS-1$
		reportMap.put("gaps", gaps); //$NON-NLS-1$
		return gson.toJson(reportMap);
	}

	/**
	 * Writes the delta report to JSON and Markdown files in the given directory.
	 *
	 * @param outputDir directory where files will be written
	 * @throws IOException if file writing fails
	 */
	public void writeToFiles(Path outputDir) throws IOException {
		Files.createDirectories(outputDir);
		Files.writeString(outputDir.resolve("delta-report.json"), //$NON-NLS-1$
				toJson(), StandardCharsets.UTF_8);
		Files.writeString(outputDir.resolve("delta-report.md"), //$NON-NLS-1$
				formatMarkdown(), StandardCharsets.UTF_8);
	}
}
