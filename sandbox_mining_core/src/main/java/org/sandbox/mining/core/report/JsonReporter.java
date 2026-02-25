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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import java.time.Instant;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import org.sandbox.mining.core.llm.CommitEvaluation;

/**
 * Generates JSON report files (evaluations.json and statistics.json)
 * using Gson serialization.
 */
public class JsonReporter {

	private static final String EVALUATIONS_FILE = "evaluations.json"; //$NON-NLS-1$

	private final Gson gson;

	public JsonReporter() {
		this.gson = new GsonBuilder()
				.setPrettyPrinting()
				.registerTypeAdapter(Instant.class, new TypeAdapter<Instant>() {
					@Override
					public void write(JsonWriter out, Instant value) throws IOException {
						out.value(value == null ? null : value.toString());
					}
					@Override
					public Instant read(JsonReader in) throws IOException {
						return Instant.parse(in.nextString());
					}
				})
				.create();
	}

	/**
	 * Writes evaluations to a JSON file, merging with any existing evaluations
	 * to accumulate results across runs. Deduplicates by commitHash (newer
	 * evaluations replace older ones).
	 *
	 * @param evaluations the list of new evaluations from the current run
	 * @param outputDir   the output directory
	 * @throws IOException if file writing fails
	 */
	public void writeEvaluations(List<CommitEvaluation> evaluations, Path outputDir) throws IOException {
		Files.createDirectories(outputDir);
		Path file = outputDir.resolve(EVALUATIONS_FILE);

		// Load existing evaluations and merge
		List<CommitEvaluation> existing = loadExistingEvaluations(file);
		List<CommitEvaluation> merged = mergeEvaluations(existing, evaluations);

		String json = gson.toJson(merged);
		Files.writeString(file, json, StandardCharsets.UTF_8);
	}

	/**
	 * Loads existing evaluations from a JSON file.
	 *
	 * @param file the evaluations file
	 * @return list of existing evaluations, empty if file doesn't exist or is invalid
	 */
	List<CommitEvaluation> loadExistingEvaluations(Path file) {
		if (!Files.exists(file)) {
			return List.of();
		}
		try {
			String content = Files.readString(file, StandardCharsets.UTF_8);
			List<CommitEvaluation> result = gson.fromJson(content,
					new TypeToken<List<CommitEvaluation>>() {}.getType());
			return result != null ? result : List.of();
		} catch (Exception e) {
			System.err.println("Warning: could not load existing evaluations: " + e.getMessage()); //$NON-NLS-1$
			return List.of();
		}
	}

	/**
	 * Merges existing and new evaluations, deduplicating by commitHash.
	 * New evaluations replace existing ones with the same commitHash.
	 *
	 * @param existing the existing evaluations
	 * @param newEvals the new evaluations
	 * @return merged list
	 */
	static List<CommitEvaluation> mergeEvaluations(List<CommitEvaluation> existing,
			List<CommitEvaluation> newEvals) {
		Map<String, CommitEvaluation> byHash = new LinkedHashMap<>();
		for (CommitEvaluation e : existing) {
			byHash.put(e.commitHash(), e);
		}
		for (CommitEvaluation e : newEvals) {
			byHash.put(e.commitHash(), e);
		}
		return new ArrayList<>(byHash.values());
	}

	/**
	 * Writes statistics to a JSON file.
	 *
	 * @param stats     the statistics collector
	 * @param outputDir the output directory
	 * @throws IOException if file writing fails
	 */
	public void writeStatistics(StatisticsCollector stats, Path outputDir) throws IOException {
		Files.createDirectories(outputDir);
		String json = gson.toJson(stats);
		Files.writeString(outputDir.resolve("statistics.json"), json, StandardCharsets.UTF_8);
	}

	/**
	 * Serializes evaluations to a JSON string.
	 *
	 * @param evaluations the list of evaluations
	 * @return the JSON string
	 */
	public String toJson(List<CommitEvaluation> evaluations) {
		return gson.toJson(evaluations);
	}
}
