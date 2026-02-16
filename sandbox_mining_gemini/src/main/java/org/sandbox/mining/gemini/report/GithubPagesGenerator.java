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
package org.sandbox.mining.gemini.report;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.sandbox.mining.gemini.gemini.CommitEvaluation;

/**
 * Generates an HTML page from evaluations and statistics for GitHub Pages.
 *
 * <p>Loads the report template from resources and writes the output HTML
 * along with the data JSON files.</p>
 */
public class GithubPagesGenerator {

	private static final String TEMPLATE_RESOURCE = "/templates/report-template.html";

	/**
	 * Generates the GitHub Pages output (index.html, evaluations.json, statistics.json).
	 *
	 * @param evaluations the evaluation results
	 * @param stats       the statistics collector
	 * @param outputDir   the output directory
	 * @throws IOException if an I/O error occurs
	 */
	public void generate(List<CommitEvaluation> evaluations,
			StatisticsCollector stats, Path outputDir) throws IOException {
		Files.createDirectories(outputDir);

		String template = loadTemplate();
		String html = injectStatistics(template, stats);

		Files.writeString(outputDir.resolve("index.html"), html, StandardCharsets.UTF_8);
	}

	/**
	 * Loads the HTML template from resources.
	 *
	 * @return the template content
	 * @throws IOException if the template cannot be loaded
	 */
	public String loadTemplate() throws IOException {
		try (InputStream is = getClass().getResourceAsStream(TEMPLATE_RESOURCE)) {
			if (is == null) {
				return getDefaultTemplate();
			}
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private String injectStatistics(String template, StatisticsCollector stats) {
		return template
				.replace("{{totalProcessed}}", String.valueOf(stats.getTotalProcessed()))
				.replace("{{relevant}}", String.valueOf(stats.getRelevant()))
				.replace("{{irrelevant}}", String.valueOf(stats.getIrrelevant()))
				.replace("{{duplicates}}", String.valueOf(stats.getDuplicates()))
				.replace("{{green}}", String.valueOf(stats.getGreen()))
				.replace("{{yellow}}", String.valueOf(stats.getYellow()))
				.replace("{{red}}", String.valueOf(stats.getRed()));
	}

	private static String getDefaultTemplate() {
		return """
				<!DOCTYPE html>
				<html lang="en">
				<head>
				<meta charset="UTF-8">
				<title>Sandbox Mining Gemini - Report</title>
				</head>
				<body>
				<h1>Mining Report</h1>
				<p>Total: {{totalProcessed}}, Relevant: {{relevant}}, Green: {{green}}, Yellow: {{yellow}}, Red: {{red}}</p>
				<script>
				fetch('evaluations.json').then(r=>r.json()).then(data=>{
				  console.log('Loaded', data.length, 'evaluations');
				});
				</script>
				</body>
				</html>
				""";
	}
}
