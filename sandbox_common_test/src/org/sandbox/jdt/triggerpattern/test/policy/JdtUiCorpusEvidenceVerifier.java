/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test.policy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Maven/JUnit-owned verifier for real pinned JDT UI migration evidence.
 *
 * @since 1.3.4
 */
final class JdtUiCorpusEvidenceVerifier {

	enum Mode {
		STRICT("strict"), //$NON-NLS-1$
		BEST_EFFORT("best-effort"); //$NON-NLS-1$

		private final String externalName;

		Mode(String externalName) {
			this.externalName = externalName;
		}

		String externalName() {
			return externalName;
		}

		static Mode parse(String value) {
			for (Mode mode : values()) {
				if (mode.externalName.equals(value)) {
					return mode;
				}
			}
			throw new IllegalArgumentException("Unsupported JDT UI evidence mode: " + value); //$NON-NLS-1$
		}
	}

	private static final Gson PRETTY_JSON = new GsonBuilder().setPrettyPrinting().create();

	private JdtUiCorpusEvidenceVerifier() {
	}

	static JsonObject verify(Path repository, Path baselineSources, Path contractPath, Mode mode,
			Path changedFilesPath, Path checkReportPath, Path applyReportPath, Path outputPath)
			throws IOException {
		JsonObject contract = readObject(contractPath);
		String project = requiredString(contract, "project", "Corpus contract"); //$NON-NLS-1$ //$NON-NLS-2$
		JsonObject requiredFiles = requiredObject(contract, "requiredFiles", "Corpus contract"); //$NON-NLS-1$ //$NON-NLS-2$
		require(!requiredFiles.entrySet().isEmpty(), "Corpus contract has no requiredFiles entries"); //$NON-NLS-1$

		Set<String> actualChanged = readChangedFiles(changedFilesPath);
		Set<String> actualJava = actualChanged.stream()
				.filter(path -> path.endsWith(".java")) //$NON-NLS-1$
				.collect(Collectors.toCollection(TreeSet::new));
		int minimum = optionalInt(contract, "minimumChangedJavaFiles", 0); //$NON-NLS-1$
		require(actualJava.size() >= minimum,
				"Only " + actualJava.size() + " Java files changed; contract requires at least " + minimum); //$NON-NLS-1$ //$NON-NLS-2$

		JsonObject checkReport = readObject(checkReportPath);
		JsonObject applyReport = readObject(applyReportPath);
		requireReport(checkReport, ModeName.CHECK, "check"); //$NON-NLS-1$
		requireReport(applyReport, ModeName.APPLY, "apply"); //$NON-NLS-1$
		Set<String> checkChanged = reportChangedFiles(checkReport, project, "check"); //$NON-NLS-1$
		Set<String> applyChanged = reportChangedFiles(applyReport, project, "apply"); //$NON-NLS-1$
		require(checkChanged.equals(applyChanged),
				"Cleanup check and apply report different changed-file sets"); //$NON-NLS-1$
		require(checkChanged.equals(actualJava),
				"Cleanup reports and Git migration patch differ: reportOnly=" //$NON-NLS-1$
						+ sorted(checkChanged, actualJava) + ", gitOnly=" + sorted(actualJava, checkChanged)); //$NON-NLS-1$

		List<String> strictlyQuarantined = new ArrayList<>();
		List<String> verifiedChanged = new ArrayList<>();
		Set<String> requiredReasonCodes = new TreeSet<>();
		List<Map.Entry<String, JsonElement>> entries = new ArrayList<>(requiredFiles.entrySet());
		entries.sort(Map.Entry.comparingByKey());

		for (Map.Entry<String, JsonElement> entry : entries) {
			String relative = entry.getKey();
			require(!relative.isBlank() && entry.getValue().isJsonObject(),
					"Invalid requiredFiles entry: " + relative); //$NON-NLS-1$
			JsonObject rules = entry.getValue().getAsJsonObject();
			Path current = repository.resolve(relative);
			Path baseline = baselineSources.resolve(relative);
			require(Files.isRegularFile(current) && Files.isRegularFile(baseline),
					"Required corpus source is missing: " + relative); //$NON-NLS-1$

			String baselineText = Files.readString(baseline, StandardCharsets.UTF_8);
			String currentText = Files.readString(current, StandardCharsets.UTF_8);
			requireMarkers(baselineText,
					stringList(rules.get("baselineMustContain"), //$NON-NLS-1$
							relative + ".baselineMustContain", true), //$NON-NLS-1$
					"baseline " + relative); //$NON-NLS-1$

			boolean unchangedInStrict = optionalBoolean(rules, "strictUnchanged", false); //$NON-NLS-1$
			if (mode == Mode.STRICT && unchangedInStrict) {
				require(!actualChanged.contains(relative),
						"Strict mode changed quarantined corpus file: " + relative); //$NON-NLS-1$
				require(Files.mismatch(baseline, current) == -1,
						"Strict mode did not preserve quarantined file byte-for-byte: " + relative); //$NON-NLS-1$
				require(!currentText.contains("Sandbox JUnit migration gap"), //$NON-NLS-1$
						"Strict mode inserted a best-effort marker into " + relative); //$NON-NLS-1$
				strictlyQuarantined.add(relative);
				continue;
			}

			require(actualChanged.contains(relative),
					"Expected migrated corpus file is absent from the patch: " + relative); //$NON-NLS-1$
			requireMarkers(currentText,
					stringList(rules.get("migratedMustContain"), //$NON-NLS-1$
							relative + ".migratedMustContain", true), //$NON-NLS-1$
					"migrated " + relative); //$NON-NLS-1$
			requireAbsent(currentText,
					stringList(rules.get("migratedMustNotContain"), //$NON-NLS-1$
							relative + ".migratedMustNotContain", true), //$NON-NLS-1$
					"migrated " + relative); //$NON-NLS-1$

			if (mode == Mode.BEST_EFFORT) {
				requireMarkers(currentText,
						stringList(rules.get("bestEffortMustContain"), //$NON-NLS-1$
								relative + ".bestEffortMustContain", true), //$NON-NLS-1$
						"best-effort " + relative); //$NON-NLS-1$
				if (rules.has("strictReasonCode")) { //$NON-NLS-1$
					requiredReasonCodes.add(requiredString(rules, "strictReasonCode", relative)); //$NON-NLS-1$
				}
			} else {
				require(!currentText.contains("Sandbox JUnit migration gap"), //$NON-NLS-1$
						"Strict mode inserted a best-effort marker into " + relative); //$NON-NLS-1$
			}
			verifiedChanged.add(relative);
		}

		if (mode == Mode.BEST_EFFORT) {
			JsonObject evidence = new JsonObject();
			evidence.add("check", checkReport); //$NON-NLS-1$
			evidence.add("apply", applyReport); //$NON-NLS-1$
			List<String> missingReasons = requiredReasonCodes.stream()
					.filter(reason -> !containsString(evidence, reason))
					.toList();
			require(missingReasons.isEmpty(),
					"Best-effort reports omit required reason codes: " + missingReasons); //$NON-NLS-1$
			require(containsBooleanProperty(evidence, "manualCompletionRequired", true), //$NON-NLS-1$
					"Best-effort reports do not state that manual completion is required"); //$NON-NLS-1$
		}

		JsonObject summary = new JsonObject();
		summary.addProperty("result", "PASS"); //$NON-NLS-1$ //$NON-NLS-2$
		summary.addProperty("mode", mode.externalName()); //$NON-NLS-1$
		summary.addProperty("project", project); //$NON-NLS-1$
		summary.addProperty("changedJavaFiles", actualJava.size()); //$NON-NLS-1$
		summary.add("verifiedChangedCorpusFiles", toJsonArray(verifiedChanged)); //$NON-NLS-1$
		summary.add("strictlyQuarantinedCorpusFiles", toJsonArray(strictlyQuarantined)); //$NON-NLS-1$
		summary.add("requiredReasonCodes", toJsonArray(requiredReasonCodes)); //$NON-NLS-1$

		Files.createDirectories(outputPath.toAbsolutePath().getParent());
		Files.writeString(outputPath, PRETTY_JSON.toJson(summary) + System.lineSeparator(),
				StandardCharsets.UTF_8);
		return summary;
	}

	static JsonObject readObject(Path path) throws IOException {
		JsonElement value = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
		require(value.isJsonObject(), path + " does not contain a JSON object"); //$NON-NLS-1$
		return value.getAsJsonObject();
	}

	private static Set<String> readChangedFiles(Path path) throws IOException {
		return Files.readAllLines(path, StandardCharsets.UTF_8).stream()
				.map(String::trim)
				.filter(line -> !line.isEmpty())
				.map(JdtUiCorpusEvidenceVerifier::normalizePath)
				.collect(Collectors.toCollection(TreeSet::new));
	}

	private static Set<String> reportChangedFiles(JsonObject report, String project, String label) {
		JsonElement element = report.get("changedFiles"); //$NON-NLS-1$
		require(element != null && element.isJsonArray(),
				label + " report has no valid changedFiles list"); //$NON-NLS-1$
		Set<String> result = new TreeSet<>();
		for (JsonElement item : element.getAsJsonArray()) {
			require(item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()
					&& !item.getAsString().isBlank(),
					label + " report has no valid changedFiles list"); //$NON-NLS-1$
			String normalized = normalizePath(item.getAsString());
			result.add(normalized.startsWith(project + "/") //$NON-NLS-1$
					? normalized
					: project + "/" + normalized); //$NON-NLS-1$
		}
		return result;
	}

	private static void requireReport(JsonObject report, String mode, String label) {
		require(mode.equals(requiredString(report, "mode", label + " report")), //$NON-NLS-1$ //$NON-NLS-2$
				label + " report mode is not " + mode); //$NON-NLS-1$
		require(optionalInt(report, "errorCount", 0) == 0, //$NON-NLS-1$
				label + " report contains cleanup errors"); //$NON-NLS-1$
		JsonElement errors = report.get("errors"); //$NON-NLS-1$
		require(errors == null || errors.isJsonArray() && errors.getAsJsonArray().size() == 0,
				label + " report contains error entries"); //$NON-NLS-1$
		require(report.has("planningDiagnostics"), //$NON-NLS-1$
				label + " report contains no planningDiagnostics"); //$NON-NLS-1$
	}

	private static List<String> stringList(JsonElement element, String label, boolean allowEmpty) {
		require(element == null || element.isJsonArray(), label + " must be a string list"); //$NON-NLS-1$
		JsonArray array = element == null ? new JsonArray() : element.getAsJsonArray();
		List<String> result = new ArrayList<>();
		for (JsonElement item : array) {
			require(item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()
					&& !item.getAsString().isEmpty(),
					label + " must be a list of non-empty strings"); //$NON-NLS-1$
			result.add(item.getAsString());
		}
		require(allowEmpty || !result.isEmpty(), label + " must not be empty"); //$NON-NLS-1$
		return List.copyOf(result);
	}

	private static void requireMarkers(String text, List<String> markers, String label) {
		List<String> missing = markers.stream().filter(marker -> !text.contains(marker)).toList();
		require(missing.isEmpty(), label + " is missing markers: " + missing); //$NON-NLS-1$
	}

	private static void requireAbsent(String text, List<String> markers, String label) {
		List<String> present = markers.stream().filter(text::contains).toList();
		require(present.isEmpty(), label + " still contains forbidden markers: " + present); //$NON-NLS-1$
	}

	private static String requiredString(JsonObject object, String key, String label) {
		JsonElement value = object.get(key);
		require(value != null && value.isJsonPrimitive()
				&& value.getAsJsonPrimitive().isString() && !value.getAsString().isBlank(),
				label + " has no valid " + key); //$NON-NLS-1$
		return value.getAsString();
	}

	private static JsonObject requiredObject(JsonObject object, String key, String label) {
		JsonElement value = object.get(key);
		require(value != null && value.isJsonObject(), label + " has no " + key + " object"); //$NON-NLS-1$ //$NON-NLS-2$
		return value.getAsJsonObject();
	}

	private static int optionalInt(JsonObject object, String key, int defaultValue) {
		JsonElement value = object.get(key);
		if (value == null) {
			return defaultValue;
		}
		require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber(),
				"Expected numeric JSON property " + key); //$NON-NLS-1$
		return value.getAsInt();
	}

	private static boolean optionalBoolean(JsonObject object, String key, boolean defaultValue) {
		JsonElement value = object.get(key);
		if (value == null) {
			return defaultValue;
		}
		require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean(),
				"Expected boolean JSON property " + key); //$NON-NLS-1$
		return value.getAsBoolean();
	}

	private static boolean containsString(JsonElement element, String expected) {
		if (element == null || element.isJsonNull()) {
			return false;
		}
		if (element.isJsonPrimitive()) {
			return element.getAsJsonPrimitive().isString()
					&& expected.equals(element.getAsString());
		}
		if (element.isJsonArray()) {
			for (JsonElement item : element.getAsJsonArray()) {
				if (containsString(item, expected)) {
					return true;
				}
			}
			return false;
		}
		for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
			if (containsString(entry.getValue(), expected)) {
				return true;
			}
		}
		return false;
	}

	private static boolean containsBooleanProperty(JsonElement element, String key, boolean expected) {
		if (element == null || element.isJsonNull() || element.isJsonPrimitive()) {
			return false;
		}
		if (element.isJsonArray()) {
			for (JsonElement item : element.getAsJsonArray()) {
				if (containsBooleanProperty(item, key, expected)) {
					return true;
				}
			}
			return false;
		}
		JsonObject object = element.getAsJsonObject();
		JsonElement direct = object.get(key);
		if (direct != null && direct.isJsonPrimitive()
				&& direct.getAsJsonPrimitive().isBoolean()
				&& direct.getAsBoolean() == expected) {
			return true;
		}
		for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
			if (containsBooleanProperty(entry.getValue(), key, expected)) {
				return true;
			}
		}
		return false;
	}

	private static JsonArray toJsonArray(Iterable<String> values) {
		JsonArray array = new JsonArray();
		for (String value : values) {
			array.add(value);
		}
		return array;
	}

	private static String normalizePath(String value) {
		String normalized = value.replace('\\', '/');
		while (normalized.startsWith("./")) { //$NON-NLS-1$
			normalized = normalized.substring(2);
		}
		return normalized;
	}

	private static List<String> sorted(Set<String> left, Set<String> right) {
		return left.stream()
				.filter(value -> !right.contains(value))
				.sorted(Comparator.naturalOrder())
				.toList();
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalArgumentException(message);
		}
	}

	private static final class ModeName {
		static final String CHECK = "check"; //$NON-NLS-1$
		static final String APPLY = "apply"; //$NON-NLS-1$

		private ModeName() {
		}
	}
	}
