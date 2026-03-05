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
package org.sandbox.mining.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sandbox.mining.report.MiningReport.MatchEntry;

/**
 * Generates a Markdown report from mining results.
 */
public class MarkdownReporter {

	/**
	 * Generates a Markdown report string from the given mining report.
	 *
	 * @param report the mining report
	 * @return the Markdown content
	 */
	public String generate(MiningReport report) {
		return generate(report, null);
	}

	/**
	 * Generates a delta-aware Markdown report string. When a previous report
	 * is provided, the summary table includes "New" and "Known" columns, and
	 * the details section highlights only matches that were not present in the
	 * previous report.
	 *
	 * @param report         the current mining report
	 * @param previousReport the previous report for delta computation, or {@code null}
	 * @return the Markdown content
	 */
	public String generate(MiningReport report, MiningReport previousReport) {
		Set<String> knownMatchKeys = previousReport != null ? buildMatchKeys(previousReport) : Set.of();

		StringBuilder sb = new StringBuilder();
		sb.append("# Refactoring Mining Report — ").append(LocalDate.now()).append("\n\n");

		// Summary table
		boolean hasDelta = !knownMatchKeys.isEmpty();
		if (hasDelta) {
			sb.append("## Summary\n");
			sb.append("| Eclipse Project | Files | Matches | New | Known | Rules |\n");
			sb.append("|----------------|-------|---------|-----|-------|-------|\n");
		} else {
			sb.append("## Summary\n");
			sb.append("| Eclipse Project | Files | Matches | Rules |\n");
			sb.append("|----------------|-------|---------|-------|\n");
		}

		Map<String, Integer> fileCounts = report.getFileCounts();
		Map<String, List<MatchEntry>> byRepo = report.getMatchesByRepo();
		Map<String, String> errors = report.getErrors();

		for (Map.Entry<String, Integer> entry : fileCounts.entrySet()) {
			String repoName = entry.getKey();
			int files = entry.getValue();
			List<MatchEntry> repoMatches = byRepo.getOrDefault(repoName, List.of());
			long rules = report.getDistinctRuleCount(repoName);
			String marker = errors.containsKey(repoName) ? " ⚠️" : "";
			if (hasDelta) {
				long newCount = repoMatches.stream().filter(m -> !knownMatchKeys.contains(matchKey(m))).count();
				long knownCount = repoMatches.size() - newCount;
				sb.append("| ").append(repoName).append(marker).append(" | ").append(files).append(" | ")
						.append(repoMatches.size()).append(" | ").append(newCount).append(" | ")
						.append(knownCount).append(" | ").append(rules).append(" |\n");
			} else {
				sb.append("| ").append(repoName).append(marker).append(" | ").append(files).append(" | ").append(repoMatches.size())
						.append(" | ").append(rules).append(" |\n");
			}
		}

		// Details — when a previous report exists, highlight new matches
		sb.append("\n## Details\n");
		if (hasDelta) {
			sb.append("_Only **new** matches since the last run are shown below._\n\n");
		}
		for (Map.Entry<String, List<MatchEntry>> repoEntry : byRepo.entrySet()) {
			List<MatchEntry> repoMatches = repoEntry.getValue();
			List<MatchEntry> displayMatches = hasDelta
					? repoMatches.stream().filter(m -> !knownMatchKeys.contains(matchKey(m))).toList()
					: repoMatches;
			if (displayMatches.isEmpty()) {
				continue;
			}
			sb.append("### ").append(repoEntry.getKey()).append("\n");

			// Group by hint file and rule
			Map<String, Map<String, List<MatchEntry>>> byHintAndRule = new java.util.LinkedHashMap<>();
			for (MatchEntry match : displayMatches) {
				byHintAndRule.computeIfAbsent(match.hintFile(), k -> new java.util.LinkedHashMap<>())
						.computeIfAbsent(match.ruleName(), k -> new java.util.ArrayList<>()).add(match);
			}

			for (Map.Entry<String, Map<String, List<MatchEntry>>> hintEntry : byHintAndRule.entrySet()) {
				for (Map.Entry<String, List<MatchEntry>> ruleEntry : hintEntry.getValue().entrySet()) {
					sb.append("#### Rule: `").append(hintEntry.getKey()).append("` → `").append(ruleEntry.getKey())
							.append("`\n");
					for (MatchEntry match : ruleEntry.getValue()) {
						sb.append("- `").append(match.filePath()).append(":").append(match.line()).append("` — `")
								.append(truncate(match.matchedCode(), 80)).append("`");
						if (match.suggestedReplacement() != null) {
							sb.append(" → `").append(truncate(match.suggestedReplacement(), 80)).append("`");
						}
						sb.append("\n");
					}
					sb.append("\n");
				}
			}
		}

		if (!report.hasMatches()) {
			sb.append("No matches found.\n");
		}

		// Errors section
		if (report.hasErrors()) {
			sb.append("\n## Errors\n");
			sb.append("The following repositories encountered errors during scanning:\n\n");
			for (Map.Entry<String, String> error : report.getErrors().entrySet()) {
				sb.append("- **").append(error.getKey()).append("**: `").append(truncate(error.getValue(), 200))
						.append("`\n");
			}
		}

		return sb.toString();
	}

	/**
	 * Writes the Markdown report to a file.
	 *
	 * @param report    the mining report
	 * @param outputDir the output directory
	 * @throws IOException if file writing fails
	 */
	public void write(MiningReport report, Path outputDir) throws IOException {
		Files.createDirectories(outputDir);
		String content = generate(report);
		Files.writeString(outputDir.resolve("report.md"), content, StandardCharsets.UTF_8);
	}

	private static String truncate(String s, int maxLen) {
		if (s == null) {
			return "";
		}
		String cleaned = s.replace("\n", " ").replace("\r", "");
		if (cleaned.length() <= maxLen) {
			return cleaned;
		}
		return cleaned.substring(0, maxLen - 3) + "...";
	}

	/**
	 * Builds a set of match keys from a report for delta comparison.
	 */
	private static Set<String> buildMatchKeys(MiningReport report) {
		Set<String> keys = new HashSet<>();
		for (MatchEntry m : report.getMatches()) {
			keys.add(matchKey(m));
		}
		return keys;
	}

	/**
	 * Creates a unique key for a match entry based on repo, file, line, and rule.
	 */
	private static String matchKey(MatchEntry m) {
		return m.repoName() + "|" + m.hintFile() + "|" + m.ruleName() + "|" + m.filePath() + ":" + m.line();
	}
}
