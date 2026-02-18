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
import java.util.List;
import java.util.Map;

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
		StringBuilder sb = new StringBuilder();
		sb.append("# Refactoring Mining Report — ").append(LocalDate.now()).append("\n\n");

		// Summary table
		sb.append("## Summary\n");
		sb.append("| Eclipse Project | Files | Matches | Rules |\n");
		sb.append("|----------------|-------|---------|-------|\n");

		Map<String, Integer> fileCounts = report.getFileCounts();
		Map<String, List<MatchEntry>> byRepo = report.getMatchesByRepo();
		Map<String, String> errors = report.getErrors();

		for (Map.Entry<String, Integer> entry : fileCounts.entrySet()) {
			String repoName = entry.getKey();
			int files = entry.getValue();
			List<MatchEntry> repoMatches = byRepo.getOrDefault(repoName, List.of());
			long rules = report.getDistinctRuleCount(repoName);
			String marker = errors.containsKey(repoName) ? " ⚠️" : "";
			sb.append("| ").append(repoName).append(marker).append(" | ").append(files).append(" | ").append(repoMatches.size())
					.append(" | ").append(rules).append(" |\n");
		}

		// Details
		sb.append("\n## Details\n");
		for (Map.Entry<String, List<MatchEntry>> repoEntry : byRepo.entrySet()) {
			sb.append("### ").append(repoEntry.getKey()).append("\n");

			// Group by hint file and rule
			Map<String, Map<String, List<MatchEntry>>> byHintAndRule = new java.util.LinkedHashMap<>();
			for (MatchEntry match : repoEntry.getValue()) {
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
}
