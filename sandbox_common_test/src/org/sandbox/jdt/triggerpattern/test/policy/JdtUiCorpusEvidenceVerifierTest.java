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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Exercises strict, best-effort and retained-workspace corpus evidence through
 * the same verifier used by the real JDT UI runner.
 *
 * @since 1.3.4
 */
public class JdtUiCorpusEvidenceVerifierTest {

	private static final String PROJECT = "org.eclipse.jdt.ui.tests"; //$NON-NLS-1$
	private static final String PARAMETERIZED_SOURCE =
			"org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/ui/tests/quickfix/ConvertLoopOperationTest.java"; //$NON-NLS-1$
	private static final Gson PRETTY_JSON = new GsonBuilder().setPrettyPrinting().create();

	@TempDir
	Path temporaryDirectory;

	@Test
	public void strictEvidenceAcceptsChangedSourcesAndByteExactQuarantine() throws Exception {
		EvidenceFixture fixture = fixture(JdtUiCorpusEvidenceVerifier.Mode.STRICT, true);
		JdtUiCorpusEvidenceVerifier.Summary summary =
				JdtUiCorpusEvidenceVerifier.verify(fixture.request());

		assertEquals(4, summary.changedJavaFiles());
		assertEquals(List.of(PARAMETERIZED_SOURCE),
				summary.strictlyQuarantinedCorpusFiles());
		assertTrue(Files.readString(fixture.output(), StandardCharsets.UTF_8)
				.contains("\"result\": \"PASS\"")); //$NON-NLS-1$
	}

	@Test
	public void strictEvidenceRejectsModifiedQuarantine() throws Exception {
		EvidenceFixture fixture = fixture(JdtUiCorpusEvidenceVerifier.Mode.STRICT, true);
		Files.writeString(fixture.repository().resolve(PARAMETERIZED_SOURCE),
				"// accidental strict partial migration\n", StandardCharsets.UTF_8, //$NON-NLS-1$
				StandardOpenOption.APPEND);

		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> JdtUiCorpusEvidenceVerifier.verify(fixture.request()));
		assertTrue(failure.getMessage().contains("byte-for-byte")); //$NON-NLS-1$
	}

	@Test
	public void bestEffortEvidenceRequiresStructuredManualCompletion() throws Exception {
		EvidenceFixture fixture =
				fixture(JdtUiCorpusEvidenceVerifier.Mode.BEST_EFFORT, true);
		JdtUiCorpusEvidenceVerifier.Summary summary =
				JdtUiCorpusEvidenceVerifier.verify(fixture.request());

		assertEquals(5, summary.changedJavaFiles());
		assertEquals(List.of("PARAMETERIZED_FIELD_INJECTION"), //$NON-NLS-1$
				summary.requiredReasonCodes());
		assertTrue(summary.strictlyQuarantinedCorpusFiles().isEmpty());
	}

	@Test
	public void bestEffortEvidenceRejectsMissingManualCompletion() throws Exception {
		EvidenceFixture fixture =
				fixture(JdtUiCorpusEvidenceVerifier.Mode.BEST_EFFORT, false);

		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> JdtUiCorpusEvidenceVerifier.verify(fixture.request()));
		assertTrue(failure.getMessage().contains("manual completion")); //$NON-NLS-1$
	}

	@Test
	public void retainedWorkspaceEvidenceMatchesContract() throws Exception {
		String repository =
				System.getProperty(JdtUiCorpusEvidenceVerifier.REPOSITORY_PROPERTY);
		assumeTrue(repository != null && !repository.isBlank(),
				"Real JDT UI evidence properties are supplied only by the retained-workspace runner"); //$NON-NLS-1$

		JdtUiCorpusEvidenceVerifier.Summary summary =
				JdtUiCorpusEvidenceVerifier.verifyConfiguredEvidence();
		assertEquals(PROJECT, summary.project());
		assertTrue(summary.changedJavaFiles() >= 4);
	}

	private EvidenceFixture fixture(JdtUiCorpusEvidenceVerifier.Mode mode,
			boolean manualCompletion) throws Exception {
		Path root = JdtUiCorpusEvidenceVerifier.repositoryRoot();
		Path contract = root.resolve("qa/upstream-jdt/jdt-ui-junit4-corpus.json"); //$NON-NLS-1$
		JsonObject definition;
		try (var reader = Files.newBufferedReader(contract, StandardCharsets.UTF_8)) {
			definition = JsonParser.parseReader(reader).getAsJsonObject();
		}

		Path repository = temporaryDirectory.resolve("repository-" + mode.argument()); //$NON-NLS-1$
		Path baseline = temporaryDirectory.resolve("baseline-" + mode.argument()); //$NON-NLS-1$
		Path changedFiles = temporaryDirectory.resolve("changed-" + mode.argument() + ".txt"); //$NON-NLS-1$ //$NON-NLS-2$
		Path checkReport = temporaryDirectory.resolve("check-" + mode.argument() + ".json"); //$NON-NLS-1$ //$NON-NLS-2$
		Path applyReport = temporaryDirectory.resolve("apply-" + mode.argument() + ".json"); //$NON-NLS-1$ //$NON-NLS-2$
		Path output = temporaryDirectory.resolve("result-" + mode.argument() + ".json"); //$NON-NLS-1$ //$NON-NLS-2$

		List<String> changed = new ArrayList<>();
		JsonObject required = definition.getAsJsonObject("requiredFiles"); //$NON-NLS-1$
		required.entrySet().stream()
				.sorted(Map.Entry.comparingByKey())
				.forEach(entry -> populateSource(repository, baseline, changed,
						mode, entry.getKey(), entry.getValue().getAsJsonObject()));
		Files.writeString(changedFiles, String.join("\n", changed) + "\n", //$NON-NLS-1$ //$NON-NLS-2$
				StandardCharsets.UTF_8);
		writeReport(checkReport, "check", changed, mode, manualCompletion); //$NON-NLS-1$
		writeReport(applyReport, "apply", changed, mode, manualCompletion); //$NON-NLS-1$

		JdtUiCorpusEvidenceVerifier.Request request =
				new JdtUiCorpusEvidenceVerifier.Request(repository, baseline, contract, mode,
						changedFiles, checkReport, applyReport, output);
		return new EvidenceFixture(repository, output, request);
	}

	private static void populateSource(Path repository, Path baseline,
			List<String> changed, JdtUiCorpusEvidenceVerifier.Mode mode,
			String relative, JsonObject rules) {
		try {
			Path baselineSource = baseline.resolve(relative);
			Path currentSource = repository.resolve(relative);
			Files.createDirectories(baselineSource.getParent());
			Files.createDirectories(currentSource.getParent());
			String baselineText = String.join("\n", strings(rules, "baselineMustContain")) //$NON-NLS-1$ //$NON-NLS-2$
					+ "\n"; //$NON-NLS-1$
			Files.writeString(baselineSource, baselineText, StandardCharsets.UTF_8);

			boolean strictQuarantine = mode == JdtUiCorpusEvidenceVerifier.Mode.STRICT
					&& rules.has("strictUnchanged") //$NON-NLS-1$
					&& rules.get("strictUnchanged").getAsBoolean(); //$NON-NLS-1$
			String currentText;
			if (strictQuarantine) {
				currentText = baselineText;
			} else {
				List<String> markers = new ArrayList<>(strings(rules, "migratedMustContain")); //$NON-NLS-1$
				if (mode == JdtUiCorpusEvidenceVerifier.Mode.BEST_EFFORT) {
					markers.addAll(strings(rules, "bestEffortMustContain")); //$NON-NLS-1$
				}
				currentText = String.join("\n", markers) + "\n"; //$NON-NLS-1$ //$NON-NLS-2$
				changed.add(relative);
			}
			Files.writeString(currentSource, currentText, StandardCharsets.UTF_8);
		} catch (IOException failure) {
			throw new IllegalStateException("Could not populate synthetic corpus source " //$NON-NLS-1$
					+ relative, failure);
		}
	}

	private static List<String> strings(JsonObject object, String name) {
		JsonElement value = object.get(name);
		if (value == null || value.isJsonNull()) {
			return List.of();
		}
		List<String> result = new ArrayList<>();
		for (JsonElement item : value.getAsJsonArray()) {
			result.add(item.getAsString());
		}
		return List.copyOf(result);
	}

	private static void writeReport(Path path, String reportMode, List<String> changed,
			JdtUiCorpusEvidenceVerifier.Mode mode, boolean manualCompletion)
			throws IOException {
		JsonObject report = new JsonObject();
		report.addProperty("schemaVersion", "1"); //$NON-NLS-1$ //$NON-NLS-2$
		report.addProperty("tool", "sandbox-project-cleanup"); //$NON-NLS-1$ //$NON-NLS-2$
		report.addProperty("project", PROJECT); //$NON-NLS-1$
		report.addProperty("mode", reportMode); //$NON-NLS-1$
		report.addProperty("filesProcessed", 25); //$NON-NLS-1$
		report.addProperty("filesChanged", changed.size()); //$NON-NLS-1$
		JsonArray changedFiles = new JsonArray();
		changed.stream()
				.sorted(Comparator.naturalOrder())
				.map(JdtUiCorpusEvidenceVerifierTest::projectRelative)
				.forEach(changedFiles::add);
		report.add("changedFiles", changedFiles); //$NON-NLS-1$

		if (mode == JdtUiCorpusEvidenceVerifier.Mode.BEST_EFFORT) {
			JsonObject diagnostics = new JsonObject();
			diagnostics.addProperty("bestEffort", true); //$NON-NLS-1$
			if (manualCompletion) {
				diagnostics.addProperty("manualCompletionRequired", true); //$NON-NLS-1$
			}
			JsonArray gaps = new JsonArray();
			JsonObject gap = new JsonObject();
			gap.addProperty("candidateId", "parameterized:ConvertLoopOperationTest"); //$NON-NLS-1$ //$NON-NLS-2$
			gap.addProperty("reasonCode", "PARAMETERIZED_FIELD_INJECTION"); //$NON-NLS-1$ //$NON-NLS-2$
			gap.addProperty("explanation", "Synthetic contract evidence"); //$NON-NLS-1$ //$NON-NLS-2$
			gap.addProperty("remediation", "Use explicit Jupiter method arguments"); //$NON-NLS-1$ //$NON-NLS-2$
			gaps.add(gap);
			diagnostics.add("gaps", gaps); //$NON-NLS-1$
			report.add("planningDiagnostics", diagnostics); //$NON-NLS-1$
		} else {
			JsonArray diagnostics = new JsonArray();
			JsonObject cleanup = new JsonObject();
			cleanup.addProperty("cleanupId", "junit"); //$NON-NLS-1$ //$NON-NLS-2$
			cleanup.add("candidates", new JsonArray()); //$NON-NLS-1$
			diagnostics.add(cleanup);
			report.add("planningDiagnostics", diagnostics); //$NON-NLS-1$
		}
		report.addProperty("errorCount", 0); //$NON-NLS-1$
		report.add("errors", new JsonArray()); //$NON-NLS-1$
		Files.writeString(path, PRETTY_JSON.toJson(report) + "\n", StandardCharsets.UTF_8); //$NON-NLS-1$
	}

	private static String projectRelative(String path) {
		String prefix = PROJECT + "/"; //$NON-NLS-1$
		return path.startsWith(prefix) ? path.substring(prefix.length()) : path;
	}

	private record EvidenceFixture(Path repository, Path output,
			JdtUiCorpusEvidenceVerifier.Request request) {
	}
}
