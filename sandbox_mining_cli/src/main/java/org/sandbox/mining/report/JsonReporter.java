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
import java.util.List;

import org.sandbox.mining.report.MiningReport.MatchEntry;

/**
 * Generates a JSON report from mining results.
 */
public class JsonReporter {

	/**
	 * Generates a JSON report string from the given mining report.
	 * Uses manual JSON generation to avoid adding a JSON library dependency.
	 *
	 * @param report the mining report
	 * @return the JSON content
	 */
	public String generate(MiningReport report) {
		StringBuilder sb = new StringBuilder();
		sb.append("{\n");

		// Summary
		sb.append("  \"summary\": {\n");
		sb.append("    \"totalMatches\": ").append(report.getMatches().size()).append(",\n");
		sb.append("    \"repositories\": {\n");
		var fileCounts = report.getFileCounts();
		int repoIndex = 0;
		for (var entry : fileCounts.entrySet()) {
			sb.append("      ").append(jsonString(entry.getKey())).append(": {\n");
			sb.append("        \"files\": ").append(entry.getValue()).append(",\n");
			var repoMatches = report.getMatchesByRepo().getOrDefault(entry.getKey(), List.of());
			sb.append("        \"matches\": ").append(repoMatches.size()).append(",\n");
			sb.append("        \"rules\": ").append(report.getDistinctRuleCount(entry.getKey())).append("\n");
			sb.append("      }");
			if (++repoIndex < fileCounts.size()) {
				sb.append(",");
			}
			sb.append("\n");
		}
		sb.append("    }\n");
		sb.append("  },\n");

		// Matches
		sb.append("  \"matches\": [\n");
		List<MatchEntry> matches = report.getMatches();
		for (int i = 0; i < matches.size(); i++) {
			MatchEntry match = matches.get(i);
			sb.append("    {\n");
			sb.append("      \"repository\": ").append(jsonString(match.repoName())).append(",\n");
			sb.append("      \"hintFile\": ").append(jsonString(match.hintFile())).append(",\n");
			sb.append("      \"rule\": ").append(jsonString(match.ruleName())).append(",\n");
			sb.append("      \"file\": ").append(jsonString(match.filePath())).append(",\n");
			sb.append("      \"line\": ").append(match.line()).append(",\n");
			sb.append("      \"matchedCode\": ").append(jsonString(match.matchedCode()));
			if (match.suggestedReplacement() != null) {
				sb.append(",\n      \"suggestedReplacement\": ").append(jsonString(match.suggestedReplacement()));
			}
			sb.append("\n    }");
			if (i < matches.size() - 1) {
				sb.append(",");
			}
			sb.append("\n");
		}
		sb.append("  ]\n");
		sb.append("}\n");

		return sb.toString();
	}

	/**
	 * Writes the JSON report to a file.
	 *
	 * @param report    the mining report
	 * @param outputDir the output directory
	 * @throws IOException if file writing fails
	 */
	public void write(MiningReport report, Path outputDir) throws IOException {
		Files.createDirectories(outputDir);
		String content = generate(report);
		Files.writeString(outputDir.resolve("report.json"), content, StandardCharsets.UTF_8);
	}

	private static String jsonString(String value) {
		if (value == null) {
			return "null";
		}
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
				.replace("\t", "\\t") + "\"";
	}
}
