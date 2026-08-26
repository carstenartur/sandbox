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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Keeps the pinned JDT UI JUnit 4 migration descriptor, profiles, runner and
 * evidence classification under Maven/JUnit authority.
 *
 * @since 1.3.4
 */
public class JdtUiMigrationContractTest {

	private static final String PROJECT = "org.eclipse.jdt.ui.tests"; //$NON-NLS-1$
	private static final String PINNED_COMMIT = "c922f757b27b7e2b6215db383cec5f8aafd13227"; //$NON-NLS-1$
	private static final String PARAMETERIZED_FILE = PROJECT
			+ "/ui/org/eclipse/jdt/ui/tests/quickfix/ConvertLoopOperationTest.java"; //$NON-NLS-1$
	private static final Set<String> EXPECTED_FILES = Set.of(
			PROJECT + "/ui/org/eclipse/jdt/ui/tests/core/rules/JUnitSourceSetup.java", //$NON-NLS-1$
			PROJECT + "/ui/org/eclipse/jdt/ui/tests/core/rules/LeakTestSetup.java", //$NON-NLS-1$
			PROJECT + "/ui/org/eclipse/jdt/ui/tests/search/FileAdapterTest.java", //$NON-NLS-1$
			PROJECT + "/ui/org/eclipse/jdt/ui/tests/search/SearchLeakTestWrapper.java", //$NON-NLS-1$
			PARAMETERIZED_FILE);
	private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

	@Test
	public void descriptorProfilesRunnerAndWorkflowAreOneContract() throws Exception {
		Path root = repositoryRoot();
		JsonObject contract = JdtUiCorpusEvidenceVerifier.readObject(
				root.resolve("qa/upstream-jdt/jdt-ui-junit4-corpus.json")); //$NON-NLS-1$

		assertEquals("https://github.com/eclipse-jdt/eclipse.jdt.ui.git", //$NON-NLS-1$
				contract.get("repository").getAsString()); //$NON-NLS-1$
		assertEquals("R4_40", contract.get("ref").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(PINNED_COMMIT, contract.get("commit").getAsString()); //$NON-NLS-1$
		assertEquals(PROJECT, contract.get("project").getAsString()); //$NON-NLS-1$

		JsonObject required = contract.getAsJsonObject("requiredFiles"); //$NON-NLS-1$
		Set<String> requiredPaths = required.entrySet().stream()
				.map(Map.Entry::getKey)
				.collect(java.util.stream.Collectors.toUnmodifiableSet());
		assertEquals(EXPECTED_FILES, requiredPaths);
		assertTrue(contract.get("minimumChangedJavaFiles").getAsInt() >= 4); //$NON-NLS-1$
		JsonObject difficult = required.getAsJsonObject(PARAMETERIZED_FILE);
		assertTrue(difficult.get("strictUnchanged").getAsBoolean()); //$NON-NLS-1$
		assertEquals("PARAMETERIZED_FIELD_INJECTION", //$NON-NLS-1$
				difficult.get("strictReasonCode").getAsString()); //$NON-NLS-1$
		assertTrue(difficult.getAsJsonArray("bestEffortMustContain").size() > 0); //$NON-NLS-1$

		assertProfile(root.resolve("qa/upstream-jdt/junit4-to-jupiter.properties"), false); //$NON-NLS-1$
		assertProfile(root.resolve("qa/upstream-jdt/junit4-to-jupiter-best-effort.properties"), true); //$NON-NLS-1$

		String runner = read(root, "qa/upstream-jdt/run-jdt-ui-before-after.sh"); //$NON-NLS-1$
		for (String marker : List.of(
				PROJECT,
				"org.eclipse.jdt.bcoview", //$NON-NLS-1$
				"REACTOR_PROJECTS", //$NON-NLS-1$
				"verify_reactor_bcoview_runtime", //$NON-NLS-1$
				"jdt-ui-junit4-corpus.json", //$NON-NLS-1$
				"JdtUiCorpusEvidenceExecutionTest", //$NON-NLS-1$
				"compare_test_inventory.py", //$NON-NLS-1$
				"strict|best-effort")) { //$NON-NLS-1$
			assertTrue(runner.contains(marker), () -> "JDT UI runner is missing contract marker " + marker); //$NON-NLS-1$
		}
		assertFalse(runner.contains("verify_jdt_ui_corpus.py"), //$NON-NLS-1$
				"The real-corpus runner must use the Maven/JUnit verifier"); //$NON-NLS-1$

		String workflow = read(root, ".github/workflows/jdt-ui-junit4-strict-qa.yml"); //$NON-NLS-1$
		assertTrue(workflow.contains("'sandbox_common_test/**'"), //$NON-NLS-1$
				"Changes to the Maven/JUnit evidence authority must trigger the real-corpus workflow"); //$NON-NLS-1$
		assertFalse(workflow.contains("verify_jdt_ui_contract.py"), //$NON-NLS-1$
				"The workflow must not own a separate Python contract gate"); //$NON-NLS-1$

		assertFalse(Files.exists(root.resolve("qa/upstream-jdt/verify_jdt_ui_contract.py"))); //$NON-NLS-1$
		assertFalse(Files.exists(root.resolve("qa/upstream-jdt/verify_jdt_ui_corpus.py"))); //$NON-NLS-1$
		String allowlist = read(root, ".github/repository-policy/python-files.allowlist"); //$NON-NLS-1$
		assertFalse(allowlist.contains("verify_jdt_ui_contract.py")); //$NON-NLS-1$
		assertFalse(allowlist.contains("verify_jdt_ui_corpus.py")); //$NON-NLS-1$
	}

	@Test
	public void verifierAcceptsStrictAndBestEffortEvidenceAndRejectsQuarantineDrift(
			@TempDir Path temporary) throws Exception {
		Path root = repositoryRoot();
		Path contractPath = root.resolve("qa/upstream-jdt/jdt-ui-junit4-corpus.json"); //$NON-NLS-1$
		JsonObject contract = JdtUiCorpusEvidenceVerifier.readObject(contractPath);

		Evidence strict = createEvidence(temporary.resolve("strict"), contract, //$NON-NLS-1$
				JdtUiCorpusEvidenceVerifier.Mode.STRICT);
		JsonObject strictResult = verify(contractPath, strict,
				JdtUiCorpusEvidenceVerifier.Mode.STRICT);
		assertEquals("PASS", strictResult.get("result").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
		boolean quarantined = false;
		for (JsonElement item : strictResult.getAsJsonArray("strictlyQuarantinedCorpusFiles")) { //$NON-NLS-1$
			quarantined |= PARAMETERIZED_FILE.equals(item.getAsString());
		}
		assertTrue(quarantined);

		Files.writeString(strict.repository().resolve(PARAMETERIZED_FILE),
				"// accidental strict partial migration\n", //$NON-NLS-1$
				StandardCharsets.UTF_8,
				java.nio.file.StandardOpenOption.APPEND);
		assertThrows(IllegalArgumentException.class,
				() -> verify(contractPath, strict, JdtUiCorpusEvidenceVerifier.Mode.STRICT));

		Evidence bestEffort = createEvidence(temporary.resolve("best-effort"), contract, //$NON-NLS-1$
				JdtUiCorpusEvidenceVerifier.Mode.BEST_EFFORT);
		JsonObject bestEffortResult = verify(contractPath, bestEffort,
				JdtUiCorpusEvidenceVerifier.Mode.BEST_EFFORT);
		assertEquals("PASS", bestEffortResult.get("result").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$

		JsonObject invalidApply = JdtUiCorpusEvidenceVerifier.readObject(bestEffort.applyReport());
		invalidApply.getAsJsonObject("planningDiagnostics") //$NON-NLS-1$
				.remove("manualCompletionRequired"); //$NON-NLS-1$
		writeJson(bestEffort.applyReport(), invalidApply);
		JsonObject invalidCheck = JdtUiCorpusEvidenceVerifier.readObject(bestEffort.checkReport());
		invalidCheck.getAsJsonObject("planningDiagnostics") //$NON-NLS-1$
				.remove("manualCompletionRequired"); //$NON-NLS-1$
		writeJson(bestEffort.checkReport(), invalidCheck);
		assertThrows(IllegalArgumentException.class,
				() -> verify(contractPath, bestEffort,
						JdtUiCorpusEvidenceVerifier.Mode.BEST_EFFORT));
	}

	private static JsonObject verify(Path contractPath, Evidence evidence,
			JdtUiCorpusEvidenceVerifier.Mode mode) throws IOException {
		return JdtUiCorpusEvidenceVerifier.verify(
				evidence.repository(),
				evidence.baseline(),
				contractPath,
				mode,
				evidence.changedFiles(),
				evidence.checkReport(),
				evidence.applyReport(),
				evidence.output());
	}

	private static Evidence createEvidence(Path root, JsonObject contract,
			JdtUiCorpusEvidenceVerifier.Mode mode) throws IOException {
		Path repository = root.resolve("repository"); //$NON-NLS-1$
		Path baseline = root.resolve("baseline"); //$NON-NLS-1$
		Path changedFiles = root.resolve("changed-files.txt"); //$NON-NLS-1$
		Path checkReport = root.resolve("check.json"); //$NON-NLS-1$
		Path applyReport = root.resolve("apply.json"); //$NON-NLS-1$
		Path output = root.resolve("result.json"); //$NON-NLS-1$
		Files.createDirectories(root);

		List<String> changed = new ArrayList<>();
		JsonObject required = contract.getAsJsonObject("requiredFiles"); //$NON-NLS-1$
		for (Map.Entry<String, JsonElement> entry : required.entrySet()) {
			String relative = entry.getKey();
			JsonObject rules = entry.getValue().getAsJsonObject();
			String baselineText = join(rules.getAsJsonArray("baselineMustContain")); //$NON-NLS-1$
			Path baselineFile = baseline.resolve(relative);
			Path currentFile = repository.resolve(relative);
			Files.createDirectories(baselineFile.getParent());
			Files.createDirectories(currentFile.getParent());
			Files.writeString(baselineFile, baselineText, StandardCharsets.UTF_8);

			boolean strictUnchanged = rules.has("strictUnchanged") //$NON-NLS-1$
					&& rules.get("strictUnchanged").getAsBoolean(); //$NON-NLS-1$
			if (mode == JdtUiCorpusEvidenceVerifier.Mode.STRICT && strictUnchanged) {
				Files.writeString(currentFile, baselineText, StandardCharsets.UTF_8);
			} else {
				StringBuilder migrated = new StringBuilder(
						join(rules.getAsJsonArray("migratedMustContain"))); //$NON-NLS-1$
				if (mode == JdtUiCorpusEvidenceVerifier.Mode.BEST_EFFORT) {
					migrated.append(join(rules.getAsJsonArray("bestEffortMustContain"))); //$NON-NLS-1$
				}
				Files.writeString(currentFile, migrated.toString(), StandardCharsets.UTF_8);
				changed.add(relative);
			}
		}
		changed.sort(String::compareTo);
		Files.writeString(changedFiles,
				String.join(System.lineSeparator(), changed) + System.lineSeparator(),
				StandardCharsets.UTF_8);

		writeReport(checkReport, "check", changed, mode); //$NON-NLS-1$
		writeReport(applyReport, "apply", changed, mode); //$NON-NLS-1$
		return new Evidence(repository, baseline, changedFiles, checkReport, applyReport, output);
	}

	private static void writeReport(Path path, String mode, List<String> changed,
			JdtUiCorpusEvidenceVerifier.Mode evidenceMode) throws IOException {
		JsonObject report = new JsonObject();
		report.addProperty("schemaVersion", "1"); //$NON-NLS-1$ //$NON-NLS-2$
		report.addProperty("tool", "sandbox-project-cleanup"); //$NON-NLS-1$ //$NON-NLS-2$
		report.addProperty("project", PROJECT); //$NON-NLS-1$
		report.addProperty("mode", mode); //$NON-NLS-1$
		report.addProperty("filesProcessed", 25); //$NON-NLS-1$
		report.addProperty("filesChanged", changed.size()); //$NON-NLS-1$
		JsonArray changedArray = new JsonArray();
		for (String relative : changed) {
			changedArray.add(relative.substring((PROJECT + "/").length())); //$NON-NLS-1$
		}
		report.add("changedFiles", changedArray); //$NON-NLS-1$

		if (evidenceMode == JdtUiCorpusEvidenceVerifier.Mode.BEST_EFFORT) {
			JsonObject diagnostics = new JsonObject();
			diagnostics.addProperty("bestEffort", true); //$NON-NLS-1$
			diagnostics.addProperty("manualCompletionRequired", true); //$NON-NLS-1$
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
		writeJson(path, report);
	}

	private static void assertProfile(Path path, boolean bestEffort) throws IOException {
		Map<String, String> profile = parseProperties(path);
		assertEquals("true", profile.get("cleanup.junitcleanup")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("false", profile.get("cleanup.junit3cleanup")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(Boolean.toString(bestEffort),
				profile.get("cleanup.junitcleanup_best_effort")); //$NON-NLS-1$
		assertEquals("true", profile.get("cleanup.junitcleanup_4_parameterized")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static Map<String, String> parseProperties(Path path) throws IOException {
		Map<String, String> result = new LinkedHashMap<>();
		int number = 0;
		for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
			number++;
			String line = raw.trim();
			if (line.isEmpty() || line.startsWith("#")) { //$NON-NLS-1$
				continue;
			}
			int lineNumber = number;
			int separator = line.indexOf('=');
			assertTrue(separator > 0 && separator < line.length() - 1,
					() -> path + ":" + lineNumber + ": expected key=value"); //$NON-NLS-1$ //$NON-NLS-2$
			String key = line.substring(0, separator);
			String previous = result.putIfAbsent(key, line.substring(separator + 1));
			assertTrue(previous == null,
					() -> path + ":" + lineNumber + ": duplicate property " + key); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return Map.copyOf(result);
	}

	private static String join(JsonArray values) {
		if (values == null || values.size() == 0) {
			return ""; //$NON-NLS-1$
		}
		StringBuilder result = new StringBuilder();
		for (JsonElement value : values) {
			result.append(value.getAsString()).append(System.lineSeparator());
		}
		return result.toString();
	}

	private static void writeJson(Path path, JsonObject object) throws IOException {
		Files.createDirectories(path.toAbsolutePath().getParent());
		Files.writeString(path, JSON.toJson(object) + System.lineSeparator(),
				StandardCharsets.UTF_8);
	}

	private static Path repositoryRoot() {
		Path candidate = Path.of("").toAbsolutePath().normalize(); //$NON-NLS-1$
		while (candidate != null) {
			if (Files.isRegularFile(candidate.resolve("pom.xml")) //$NON-NLS-1$
					&& Files.isDirectory(candidate.resolve("qa/upstream-jdt"))) { //$NON-NLS-1$
				return candidate;
			}
			candidate = candidate.getParent();
		}
		throw new IllegalStateException("Could not locate the Sandbox repository root"); //$NON-NLS-1$
	}

	private static String read(Path root, String relative) throws IOException {
		return Files.readString(root.resolve(relative), StandardCharsets.UTF_8);
	}

	private record Evidence(
			Path repository,
			Path baseline,
			Path changedFiles,
			Path checkReport,
			Path applyReport,
			Path output) {
	}
}
