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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * Verifies the source, report and quarantine evidence of the pinned JDT UI
 * JUnit migration corpus.
 *
 * <p>The verifier is deliberately independent of GitHub Actions. A retained
 * upstream workspace and CI can invoke the same Java authority through Maven,
 * while shell code remains responsible only for provisioning and command
 * orchestration.</p>
 *
 * @since 1.3.5
 */
final class JdtUiCorpusEvidenceVerifier {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String GAP_MARKER = "Sandbox JUnit migration gap"; //$NON-NLS-1$

	private JdtUiCorpusEvidenceVerifier() {
		// Utility class.
	}

	static Verification verify(Path repository, Path baselineSources, Path contractPath,
			Mode mode, Path changedFilesPath, Path checkReportPath, Path applyReportPath) throws IOException {
		Objects.requireNonNull(repository, "repository"); //$NON-NLS-1$
		Objects.requireNonNull(baselineSources, "baselineSources"); //$NON-NLS-1$
		Objects.requireNonNull(contractPath, "contractPath"); //$NON-NLS-1$
		Objects.requireNonNull(mode, "mode"); //$NON-NLS-1$
		Objects.requireNonNull(changedFilesPath, "changedFilesPath"); //$NON-NLS-1$
		Objects.requireNonNull(checkReportPath, "checkReportPath"); //$NON-NLS-1$
		Objects.requireNonNull(applyReportPath, "applyReportPath"); //$NON-NLS-1$

		JsonObject contract = readObject(contractPath, "Corpus contract"); //$NON-NLS-1$
		String project = requiredString(contract, "project", "Corpus contract"); //$NON-NLS-1$ //$NON-NLS-2$
		JsonObject requiredFiles = requiredObject(contract, "requiredFiles", "Corpus contract"); //$NON-NLS-1$ //$NON-NLS-2$
		if (requiredFiles.size() == 0) {
			throw new IllegalArgumentException("Corpus contract has no requiredFiles entries"); //$NON-NLS-1$
		}
		int minimumChangedJavaFiles = nonNegativeInteger(contract.get("minimumChangedJavaFiles"), //$NON-NLS-1$
				"Corpus contract minimumChangedJavaFiles", 0); //$NON-NLS-1$

		Set<String> actualChanged = changedFiles(changedFilesPath);
		Set<String> actualJava = new TreeSet<>();
		Set<String> actualNonJava = new TreeSet<>();
		for (String path : actualChanged) {
			(path.endsWith(".java") ? actualJava : actualNonJava).add(path); //$NON-NLS-1$
		}
		if (actualJava.size() < minimumChangedJavaFiles) {
			throw new IllegalArgumentException("Only " + actualJava.size() //$NON-NLS-1$
					+ " Java files changed; contract requires at least " + minimumChangedJavaFiles); //$NON-NLS-1$
		}

		JsonObject checkReport = readObject(checkReportPath, "Check report"); //$NON-NLS-1$
		JsonObject applyReport = readObject(applyReportPath, "Apply report"); //$NON-NLS-1$
		requireReport(checkReport, "check", "Check"); //$NON-NLS-1$ //$NON-NLS-2$
		requireReport(applyReport, "apply", "Apply"); //$NON-NLS-1$ //$NON-NLS-2$
		Set<String> checkChanged = reportChangedFiles(checkReport, project, "Check"); //$NON-NLS-1$
		Set<String> applyChanged = reportChangedFiles(applyReport, project, "Apply"); //$NON-NLS-1$
		if (!checkChanged.equals(applyChanged)) {
			throw new IllegalArgumentException("Cleanup check and apply report different changed-file sets"); //$NON-NLS-1$
		}
		if (!checkChanged.equals(actualChanged)) {
			Set<String> reportOnly = new TreeSet<>(checkChanged);
			reportOnly.removeAll(actualChanged);
			Set<String> gitOnly = new TreeSet<>(actualChanged);
			gitOnly.removeAll(checkChanged);
			throw new IllegalArgumentException("Cleanup reports and Git migration patch differ: reportOnly=" //$NON-NLS-1$
					+ reportOnly + ", gitOnly=" + gitOnly); //$NON-NLS-1$
		}

		List<String> strictUnchanged = new ArrayList<>();
		List<String> verifiedChanged = new ArrayList<>();
		Set<String> requiredReasonCodes = new TreeSet<>();
		for (Map.Entry<String, JsonElement> entry : requiredFiles.entrySet().stream()
				.sorted(Map.Entry.comparingByKey()).toList()) {
			String relative = entry.getKey();
			if (relative.isBlank() || !entry.getValue().isJsonObject()) {
				throw new IllegalArgumentException("Invalid requiredFiles entry: " + relative); //$NON-NLS-1$
			}
			JsonObject rules = entry.getValue().getAsJsonObject();
			Path current = resolveWithin(repository, relative, "Repository corpus source"); //$NON-NLS-1$
			Path baseline = resolveWithin(baselineSources, relative, "Baseline corpus source"); //$NON-NLS-1$
			if (!Files.isRegularFile(current) || !Files.isRegularFile(baseline)) {
				throw new IllegalArgumentException("Required corpus source is missing: " + relative); //$NON-NLS-1$
			}
			String baselineText = Files.readString(baseline, StandardCharsets.UTF_8);
			String currentText = Files.readString(current, StandardCharsets.UTF_8);
			requireMarkers(baselineText, optionalStringList(rules, "baselineMustContain"), //$NON-NLS-1$
					"Baseline " + relative); //$NON-NLS-1$

			boolean unchangedInStrict = booleanTrue(rules.get("strictUnchanged")); //$NON-NLS-1$
			if (mode == Mode.STRICT && unchangedInStrict) {
				if (actualChanged.contains(relative)) {
					throw new IllegalArgumentException("Strict mode changed quarantined corpus file: " + relative); //$NON-NLS-1$
				}
				if (!java.util.Arrays.equals(Files.readAllBytes(baseline), Files.readAllBytes(current))) {
					throw new IllegalArgumentException("Strict mode did not preserve quarantined file byte-for-byte: " //$NON-NLS-1$
							+ relative);
				}
				if (currentText.contains(GAP_MARKER)) {
					throw new IllegalArgumentException("Strict mode inserted a best-effort marker into " + relative); //$NON-NLS-1$
				}
				strictUnchanged.add(relative);
				continue;
			}

			if (!actualChanged.contains(relative)) {
				throw new IllegalArgumentException("Expected migrated corpus file is absent from the patch: " //$NON-NLS-1$
						+ relative);
			}
			requireMarkers(currentText, optionalStringList(rules, "migratedMustContain"), //$NON-NLS-1$
					"Migrated " + relative); //$NON-NLS-1$
			requireAbsent(currentText, optionalStringList(rules, "migratedMustNotContain"), //$NON-NLS-1$
					"Migrated " + relative); //$NON-NLS-1$
			if (mode == Mode.BEST_EFFORT) {
				requireMarkers(currentText, optionalStringList(rules, "bestEffortMustContain"), //$NON-NLS-1$
						"Best-effort " + relative); //$NON-NLS-1$
				JsonElement reason = rules.get("strictReasonCode"); //$NON-NLS-1$
				if (reason != null) {
					requiredReasonCodes.add(requiredString(reason, relative + ".strictReasonCode")); //$NON-NLS-1$
				}
			} else if (currentText.contains(GAP_MARKER)) {
				throw new IllegalArgumentException("Strict mode inserted a best-effort marker into " + relative); //$NON-NLS-1$
			}
			verifiedChanged.add(relative);
		}

		if (mode == Mode.BEST_EFFORT) {
			for (String reason : requiredReasonCodes) {
				if (!containsString(checkReport, reason) && !containsString(applyReport, reason)) {
					throw new IllegalArgumentException("Best-effort reports omit required reason code: " + reason); //$NON-NLS-1$
				}
			}
			if (!containsTrueProperty(checkReport, "manualCompletionRequired") //$NON-NLS-1$
					&& !containsTrueProperty(applyReport, "manualCompletionRequired")) { //$NON-NLS-1$
				throw new IllegalArgumentException(
						"Best-effort reports do not state that manual completion is required"); //$NON-NLS-1$
			}
		}

		return new Verification("PASS", mode.value(), project, actualChanged.size(), actualJava.size(), //$NON-NLS-1$
				List.copyOf(actualNonJava), List.copyOf(verifiedChanged), List.copyOf(strictUnchanged),
				List.copyOf(requiredReasonCodes));
	}

	private static JsonObject readObject(Path path, String label) throws IOException {
		JsonElement parsed = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
		if (!parsed.isJsonObject()) {
			throw new IllegalArgumentException(label + " must contain a JSON object: " + path); //$NON-NLS-1$
		}
		return parsed.getAsJsonObject();
	}

	private static Set<String> changedFiles(Path path) throws IOException {
		Set<String> result = new TreeSet<>();
		for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
			String normalized = raw.strip().replace('\\', '/');
			if (!normalized.isEmpty()) {
				result.add(normalized);
			}
		}
		return Set.copyOf(result);
	}

	private static void requireReport(JsonObject report, String expectedMode, String label) {
		if (!expectedMode.equals(optionalString(report.get("mode")))) { //$NON-NLS-1$
			throw new IllegalArgumentException(label + " report mode is " + report.get("mode") //$NON-NLS-1$ //$NON-NLS-2$
					+ ", expected " + expectedMode); //$NON-NLS-1$
		}
		if (nonNegativeInteger(report.get("errorCount"), label + " report errorCount", 0) != 0) { //$NON-NLS-1$ //$NON-NLS-2$
			throw new IllegalArgumentException(label + " report contains cleanup errors"); //$NON-NLS-1$
		}
		JsonElement errors = report.get("errors"); //$NON-NLS-1$
		if (errors != null && (!errors.isJsonArray() || errors.getAsJsonArray().size() != 0)) {
			throw new IllegalArgumentException(label + " report contains error entries"); //$NON-NLS-1$
		}
		if (!report.has("planningDiagnostics")) { //$NON-NLS-1$
			throw new IllegalArgumentException(label + " report contains no planningDiagnostics"); //$NON-NLS-1$
		}
	}

	private static Set<String> reportChangedFiles(JsonObject report, String project, String label) {
		JsonElement values = report.get("changedFiles"); //$NON-NLS-1$
		if (values == null || !values.isJsonArray()) {
			throw new IllegalArgumentException(label + " report has no valid changedFiles list"); //$NON-NLS-1$
		}
		Set<String> result = new TreeSet<>();
		for (JsonElement value : values.getAsJsonArray()) {
			String path = requiredString(value, label + " report changedFiles entry"); //$NON-NLS-1$
			String normalized = stripRelativePrefix(path.replace('\\', '/'));
			result.add(normalized.startsWith(project + "/") ? normalized : project + "/" + normalized); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return Set.copyOf(result);
	}

	private static String stripRelativePrefix(String value) {
		int index = 0;
		while (index < value.length() && (value.charAt(index) == '.' || value.charAt(index) == '/')) {
			index++;
		}
		return value.substring(index);
	}

	private static Path resolveWithin(Path root, String relative, String label) {
		Path normalizedRoot = root.toAbsolutePath().normalize();
		Path resolved = normalizedRoot.resolve(relative).normalize();
		if (!resolved.startsWith(normalizedRoot)) {
			throw new IllegalArgumentException(label + " escapes its root: " + relative); //$NON-NLS-1$
		}
		return resolved;
	}

	private static JsonObject requiredObject(JsonObject object, String member, String label) {
		JsonElement value = object.get(member);
		if (value == null || !value.isJsonObject()) {
			throw new IllegalArgumentException(label + " has no " + member + " object"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return value.getAsJsonObject();
	}

	private static String requiredString(JsonObject object, String member, String label) {
		return requiredString(object.get(member), label + " " + member); //$NON-NLS-1$
	}

	private static String requiredString(JsonElement value, String label) {
		String result = optionalString(value);
		if (result == null || result.isBlank()) {
			throw new IllegalArgumentException(label + " must be a non-empty string"); //$NON-NLS-1$
		}
		return result;
	}

	private static String optionalString(JsonElement value) {
		if (value == null || !value.isJsonPrimitive()) {
			return null;
		}
		JsonPrimitive primitive = value.getAsJsonPrimitive();
		return primitive.isString() ? primitive.getAsString() : null;
	}

	private static int nonNegativeInteger(JsonElement value, String label, int fallback) {
		if (value == null) {
			return fallback;
		}
		if (!value.isJsonPrimitive()) {
			throw new IllegalArgumentException(label + " must be a non-negative integer"); //$NON-NLS-1$
		}
		try {
			int result = value.getAsInt();
			if (result < 0) {
				throw new IllegalArgumentException(label + " must be a non-negative integer"); //$NON-NLS-1$
			}
			return result;
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException(label + " must be a non-negative integer", exception); //$NON-NLS-1$
		}
	}

	private static boolean booleanTrue(JsonElement value) {
		return value != null && value.isJsonPrimitive()
				&& value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean();
	}

	private static List<String> optionalStringList(JsonObject object, String member) {
		JsonElement value = object.get(member);
		return value == null ? List.of() : stringList(value, member);
	}

	private static List<String> stringList(JsonElement value, String label) {
		if (!value.isJsonArray()) {
			throw new IllegalArgumentException(label + " must be a list of non-empty strings"); //$NON-NLS-1$
		}
		JsonArray array = value.getAsJsonArray();
		List<String> result = new ArrayList<>(array.size());
		for (JsonElement entry : array) {
			result.add(requiredString(entry, label + " entry")); //$NON-NLS-1$
		}
		return List.copyOf(result);
	}

	private static void requireMarkers(String text, List<String> markers, String label) {
		List<String> missing = markers.stream().filter(marker -> !text.contains(marker)).toList();
		if (!missing.isEmpty()) {
			throw new IllegalArgumentException(label + " is missing markers: " + missing); //$NON-NLS-1$
		}
	}

	private static void requireAbsent(String text, List<String> markers, String label) {
		List<String> present = markers.stream().filter(text::contains).toList();
		if (!present.isEmpty()) {
			throw new IllegalArgumentException(label + " still contains forbidden markers: " + present); //$NON-NLS-1$
		}
	}

	private static boolean containsString(JsonElement element, String expected) {
		if (element == null || element.isJsonNull()) {
			return false;
		}
		if (element.isJsonPrimitive()) {
			JsonPrimitive primitive = element.getAsJsonPrimitive();
			return primitive.isString() && primitive.getAsString().contains(expected);
		}
		if (element.isJsonArray()) {
			for (JsonElement child : element.getAsJsonArray()) {
				if (containsString(child, expected)) {
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

	private static boolean containsTrueProperty(JsonElement element, String property) {
		if (element == null || element.isJsonNull() || element.isJsonPrimitive()) {
			return false;
		}
		if (element.isJsonArray()) {
			for (JsonElement child : element.getAsJsonArray()) {
				if (containsTrueProperty(child, property)) {
					return true;
				}
			}
			return false;
		}
		JsonObject object = element.getAsJsonObject();
		if (booleanTrue(object.get(property))) {
			return true;
		}
		for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
			if (containsTrueProperty(entry.getValue(), property)) {
				return true;
			}
		}
		return false;
	}

	enum Mode {
		STRICT("strict"), //$NON-NLS-1$
		BEST_EFFORT("best-effort"); //$NON-NLS-1$

		private final String value;

		Mode(String value) {
			this.value = value;
		}

		String value() {
			return value;
		}

		static Mode parse(String value) {
			for (Mode mode : values()) {
				if (mode.value.equals(value)) {
					return mode;
				}
			}
			throw new IllegalArgumentException("Unsupported JDT UI corpus mode: " + value); //$NON-NLS-1$
		}
	}

	record Verification(String result, String mode, String project, int changedFiles,
			int changedJavaFiles, List<String> changedNonJavaFiles,
			List<String> verifiedChangedCorpusFiles, List<String> strictlyQuarantinedCorpusFiles,
			List<String> requiredReasonCodes) {
		Verification {
			Objects.requireNonNull(result, "result"); //$NON-NLS-1$
			Objects.requireNonNull(mode, "mode"); //$NON-NLS-1$
			Objects.requireNonNull(project, "project"); //$NON-NLS-1$
			changedNonJavaFiles = List.copyOf(changedNonJavaFiles);
			verifiedChangedCorpusFiles = List.copyOf(verifiedChangedCorpusFiles);
			strictlyQuarantinedCorpusFiles = List.copyOf(strictlyQuarantinedCorpusFiles);
			requiredReasonCodes = List.copyOf(requiredReasonCodes);
		}

		void write(Path output) throws IOException {
			Objects.requireNonNull(output, "output"); //$NON-NLS-1$
			Path parent = output.toAbsolutePath().normalize().getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Files.writeString(output, GSON.toJson(this) + System.lineSeparator(), StandardCharsets.UTF_8);
		}
	}
}
