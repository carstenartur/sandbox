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
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Verifies the checked-in contract and generated evidence for the pinned JDT UI
 * JUnit 4 to Jupiter corpus.
 *
 * <p>The class is deliberately independent of workflow and shell APIs. Both
 * ordinary unit tests and the retained-workspace runner invoke this same Java
 * authority through Maven.</p>
 *
 * @since 1.3.5
 */
public final class JdtUiCorpusEvidenceVerifier {

	private static final String BEST_EFFORT_MARKER = "Sandbox JUnit migration gap"; //$NON-NLS-1$

	private JdtUiCorpusEvidenceVerifier() {
		// Utility class.
	}

	/** Supported corpus verification modes. */
	public enum Mode {
		STRICT("strict"), //$NON-NLS-1$
		BEST_EFFORT("best-effort"); //$NON-NLS-1$

		private final String externalName;

		Mode(String externalName) {
			this.externalName = externalName;
		}

		public String externalName() {
			return externalName;
		}

		public static Mode parse(String value) {
			for (Mode mode : values()) {
				if (mode.externalName.equals(value)) {
					return mode;
				}
			}
			throw new IllegalArgumentException("Unsupported JDT UI corpus mode: " + value); //$NON-NLS-1$
		}
	}

	/** Complete verifier input. */
	public record Request(Path repository, Path baselineSources, Path contract, Mode mode,
			Path changedFiles, Path checkReport, Path applyReport, Path output) {

		public Request {
			Objects.requireNonNull(repository, "repository"); //$NON-NLS-1$
			Objects.requireNonNull(baselineSources, "baselineSources"); //$NON-NLS-1$
			Objects.requireNonNull(contract, "contract"); //$NON-NLS-1$
			Objects.requireNonNull(mode, "mode"); //$NON-NLS-1$
			Objects.requireNonNull(changedFiles, "changedFiles"); //$NON-NLS-1$
			Objects.requireNonNull(checkReport, "checkReport"); //$NON-NLS-1$
			Objects.requireNonNull(applyReport, "applyReport"); //$NON-NLS-1$
			Objects.requireNonNull(output, "output"); //$NON-NLS-1$
		}
	}

	/** Parsed corpus file rules. */
	public record FileRules(List<String> baselineMustContain, List<String> migratedMustContain,
			List<String> migratedMustNotContain, boolean strictUnchanged, String strictReasonCode,
			List<String> bestEffortMustContain) {

		public FileRules {
			baselineMustContain = List.copyOf(baselineMustContain);
			migratedMustContain = List.copyOf(migratedMustContain);
			migratedMustNotContain = List.copyOf(migratedMustNotContain);
			bestEffortMustContain = List.copyOf(bestEffortMustContain);
		}
	}

	/** Parsed pinned corpus contract. */
	public record Contract(String repository, String ref, String commit, String project,
			int minimumChangedJavaFiles, Map<String, FileRules> requiredFiles) {

		public Contract {
			requiredFiles = Collections.unmodifiableMap(new LinkedHashMap<>(requiredFiles));
		}
	}

	/** Successful verification result. */
	public record Result(Mode mode, Contract contract, int changedFiles, int changedJavaFiles,
			List<String> changedNonJavaFiles, List<String> verifiedChangedCorpusFiles,
			List<String> strictlyQuarantinedCorpusFiles, List<String> requiredReasonCodes) {

		public Result {
			changedNonJavaFiles = List.copyOf(changedNonJavaFiles);
			verifiedChangedCorpusFiles = List.copyOf(verifiedChangedCorpusFiles);
			strictlyQuarantinedCorpusFiles = List.copyOf(strictlyQuarantinedCorpusFiles);
			requiredReasonCodes = List.copyOf(requiredReasonCodes);
		}
	}

	public static Contract readContract(Path path) throws IOException {
		JsonObject root = readObject(path, "corpus contract"); //$NON-NLS-1$
		String repository = requiredString(root, "repository", "Corpus contract"); //$NON-NLS-1$ //$NON-NLS-2$
		String ref = requiredString(root, "ref", "Corpus contract"); //$NON-NLS-1$ //$NON-NLS-2$
		String commit = requiredString(root, "commit", "Corpus contract"); //$NON-NLS-1$ //$NON-NLS-2$
		String project = requiredString(root, "project", "Corpus contract"); //$NON-NLS-1$ //$NON-NLS-2$
		int minimumChangedJavaFiles = integer(root, "minimumChangedJavaFiles", 0, "Corpus contract"); //$NON-NLS-1$ //$NON-NLS-2$
		if (minimumChangedJavaFiles < 0) {
			fail("Corpus contract minimumChangedJavaFiles must not be negative"); //$NON-NLS-1$
		}
		JsonObject required = requiredObject(root, "requiredFiles", "Corpus contract"); //$NON-NLS-1$ //$NON-NLS-2$
		if (required.size() == 0) {
			fail("Corpus contract has no requiredFiles entries"); //$NON-NLS-1$
		}

		Map<String, FileRules> files = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : required.entrySet()) {
			String relative = entry.getKey();
			if (relative.isBlank() || !entry.getValue().isJsonObject()) {
				fail("Invalid requiredFiles entry: " + relative); //$NON-NLS-1$
			}
			JsonObject rules = entry.getValue().getAsJsonObject();
			files.put(relative, new FileRules(
					stringList(rules, "baselineMustContain", relative), //$NON-NLS-1$
					stringList(rules, "migratedMustContain", relative), //$NON-NLS-1$
					stringList(rules, "migratedMustNotContain", relative), //$NON-NLS-1$
					booleanValue(rules, "strictUnchanged", false, relative), //$NON-NLS-1$
					optionalString(rules, "strictReasonCode", relative), //$NON-NLS-1$
					stringList(rules, "bestEffortMustContain", relative))); //$NON-NLS-1$
		}
		return new Contract(repository, ref, commit, project, minimumChangedJavaFiles, files);
	}

	public static Result verify(Request request) throws IOException {
		Contract contract = readContract(request.contract());
		Set<String> actualChanged = changedFiles(request.changedFiles());
		Set<String> actualJava = new LinkedHashSet<>();
		List<String> actualNonJava = new ArrayList<>();
		for (String path : actualChanged) {
			if (path.endsWith(".java")) { //$NON-NLS-1$
				actualJava.add(path);
			} else {
				actualNonJava.add(path);
			}
		}
		if (actualJava.size() < contract.minimumChangedJavaFiles()) {
			fail("Only " + actualJava.size() + " Java files changed; contract requires at least " //$NON-NLS-1$ //$NON-NLS-2$
					+ contract.minimumChangedJavaFiles());
		}

		JsonObject checkReport = readObject(request.checkReport(), "check report"); //$NON-NLS-1$
		JsonObject applyReport = readObject(request.applyReport(), "apply report"); //$NON-NLS-1$
		requireReport(checkReport, "check", "check"); //$NON-NLS-1$ //$NON-NLS-2$
		requireReport(applyReport, "apply", "apply"); //$NON-NLS-1$ //$NON-NLS-2$
		Set<String> checkChanged = reportChangedFiles(checkReport, contract.project(), "check"); //$NON-NLS-1$
		Set<String> applyChanged = reportChangedFiles(applyReport, contract.project(), "apply"); //$NON-NLS-1$
		if (!checkChanged.equals(applyChanged)) {
			fail("Cleanup check and apply report different changed-file sets"); //$NON-NLS-1$
		}
		if (!checkChanged.equals(actualChanged)) {
			fail("Cleanup reports and Git migration patch differ: reportOnly=" //$NON-NLS-1$
					+ difference(checkChanged, actualChanged) + ", gitOnly=" //$NON-NLS-1$
					+ difference(actualChanged, checkChanged));
		}

		List<String> strictUnchanged = new ArrayList<>();
		List<String> verifiedChanged = new ArrayList<>();
		Set<String> requiredReasonCodes = new LinkedHashSet<>();
		for (Map.Entry<String, FileRules> entry : contract.requiredFiles().entrySet()) {
			String relative = entry.getKey();
			FileRules rules = entry.getValue();
			Path current = request.repository().resolve(relative);
			Path baseline = request.baselineSources().resolve(relative);
			if (!Files.isRegularFile(current) || !Files.isRegularFile(baseline)) {
				fail("Required corpus source is missing: " + relative); //$NON-NLS-1$
			}
			String baselineText = Files.readString(baseline, StandardCharsets.UTF_8);
			String currentText = Files.readString(current, StandardCharsets.UTF_8);
			requireMarkers(baselineText, rules.baselineMustContain(), "baseline " + relative); //$NON-NLS-1$

			if (request.mode() == Mode.STRICT && rules.strictUnchanged()) {
				if (actualChanged.contains(relative)) {
					fail("Strict mode changed quarantined corpus file: " + relative); //$NON-NLS-1$
				}
				if (!java.util.Arrays.equals(Files.readAllBytes(baseline), Files.readAllBytes(current))) {
					fail("Strict mode did not preserve quarantined file byte-for-byte: " + relative); //$NON-NLS-1$
				}
				if (currentText.contains(BEST_EFFORT_MARKER)) {
					fail("Strict mode inserted a best-effort marker into " + relative); //$NON-NLS-1$
				}
				strictUnchanged.add(relative);
				continue;
			}

			if (!actualChanged.contains(relative)) {
				fail("Expected migrated corpus file is absent from the patch: " + relative); //$NON-NLS-1$
			}
			requireMarkers(currentText, rules.migratedMustContain(), "migrated " + relative); //$NON-NLS-1$
			requireAbsent(currentText, rules.migratedMustNotContain(), "migrated " + relative); //$NON-NLS-1$
			if (request.mode() == Mode.BEST_EFFORT) {
				requireMarkers(currentText, rules.bestEffortMustContain(), "best-effort " + relative); //$NON-NLS-1$
				if (rules.strictReasonCode() != null) {
					requiredReasonCodes.add(rules.strictReasonCode());
				}
			} else if (currentText.contains(BEST_EFFORT_MARKER)) {
				fail("Strict mode inserted a best-effort marker into " + relative); //$NON-NLS-1$
			}
			verifiedChanged.add(relative);
		}

		if (request.mode() == Mode.BEST_EFFORT) {
			for (String reason : requiredReasonCodes) {
				if (!containsString(checkReport, reason) && !containsString(applyReport, reason)) {
					fail("Best-effort reports omit required reason code: " + reason); //$NON-NLS-1$
				}
			}
			if (!containsBooleanProperty(checkReport, "manualCompletionRequired", true) //$NON-NLS-1$
					&& !containsBooleanProperty(applyReport, "manualCompletionRequired", true)) { //$NON-NLS-1$
				fail("Best-effort reports do not state that manual completion is required"); //$NON-NLS-1$
			}
		}

		Collections.sort(actualNonJava);
		List<String> reasonCodes = new ArrayList<>(requiredReasonCodes);
		Collections.sort(reasonCodes);
		Result result = new Result(request.mode(), contract, actualChanged.size(), actualJava.size(),
				actualNonJava, verifiedChanged, strictUnchanged, reasonCodes);
		writeResult(request.output(), result);
		return result;
	}

	private static void writeResult(Path output, Result result) throws IOException {
		JsonObject summary = new JsonObject();
		summary.addProperty("result", "PASS"); //$NON-NLS-1$ //$NON-NLS-2$
		summary.addProperty("mode", result.mode().externalName()); //$NON-NLS-1$
		summary.addProperty("repository", result.contract().repository()); //$NON-NLS-1$
		summary.addProperty("ref", result.contract().ref()); //$NON-NLS-1$
		summary.addProperty("commit", result.contract().commit()); //$NON-NLS-1$
		summary.addProperty("project", result.contract().project()); //$NON-NLS-1$
		summary.addProperty("changedFiles", result.changedFiles()); //$NON-NLS-1$
		summary.addProperty("changedJavaFiles", result.changedJavaFiles()); //$NON-NLS-1$
		summary.add("changedNonJavaFiles", jsonArray(result.changedNonJavaFiles())); //$NON-NLS-1$
		summary.add("verifiedChangedCorpusFiles", jsonArray(result.verifiedChangedCorpusFiles())); //$NON-NLS-1$
		summary.add("strictlyQuarantinedCorpusFiles", //$NON-NLS-1$
				jsonArray(result.strictlyQuarantinedCorpusFiles()));
		summary.add("requiredReasonCodes", jsonArray(result.requiredReasonCodes())); //$NON-NLS-1$
		Path parent = output.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		Files.writeString(output, gson.toJson(summary) + System.lineSeparator(), StandardCharsets.UTF_8);
	}

	private static JsonArray jsonArray(List<String> values) {
		JsonArray array = new JsonArray();
		values.forEach(array::add);
		return array;
	}

	private static JsonObject readObject(Path path, String label) throws IOException {
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonElement value = JsonParser.parseReader(reader);
			if (!value.isJsonObject()) {
				fail(path + " does not contain a JSON object for " + label); //$NON-NLS-1$
			}
			return value.getAsJsonObject();
		} catch (RuntimeException failure) {
			throw new IllegalArgumentException("Cannot parse " + label + " " + path, failure); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	private static Set<String> changedFiles(Path path) throws IOException {
		Set<String> result = new LinkedHashSet<>();
		for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
			String normalized = normalizePath(raw);
			if (!normalized.isEmpty()) {
				result.add(normalized);
			}
		}
		return result;
	}

	private static Set<String> reportChangedFiles(JsonObject report, String project, String label) {
		JsonElement value = report.get("changedFiles"); //$NON-NLS-1$
		if (value == null || !value.isJsonArray()) {
			fail(label + " report has no valid changedFiles list"); //$NON-NLS-1$
		}
		Set<String> result = new LinkedHashSet<>();
		for (JsonElement item : value.getAsJsonArray()) {
			if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()
					|| item.getAsString().isBlank()) {
				fail(label + " report has no valid changedFiles list"); //$NON-NLS-1$
			}
			String normalized = normalizePath(item.getAsString());
			result.add(normalized.startsWith(project + "/") ? normalized : project + "/" + normalized); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return result;
	}

	private static void requireReport(JsonObject report, String mode, String label) {
		if (!mode.equals(optionalString(report, "mode", label + " report"))) { //$NON-NLS-1$ //$NON-NLS-2$
			fail(label + " report mode is not " + mode); //$NON-NLS-1$
		}
		if (integer(report, "errorCount", 0, label + " report") != 0) { //$NON-NLS-1$ //$NON-NLS-2$
			fail(label + " report contains cleanup errors"); //$NON-NLS-1$
		}
		JsonElement errors = report.get("errors"); //$NON-NLS-1$
		if (errors != null && (!errors.isJsonArray() || errors.getAsJsonArray().size() != 0)) {
			fail(label + " report contains error entries"); //$NON-NLS-1$
		}
		if (!report.has("planningDiagnostics")) { //$NON-NLS-1$
			fail(label + " report contains no planningDiagnostics"); //$NON-NLS-1$
		}
	}

	private static void requireMarkers(String text, List<String> markers, String label) {
		List<String> missing = markers.stream().filter(marker -> !text.contains(marker)).toList();
		if (!missing.isEmpty()) {
			fail(label + " is missing markers: " + missing); //$NON-NLS-1$
		}
	}

	private static void requireAbsent(String text, List<String> markers, String label) {
		List<String> present = markers.stream().filter(text::contains).toList();
		if (!present.isEmpty()) {
			fail(label + " still contains forbidden markers: " + present); //$NON-NLS-1$
		}
	}

	private static String requiredString(JsonObject object, String key, String label) {
		String value = optionalString(object, key, label);
		if (value == null) {
			fail(label + " has no " + key); //$NON-NLS-1$
		}
		return value;
	}

	private static String optionalString(JsonObject object, String key, String label) {
		JsonElement value = object.get(key);
		if (value == null || value.isJsonNull()) {
			return null;
		}
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
				|| value.getAsString().isBlank()) {
			fail(label + "." + key + " must be a non-empty string"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return value.getAsString();
	}

	private static JsonObject requiredObject(JsonObject object, String key, String label) {
		JsonElement value = object.get(key);
		if (value == null || !value.isJsonObject()) {
			fail(label + "." + key + " must be an object"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return value.getAsJsonObject();
	}

	private static List<String> stringList(JsonObject object, String key, String label) {
		JsonElement value = object.get(key);
		if (value == null || value.isJsonNull()) {
			return List.of();
		}
		if (!value.isJsonArray()) {
			fail(label + "." + key + " must be a list of non-empty strings"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		List<String> result = new ArrayList<>();
		for (JsonElement item : value.getAsJsonArray()) {
			if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()
					|| item.getAsString().isBlank()) {
				fail(label + "." + key + " must be a list of non-empty strings"); //$NON-NLS-1$ //$NON-NLS-2$
			}
			result.add(item.getAsString());
		}
		return result;
	}

	private static boolean booleanValue(JsonObject object, String key, boolean defaultValue, String label) {
		JsonElement value = object.get(key);
		if (value == null || value.isJsonNull()) {
			return defaultValue;
		}
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
			fail(label + "." + key + " must be a boolean"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return value.getAsBoolean();
	}

	private static int integer(JsonObject object, String key, int defaultValue, String label) {
		JsonElement value = object.get(key);
		if (value == null || value.isJsonNull()) {
			return defaultValue;
		}
		try {
			return value.getAsInt();
		} catch (RuntimeException failure) {
			throw new IllegalArgumentException(label + "." + key + " must be an integer", failure); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	private static String normalizePath(String raw) {
		String normalized = raw.trim().replace('\\', '/');
		while (normalized.startsWith("./")) { //$NON-NLS-1$
			normalized = normalized.substring(2);
		}
		while (normalized.startsWith("/")) { //$NON-NLS-1$
			normalized = normalized.substring(1);
		}
		return normalized;
	}

	private static Set<String> difference(Set<String> left, Set<String> right) {
		Set<String> result = new LinkedHashSet<>(left);
		result.removeAll(right);
		return result;
	}

	private static boolean containsString(JsonElement element, String expected) {
		if (element == null || element.isJsonNull()) {
			return false;
		}
		if (element.isJsonPrimitive()) {
			return element.getAsJsonPrimitive().isString() && expected.equals(element.getAsString());
		}
		if (element.isJsonArray()) {
			for (JsonElement child : element.getAsJsonArray()) {
				if (containsString(child, expected)) {
					return true;
				}
			}
		} else if (element.isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
				if (containsString(entry.getValue(), expected)) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean containsBooleanProperty(JsonElement element, String name, boolean expected) {
		if (element == null || element.isJsonNull()) {
			return false;
		}
		if (element.isJsonObject()) {
			JsonObject object = element.getAsJsonObject();
			JsonElement value = object.get(name);
			if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()
					&& value.getAsBoolean() == expected) {
				return true;
			}
			for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
				if (containsBooleanProperty(entry.getValue(), name, expected)) {
					return true;
				}
			}
		} else if (element.isJsonArray()) {
			for (JsonElement child : element.getAsJsonArray()) {
				if (containsBooleanProperty(child, name, expected)) {
					return true;
				}
			}
		}
		return false;
	}

	private static void fail(String message) {
		throw new IllegalArgumentException(message);
	}
}
