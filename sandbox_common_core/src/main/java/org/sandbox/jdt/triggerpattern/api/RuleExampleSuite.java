/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0: https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.api;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;

/**
 * Versioned, readable evidence stored beside a rule as {@code .examples.json}.
 * Sources are complete compilation units; no formatting or line-ending changes
 * are made. Evidence is replayed, never interpreted as permission to rewrite.
 */
public record RuleExampleSuite(int schemaVersion, List<Example> examples) {

	public static final int CURRENT_VERSION = 1;
	private static final Gson JSON = new GsonBuilder().setPrettyPrinting()
			.disableHtmlEscaping().setStrictness(Strictness.STRICT).create();

	public RuleExampleSuite {
		if (schemaVersion != CURRENT_VERSION) {
			throw new IllegalArgumentException("Unsupported example schema version: " + schemaVersion);
		}
		examples = List.copyOf(Objects.requireNonNull(examples, "examples"));
		Set<String> names = new HashSet<>();
		for (Example example : examples) {
			if (!names.add(example.name())) {
				throw new IllegalArgumentException("Example names must be unique: " + example.name());
			}
		}
	}

	public record Example(String name, String unitName, String before, String after, boolean expectChange) {
		public Example {
			if (name == null || name.isBlank() || unitName == null || !unitName.endsWith(".java")) {
				throw new IllegalArgumentException("Each example needs a name and a .java compilation-unit name");
			}
			if (before == null || before.isBlank() || after == null || after.isBlank()) {
				throw new IllegalArgumentException("Examples require complete nonempty before and after source");
			}
			if (expectChange == before.equals(after)) {
				throw new IllegalArgumentException("Positive examples must change; counterexamples must stay identical");
			}
		}
	}

	public String toJson() {
		return JSON.toJson(this) + "\n";
	}

	/** Rejects missing or misspelled fields instead of silently changing expectations. */
	public static RuleExampleSuite fromJson(String source) {
		try {
			JsonObject root = JSON.fromJson(source, JsonObject.class);
			requireFields(root, Set.of("schemaVersion", "examples"));
			if (!root.get("schemaVersion").isJsonPrimitive()
					|| !root.getAsJsonPrimitive("schemaVersion").isNumber()
					|| root.get("schemaVersion").getAsBigDecimal().intValueExact() != CURRENT_VERSION) {
				throw new IllegalArgumentException("schemaVersion must be the supported JSON integer " + CURRENT_VERSION);
			}
			for (var element : root.getAsJsonArray("examples")) {
				JsonObject example = element.getAsJsonObject();
				requireFields(example, Set.of("name", "unitName", "before", "after", "expectChange"));
				for (String field : List.of("name", "unitName", "before", "after")) {
					if (!example.get(field).isJsonPrimitive() || !example.getAsJsonPrimitive(field).isString()) {
						throw new IllegalArgumentException(field + " must be a JSON string");
					}
				}
				if (!example.get("expectChange").isJsonPrimitive()
						|| !example.getAsJsonPrimitive("expectChange").isBoolean()) {
					throw new IllegalArgumentException("expectChange must be a JSON boolean");
				}
			}
			return JSON.fromJson(root, RuleExampleSuite.class);
		} catch (RuntimeException exception) {
			throw new IllegalArgumentException("Invalid rule examples: " + exception.getMessage(), exception);
		}
	}

	private static void requireFields(JsonObject object, Set<String> fields) {
		if (object == null || !object.keySet().equals(fields)) {
			throw new IllegalArgumentException("Required fields: " + fields);
		}
	}
}
