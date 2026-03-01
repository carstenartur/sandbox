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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation;

/**
 * Imports evaluation results from external tools (e.g. Copilot)
 * for comparison against the Gemini mining pipeline.
 *
 * <p>Supports JSON format compatible with {@link CommitEvaluation}.</p>
 */
public class ExternalEvaluationImporter {

	private final Gson gson;

	public ExternalEvaluationImporter() {
		this.gson = new GsonBuilder()
				.registerTypeAdapter(Instant.class, new TypeAdapter<Instant>() {
					@Override
					public void write(JsonWriter out, Instant value) throws IOException {
						out.value(value == null ? null : value.toString());
					}
					@Override
					public Instant read(JsonReader in) throws IOException {
						String s = in.nextString();
						return s == null ? null : Instant.parse(s);
					}
				})
				.create();
	}

	/**
	 * Imports evaluations from a JSON file.
	 *
	 * @param jsonFile path to the JSON file containing evaluations
	 * @return list of imported evaluations
	 * @throws IOException if the file cannot be read
	 */
	public List<CommitEvaluation> importFromJson(Path jsonFile) throws IOException {
		String content = Files.readString(jsonFile, StandardCharsets.UTF_8);
		List<CommitEvaluation> result = gson.fromJson(content,
				new TypeToken<List<CommitEvaluation>>() {}.getType());
		return result != null ? result : new ArrayList<>();
	}

	/**
	 * Imports evaluations from a JSON string.
	 *
	 * @param json the JSON string
	 * @return list of imported evaluations
	 */
	public List<CommitEvaluation> importFromString(String json) {
		List<CommitEvaluation> result = gson.fromJson(json,
				new TypeToken<List<CommitEvaluation>>() {}.getType());
		return result != null ? result : new ArrayList<>();
	}
}
