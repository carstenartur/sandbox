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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Verifies the named pinned JDT UI/JUnit 4 migration corpus through the same
 * Maven/JUnit authority used by local and CI execution.
 *
 * <p>The verifier checks report agreement, the exact changed-file set,
 * required source markers, strict byte-for-byte quarantine and best-effort
 * remediation evidence. It deliberately performs no checkout and retains no
 * AST or DOM objects.</p>
 *
 * @since 1.3.5
 */
final class JdtUiCorpusEvidenceVerifier {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String BEST_EFFORT_MARKER = "Sandbox JUnit migration gap"; //$NON-NLS-1$

	private JdtUiCorpusEvidenceVerifier() {
		// Utility class.
	}

	static Result verify(Request request) throws IOException {
		Objects.requireNonNull(request, "request"); //$NON-NLS-1$
		JsonObject contract = object(request.contract(), "corpus contract"); //$NON-NLS-1$
		String project = requiredString(contract, "project", "corpus contract"); //$NON-NLS-1$ //$NON-NLS-2$
		JsonObject requiredFiles = requiredObject(contract, "requiredFiles", "corpus contract"); //$NON-NLS-1$ //$NON-NLS-2$

		Set<String> actualChanged = changedFiles(request.changedFiles());
		Set<String> changedJava = actualChanged.stream()
				.filter(path -> path.endsWith(".java")) //$NON-NLS-1$
				.collect(java.util.stream.Collectors.toCollection(TreeSet::new));
		Set<String> changedNonJava = new TreeSet<>(actualChanged);
		changedNonJava.removeAll(changedJava);
		int minimumChangedJavaFiles = integer(contract, "minimumChangedJavaFiles", 0); //$NON-NLS-1$
		if (changedJava.size() < minimumChangedJavaFiles) {
			throw new IllegalArgumentException("Only " + changedJava.size() //$NON-NLS-1$
					+ " Java files changed; contract requires at least " + minimumChangedJavaFiles); //$NON-NLS-1$
		}

		JsonObject checkReport = object(request.checkReport(), "check report"); //$NON-NLS-1$
		JsonObject applyReport = object(request.applyReport(), "apply report"); //$NON-NLS-1$
		requireReport(checkReport, "check", "check"); //$NON-NLS-1$ //$NON-NLS-2$
		requireReport(applyReport, "apply", "apply"); //$NON-NLS-1$ //$NON-NLS-2$
		Set<String> checkChanged = reportChangedFiles(checkReport, project, "check"); //$NON-NLS-1$
		Set<String> applyChanged = reportChangedFiles(applyReport, project, "apply"); //$NON-NLS-1$
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

		List<String> verifiedChanged = new ArrayList<>();
		List<String> strictlyQuarantined = new ArrayList<>();
		Set<String> requiredReasonCodes = new TreeSet<>();
		List<String> relatives = requiredFiles.keySet().stream().sorted().toList();
		for (String relative : relatives) {
			JsonObject rules = requiredObject(requiredFiles, relative, "requiredFiles"); //$NON-NLS-1$
			Path current = request.repository().resolve(relative);
			Path baseline = request.baselineSources().resolve(relative);
			if (!Files.isRegularFile(current) || !Files.isRegularFile(baseline)) {
				throw new IllegalArgumentException("Required corpus source is missing: " + relative); //$NON-NLS-1$
			}
			String baselineText = Files.readString(baseline, StandardCharsets.UTF_8);
			String currentText = Files.readString(current, StandardCharsets.UTF_8);
			requireMarkers(baselineText, strings(rules, "baselineMustContain"), //$NON-NLS-1$
					"baseline " + relative); //$NON-NLS-1$

			boolean strictUnchanged = booleanValue(rules, "strictUnchanged", false); //$NON-NLS-1$
			if (request.mode() == Mode.STRICT && strictUnchanged) {
				if (actualChanged.contains(relative)) {
					throw new IllegalArgumentException("Strict mode changed quarantined corpus file: " + relative); //$NON-NLS-1$
				}
				if (Files.mismatch(baseline, current) != -1L) {
					throw new IllegalArgumentException(
							"Strict mode did not preserve quarantined file byte-for-byte: " + relative); //$NON-NLS-1$
				}
				if (currentText.contains(BEST_EFFORT_MARKER)) {
					throw new IllegalArgumentException("Strict mode inserted a best-effort marker into " + relative); //$NON-NLS-1$
				}
				strictlyQuarantined.add(relative);
				continue;
			}

			if (!actualChanged.contains(relative)) {
				throw new IllegalArgumentException("Expected migrated corpus file is absent from the patch: " //$NON-NLS-1$
						+ relative);
			}
			requireMarkers(currentText, strings(rules, "migratedMustContain"), //$NON-NLS-1$
					"migrated " + relative); //$NON-NLS-1$
			requireAbsent(currentText, strings(rules, "migratedMustNotContain"), //$NON-NLS-1$
					"migrated " + relative); //$NON-NLS-1$
			if (request.mode() == Mode.BEST_EFFORT) {
				requireMarkers(currentText, strings(rules, "bestEffortMustContain"), //$NON-NLS-1$
						"best-effort " + relative); //$NON-NLS-1$
				String reason = optionalString(rules, "strictReasonCode"); //$NON-NLS-1$
				if (reason != null) {
					requiredReasonCodes.add(reason);
				}
			} else if (currentText.contains(BEST_EFFORT_MARKER)) {
				throw new IllegalArgumentException("Strict mode inserted a best-effort marker into " + relative); //$NON-NLS-1$
			}
			verifiedChanged.add(relative);
		}

		if (request.mode() == Mode.BEST_EFFORT) {
			String evidence = GSON.toJson(List.of(checkReport, applyReport));
			List<String> missingReasons = requiredReasonCodes.stream()
					.filter(reason -> !evidence.contains(reason))
					.toList();
			if (!missingReasons.isEmpty()) {
				throw new IllegalArgumentException("Best-effort reports omit required reason codes: " //$NON-NLS-1$
						+ missingReasons);
			}
			if (!containsTrue(checkReport, "manualCompletionRequired") //$NON-NLS-1$
					&& !containsTrue(applyReport, "manualCompletionRequired")) { //$NON-NLS-1$
				throw new IllegalArgumentException(
						"Best-effort reports do not state that manual completion is required"); //$NON-NLS-1$
			}
		}

		return new Result("PASS", request.mode().value(), project, actualChanged.size(), changedJava.size(), //$NON-NLS-1$
				List.copyOf(changedNonJava), List.copyOf(verifiedChanged), List.copyOf(strictlyQuarantined),
				List.copyOf(requiredReasonCodes));
	}

	private static JsonObject object(Path path, String label) throws IOException {
		JsonElement value = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
		if (!value.isJsonObject()) {
			throw new IllegalArgumentException(label + " must contain a JSON object: " + path); //$NON-NLS-1$
		}
		return value.getAsJsonObject();
	}

	private static JsonObject requiredObject(JsonObject owner, String name, String label) {
		JsonElement value = owner.get(name);
		if (value == null || !value.isJsonObject()) {
			throw new IllegalArgumentException(label + "." + name + " must be a JSON object"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return value.getAsJsonObject();
	}

	private static String requiredString(JsonObject owner, String name, String label) {
		String value = optionalString(owner, name);
		if (value == null) {
			throw new IllegalArgumentException(label + "." + name + " must be a non-empty string"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return value;
	}

	private static String optionalString(JsonObject owner, String name) {
		JsonElement value = owner.get(name);
		if (value == null || value.isJsonNull()) {
			return null;
		}
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
				|| value.getAsString().isBlank()) {
			throw new IllegalArgumentException(name + " must be a non-empty string"); //$NON-NLS-1$
		}
		return value.getAsString();
	}

	private static int integer(JsonObject owner, String name, int fallback) {
		JsonElement value = owner.get(name);
		if (value == null || value.isJsonNull()) {
			return fallback;
		}
		try {
			return value.getAsInt();
		} catch (RuntimeException failure) {
			throw new IllegalArgumentException(name + " must be an integer", failure); //$NON-NLS-1$
		}
	}

	private static boolean booleanValue(JsonObject owner, String name, boolean fallback) {
		JsonElement value = owner.get(name);
		if (value == null || value.isJsonNull()) {
			return fallback;
		}
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
			throw new IllegalArgumentException(name + " must be a boolean"); //$NON-NLS-1$
		}
		return value.getAsBoolean();
	}

	private static List<String> strings(JsonObject owner, String name) {
		JsonElement value = owner.get(name);
		if (value == null || value.isJsonNull()) {
			return List.of();
		}
		if (!value.isJsonArray()) {
			throw new IllegalArgumentException(name + " must be a JSON array"); //$NON-NLS-1$
		}
		JsonArray array = value.getAsJsonArray();
		List<String> result = new ArrayList<>(array.size());
		for (JsonElement element : array) {
			if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()
					|| element.getAsString().isEmpty()) {
				throw new IllegalArgumentException(name + " entries must be non-empty strings"); //$NON-NLS-1$
			}
			result.add(element.getAsString());
		}
		return List.copyOf(result);
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

	private static Set<String> reportChangedFiles(JsonObject report, String project, String label) {
		JsonElement value = report.get("changedFiles"); //$NON-NLS-1$
		if (value == null || !value.isJsonArray()) {
			throw new IllegalArgumentException(label + " report has no valid changedFiles list"); //$NON-NLS-1$
		}
		Set<String> result = new TreeSet<>();
		for (JsonElement element : value.getAsJsonArray()) {
			if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()
					|| element.getAsString().isBlank()) {
				throw new IllegalArgumentException(label + " report has no valid changedFiles list"); //$NON-NLS-1$
			}
			String normalized = element.getAsString().replace('\\', '/');
			while (normalized.startsWith("./")) { //$NON-NLS-1$
				normalized = normalized.substring(2);
			}
			if (!normalized.startsWith(project + "/")) { //$NON-NLS-1$
				normalized = project + "/" + normalized; //$NON-NLS-1$
			}
			result.add(normalized);
		}
		return Set.copyOf(result);
	}

	private static void requireReport(JsonObject report, String expectedMode, String label) {
		if (!expectedMode.equals(requiredString(report, "mode", label + " report"))) { //$NON-NLS-1$ //$NON-NLS-2$
			throw new IllegalArgumentException(label + " report has the wrong mode"); //$NON-NLS-1$
		}
		if (integer(report, "errorCount", 0) != 0) { //$NON-NLS-1$
			throw new IllegalArgumentException(label + " report contains cleanup errors"); //$NON-NLS-1$
		}
		JsonElement errors = report.get("errors"); //$NON-NLS-1$
		if (errors == null || !errors.isJsonArray() || !errors.getAsJsonArray().isEmpty()) {
			throw new IllegalArgumentException(label + " report contains error entries"); //$NON-NLS-1$
		}
		if (!report.has("planningDiagnostics")) { //$NON-NLS-1$
			throw new IllegalArgumentException(label + " report contains no planningDiagnostics"); //$NON-NLS-1$
		}
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

	private static boolean containsTrue(JsonElement value, String name) {
		if (value == null || value.isJsonNull()) {
			return false;
		}
		if (value.isJsonObject()) {
			JsonObject object = value.getAsJsonObject();
			JsonElement member = object.get(name);
			if (member != null && member.isJsonPrimitive() && member.getAsJsonPrimitive().isBoolean()
					&& member.getAsBoolean()) {
				return true;
			}
			return object.entrySet().stream().anyMatch(entry -> containsTrue(entry.getValue(), name));
		}
		if (value.isJsonArray()) {
			for (JsonElement element : value.getAsJsonArray()) {
				if (containsTrue(element, name)) {
					return true;
				}
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
			throw new IllegalArgumentException("Mode must be strict or best-effort: " + value); //$NON-NLS-1$
		}
	}

	record Request(Path repository, Path baselineSources, Path contract, Mode mode, Path changedFiles,
			Path checkReport, Path applyReport) {
		Request {
			Objects.requireNonNull(repository, "repository"); //$NON-NLS-1$
			Objects.requireNonNull(baselineSources, "baselineSources"); //$NON-NLS-1$
			Objects.requireNonNull(contract, "contract"); //$NON-NLS-1$
			Objects.requireNonNull(mode, "mode"); //$NON-NLS-1$
			Objects.requireNonNull(changedFiles, "changedFiles"); //$NON-NLS-1$
			Objects.requireNonNull(checkReport, "checkReport"); //$NON-NLS-1$
			Objects.requireNonNull(applyReport, "applyReport"); //$NON-NLS-1$
		}
	}

	record Result(String result, String mode, String project, int changedFiles, int changedJavaFiles,
			List<String> changedNonJavaFiles, List<String> verifiedChangedCorpusFiles,
			List<String> strictlyQuarantinedCorpusFiles, List<String> requiredReasonCodes) {
		Result {
			changedNonJavaFiles = sortedCopy(changedNonJavaFiles);
			verifiedChangedCorpusFiles = sortedCopy(verifiedChangedCorpusFiles);
			strictlyQuarantinedCorpusFiles = sortedCopy(strictlyQuarantinedCorpusFiles);
			requiredReasonCodes = sortedCopy(requiredReasonCodes);
		}

		void write(Path output) throws IOException {
			Objects.requireNonNull(output, "output"); //$NON-NLS-1$
			Path parent = output.toAbsolutePath().normalize().getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Files.writeString(output, GSON.toJson(this) + System.lineSeparator(), StandardCharsets.UTF_8);
		}

		private static List<String> sortedCopy(List<String> values) {
			return values.stream().sorted(Comparator.naturalOrder()).toList();
		}
	}
}
