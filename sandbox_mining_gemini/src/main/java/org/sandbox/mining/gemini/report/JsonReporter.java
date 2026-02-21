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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import java.time.Instant;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;

import org.sandbox.mining.gemini.gemini.CommitEvaluation;

/**
 * Generates JSON report files (evaluations.json and statistics.json)
 * using Gson serialization.
 */
public class JsonReporter {

	private final Gson gson;

	public JsonReporter() {
		this.gson = new GsonBuilder()
				.setPrettyPrinting()
				.registerTypeAdapter(Instant.class, (JsonSerializer<Instant>) (src, typeOfSrc, context) ->
						new JsonPrimitive(src.toString()))
				.create();
	}

	/**
	 * Writes evaluations to a JSON file.
	 *
	 * @param evaluations the list of evaluations
	 * @param outputDir   the output directory
	 * @throws IOException if file writing fails
	 */
	public void writeEvaluations(List<CommitEvaluation> evaluations, Path outputDir) throws IOException {
		Files.createDirectories(outputDir);
		String json = gson.toJson(evaluations);
		Files.writeString(outputDir.resolve("evaluations.json"), json, StandardCharsets.UTF_8);
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
