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
package org.sandbox.mining.core.report;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;

import org.sandbox.mining.core.llm.CommitEvaluation;

/**
 * Generates an HTML page from evaluations and statistics for GitHub Pages.
 *
 * <p>Loads the report template from resources and writes the output HTML
 * along with the data JSON files (evaluations.json and statistics.json).</p>
 */
public class GithubPagesGenerator {

	private static final String TEMPLATE_RESOURCE = "/templates/report-template.html";
	private static final Set<String> DEMO_HASHES = Set.of(
			"a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
			"b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3",
			"c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4",
			"d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5",
			"e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6",
			"f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1",
			"a7b8c9d0e1f2a7b8c9d0e1f2a7b8c9d0e1f2a7b8");
	private final Gson gson;

	public GithubPagesGenerator() {
		this.gson = new GsonBuilder()
				.setPrettyPrinting()
				.registerTypeAdapter(Instant.class, (JsonSerializer<Instant>) (src, typeOfSrc, context) ->
						new JsonPrimitive(src.toString()))
				.registerTypeAdapter(Instant.class, (JsonDeserializer<Instant>) (json, typeOfT, context) ->
						Instant.parse(json.getAsString()))
				.create();
	}

	/**
	 * Generates the GitHub Pages output (index.html, evaluations.json, statistics.json).
	 *
	 * <p>Loads any existing evaluations.json from the output directory, filters out
	 * demo seed data, deduplicates by commit hash, merges with the new evaluations,
	 * and rebuilds statistics from the combined list.</p>
	 *
	 * @param evaluations the evaluation results from the current run
	 * @param stats       the statistics collector for the current run (used for run metadata)
	 * @param outputDir   the output directory
	 * @throws IOException if an I/O error occurs
	 */
	public void generate(List<CommitEvaluation> evaluations,
			StatisticsCollector stats, Path outputDir) throws IOException {
		Files.createDirectories(outputDir);

		List<CommitEvaluation> merged = loadAndMerge(outputDir, evaluations);

		StatisticsCollector mergedStats = StatisticsCollector.rebuildFrom(merged);
		mergedStats.setRunMetadata(stats.getRunMetadata());
		mergedStats.computeTimeWindow(merged);

		String template = loadTemplate();
		String html = injectStatistics(template, mergedStats);

		Files.writeString(outputDir.resolve("index.html"), html, StandardCharsets.UTF_8);
		Files.writeString(outputDir.resolve("evaluations.json"),
				gson.toJson(merged), StandardCharsets.UTF_8);
		Files.writeString(outputDir.resolve("statistics.json"),
				gson.toJson(mergedStats), StandardCharsets.UTF_8);
	}

	/**
	 * Loads existing evaluations from the output directory, filters demo data,
	 * and merges with the new evaluations (deduplicating by commit hash).
	 *
	 * @param outputDir   the output directory
	 * @param newEvals    the new evaluations from the current run
	 * @return the merged list
	 * @throws IOException if an I/O error occurs
	 */
	private List<CommitEvaluation> loadAndMerge(Path outputDir,
			List<CommitEvaluation> newEvals) throws IOException {
		Path existingFile = outputDir.resolve("evaluations.json");
		List<CommitEvaluation> existing = new ArrayList<>();
		if (Files.exists(existingFile)) {
			String json = Files.readString(existingFile, StandardCharsets.UTF_8);
			Type listType = new TypeToken<List<CommitEvaluation>>() {}.getType();
			List<CommitEvaluation> loaded = gson.fromJson(json, listType);
			if (loaded != null) {
				existing = loaded.stream()
						.filter(e -> !DEMO_HASHES.contains(e.commitHash()))
						.collect(Collectors.toCollection(ArrayList::new));
			}
		}
		Set<String> existingHashes = existing.stream()
				.map(CommitEvaluation::commitHash)
				.collect(Collectors.toSet());
		for (CommitEvaluation newEval : newEvals) {
			if (!existingHashes.contains(newEval.commitHash())) {
				existing.add(newEval);
			}
		}
		return existing;
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
				<title>Sandbox Mining Core - Report</title>
				</head>
				<body>
				<h1>Mining Report</h1>
				<p style="background: #f8f9fa; padding: 15px; border-radius: 8px; margin-bottom: 20px;">
				  <a href="https://github.com/carstenartur/sandbox/actions/workflows/mining-core.yml">
				    <img src="https://github.com/carstenartur/sandbox/actions/workflows/mining-core.yml/badge.svg" alt="Workflow Status">
				  </a>
				  | <a href="https://github.com/carstenartur/sandbox/actions/workflows/mining-core.yml">View Workflow Runs</a>
				  | <a href="https://carstenartur.github.io/sandbox/">← Back to Dashboard</a>
				</p>
				<p>Total: {{totalProcessed}}, Relevant: {{relevant}}, Green: {{green}}, Yellow: {{yellow}}, Red: {{red}}</p>
				<h2>Discovered DSL Sequences</h2>
				<div id="dsl-body"></div>
				<script>
				fetch('evaluations.json').then(r=>r.json()).then(data=>{
				  var dsl=data.filter(e=>e.dslRule&&e.relevant);
				  var el=document.getElementById('dsl-body');
				  if(dsl.length===0){el.textContent='No DSL sequences discovered yet.';return;}
				  dsl.forEach(e=>{
				    var p=document.createElement('p');
				    p.innerHTML='<strong>'+e.trafficLight+'</strong> ['+e.category+'] '+e.summary+'<pre>'+e.dslRule+'</pre>';
				    el.appendChild(p);
				  });
				});
				</script>
				</body>
				</html>
				""";
	}
}
