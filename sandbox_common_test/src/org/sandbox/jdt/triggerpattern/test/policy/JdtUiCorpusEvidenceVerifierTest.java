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
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.google.gson.JsonObject;

/**
 * Tests the Maven/JUnit authority for pinned JDT UI corpus evidence.
 *
 * @since 1.3.5
 */
public class JdtUiCorpusEvidenceVerifierTest {

	private static final String REPOSITORY =
			"https://github.com/eclipse-jdt/eclipse.jdt.ui.git"; //$NON-NLS-1$
	private static final String REF = "R4_40"; //$NON-NLS-1$
	private static final String COMMIT = "c922f757b27b7e2b6215db383cec5f8aafd13227"; //$NON-NLS-1$
	private static final String PROJECT = "org.eclipse.jdt.ui.tests"; //$NON-NLS-1$
	private static final String QUARANTINED =
			"org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/ui/tests/quickfix/ConvertLoopOperationTest.java"; //$NON-NLS-1$

	private static final Set<String> EXPECTED_FILES = Set.of(
			"org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/ui/tests/core/rules/JUnitSourceSetup.java", //$NON-NLS-1$
			"org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/ui/tests/core/rules/LeakTestSetup.java", //$NON-NLS-1$
			"org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/ui/tests/search/FileAdapterTest.java", //$NON-NLS-1$
			"org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/ui/tests/search/SearchLeakTestWrapper.java", //$NON-NLS-1$
			QUARANTINED);

	@TempDir
	Path temporary;

	@Test
	public void checkedInContractAndProfilesMatchPinnedAuthority() throws Exception {
		Path root = repositoryRoot();
		JdtUiCorpusEvidenceVerifier.Contract contract =
				JdtUiCorpusEvidenceVerifier.readContract(contractPath(root));

		assertEquals(REPOSITORY, contract.repository());
		assertEquals(REF, contract.ref());
		assertEquals(COMMIT, contract.commit());
		assertEquals(PROJECT, contract.project());
		assertTrue(contract.minimumChangedJavaFiles() >= 4);
		assertEquals(EXPECTED_FILES, contract.requiredFiles().keySet());
		JdtUiCorpusEvidenceVerifier.FileRules difficult = contract.requiredFiles().get(QUARANTINED);
		assertTrue(difficult.strictUnchanged());
		assertEquals("PARAMETERIZED_FIELD_INJECTION", difficult.strictReasonCode()); //$NON-NLS-1$
		assertFalse(difficult.bestEffortMustContain().isEmpty());

		Map<String, String> strict = readProperties(
				root.resolve("qa/upstream-jdt/junit4-to-jupiter.properties")); //$NON-NLS-1$
		Map<String, String> bestEffort = readProperties(
				root.resolve("qa/upstream-jdt/junit4-to-jupiter-best-effort.properties")); //$NON-NLS-1$
		assertProfile(strict, "false"); //$NON-NLS-1$
		assertProfile(bestEffort, "true"); //$NON-NLS-1$
	}

	@Test
	public void verifiesStrictEvidenceAndPreservesParameterizedQuarantine() throws Exception {
		EvidenceFixture fixture = createFixture(JdtUiCorpusEvidenceVerifier.Mode.STRICT);
		JdtUiCorpusEvidenceVerifier.Result result =
				JdtUiCorpusEvidenceVerifier.verify(fixture.request());

		assertEquals(4, result.changedJavaFiles());
		assertEquals(List.of(QUARANTINED), result.strictlyQuarantinedCorpusFiles());
		assertEquals(List.of(), result.requiredReasonCodes());
		assertTrue(Files.readString(fixture.output(), StandardCharsets.UTF_8)
				.contains("\"result\": \"PASS\"")); //$NON-NLS-1$
	}

	@Test
	public void verifiesBestEffortEvidenceAndStructuredRemediation() throws Exception {
		EvidenceFixture fixture = createFixture(JdtUiCorpusEvidenceVerifier.Mode.BEST_EFFORT);
		JdtUiCorpusEvidenceVerifier.Result result =
				JdtUiCorpusEvidenceVerifier.verify(fixture.request());

		assertEquals(5, result.changedJavaFiles());
		assertEquals(List.of(), result.strictlyQuarantinedCorpusFiles());
		assertEquals(List.of("PARAMETERIZED_FIELD_INJECTION"), result.requiredReasonCodes()); //$NON-NLS-1$
	}

	@Test
	public void rejectsChangedStrictQuarantine() throws Exception {
		EvidenceFixture fixture = createFixture(JdtUiCorpusEvidenceVerifier.Mode.STRICT);
		fixture.changed().add(QUARANTINED);
		writeChangedFiles(fixture.changedFiles(), fixture.changed());
		writeReport(fixture.checkReport(), "check", fixture.changed(), false, true); //$NON-NLS-1$
		writeReport(fixture.applyReport(), "apply", fixture.changed(), false, true); //$NON-NLS-1$

		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> JdtUiCorpusEvidenceVerifier.verify(fixture.request()));
		assertTrue(failure.getMessage().contains("changed quarantined corpus file")); //$NON-NLS-1$
	}

	@Test
	public void rejectsCheckApplyDisagreement() throws Exception {
		EvidenceFixture fixture = createFixture(JdtUiCorpusEvidenceVerifier.Mode.STRICT);
		List<String> applyChanged = new ArrayList<>(fixture.changed());
		applyChanged.remove(applyChanged.size() - 1);
		writeReport(fixture.applyReport(), "apply", applyChanged, false, true); //$NON-NLS-1$

		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> JdtUiCorpusEvidenceVerifier.verify(fixture.request()));
		assertTrue(failure.getMessage().contains("different changed-file sets")); //$NON-NLS-1$
	}

	@Test
	public void rejectsBestEffortEvidenceWithoutReasonCode() throws Exception {
		EvidenceFixture fixture = createFixture(JdtUiCorpusEvidenceVerifier.Mode.BEST_EFFORT);
		writeReport(fixture.checkReport(), "check", fixture.changed(), true, false); //$NON-NLS-1$
		writeReport(fixture.applyReport(), "apply", fixture.changed(), true, false); //$NON-NLS-1$

		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> JdtUiCorpusEvidenceVerifier.verify(fixture.request()));
		assertTrue(failure.getMessage().contains("required reason code")); //$NON-NLS-1$
	}

	private EvidenceFixture createFixture(JdtUiCorpusEvidenceVerifier.Mode mode) throws Exception {
		Path root = repositoryRoot();
		Path fixtureRoot = temporary.resolve(mode.externalName());
		Path repository = fixtureRoot.resolve("repository"); //$NON-NLS-1$
		Path baseline = fixtureRoot.resolve("baseline"); //$NON-NLS-1$
		Path changedFiles = fixtureRoot.resolve("changed-files.txt"); //$NON-NLS-1$
		Path checkReport = fixtureRoot.resolve("check.json"); //$NON-NLS-1$
		Path applyReport = fixtureRoot.resolve("apply.json"); //$NON-NLS-1$
		Path output = fixtureRoot.resolve("result.json"); //$NON-NLS-1$
		JdtUiCorpusEvidenceVerifier.Contract contract =
				JdtUiCorpusEvidenceVerifier.readContract(contractPath(root));
		List<String> changed = populate(contract, repository, baseline, mode);
		writeChangedFiles(changedFiles, changed);
		boolean bestEffort = mode == JdtUiCorpusEvidenceVerifier.Mode.BEST_EFFORT;
		writeReport(checkReport, "check", changed, bestEffort, true); //$NON-NLS-1$
		writeReport(applyReport, "apply", changed, bestEffort, true); //$NON-NLS-1$
		JdtUiCorpusEvidenceVerifier.Request request = new JdtUiCorpusEvidenceVerifier.Request(
				repository, baseline, contractPath(root), mode, changedFiles, checkReport, applyReport, output);
		return new EvidenceFixture(request, changedFiles, checkReport, applyReport, output, changed);
	}

	private static List<String> populate(JdtUiCorpusEvidenceVerifier.Contract contract,
			Path repository, Path baseline, JdtUiCorpusEvidenceVerifier.Mode mode) throws IOException {
		List<String> changed = new ArrayList<>();
		for (Map.Entry<String, JdtUiCorpusEvidenceVerifier.FileRules> entry
				: contract.requiredFiles().entrySet()) {
			String relative = entry.getKey();
			JdtUiCorpusEvidenceVerifier.FileRules rules = entry.getValue();
			Path baselineSource = baseline.resolve(relative);
			Path currentSource = repository.resolve(relative);
			Files.createDirectories(baselineSource.getParent());
			Files.createDirectories(currentSource.getParent());
			String baselineText = String.join(System.lineSeparator(), rules.baselineMustContain())
					+ System.lineSeparator();
			Files.writeString(baselineSource, baselineText, StandardCharsets.UTF_8);

			String currentText;
			if (mode == JdtUiCorpusEvidenceVerifier.Mode.STRICT && rules.strictUnchanged()) {
				currentText = baselineText;
			} else {
				List<String> markers = new ArrayList<>(rules.migratedMustContain());
				if (mode == JdtUiCorpusEvidenceVerifier.Mode.BEST_EFFORT) {
					markers.addAll(rules.bestEffortMustContain());
				}
				currentText = String.join(System.lineSeparator(), markers) + System.lineSeparator();
				changed.add(relative);
			}
			Files.writeString(currentSource, currentText, StandardCharsets.UTF_8);
		}
		return changed;
	}

	private static void writeChangedFiles(Path path, List<String> changed) throws IOException {
		Files.createDirectories(path.getParent());
		Files.writeString(path, String.join(System.lineSeparator(), changed)
				+ System.lineSeparator(), StandardCharsets.UTF_8);
	}

	private static void writeReport(Path path, String mode, List<String> changed,
			boolean bestEffort, boolean includeReason) throws IOException {
		JsonObject report = new JsonObject();
		report.addProperty("schemaVersion", "1"); //$NON-NLS-1$ //$NON-NLS-2$
		report.addProperty("tool", "sandbox-project-cleanup"); //$NON-NLS-1$ //$NON-NLS-2$
		report.addProperty("project", PROJECT); //$NON-NLS-1$
		report.addProperty("mode", mode); //$NON-NLS-1$
		report.addProperty("filesProcessed", 25); //$NON-NLS-1$
		report.addProperty("filesChanged", changed.size()); //$NON-NLS-1$
		JsonArray changedFiles = new JsonArray();
		for (String relative : changed) {
			changedFiles.add(relative.substring((PROJECT + "/").length())); //$NON-NLS-1$
		}
		report.add("changedFiles", changedFiles); //$NON-NLS-1$
		if (bestEffort) {
			JsonObject diagnostics = new JsonObject();
			diagnostics.addProperty("bestEffort", true); //$NON-NLS-1$
			diagnostics.addProperty("manualCompletionRequired", true); //$NON-NLS-1$
			JsonArray gaps = new JsonArray();
			JsonObject gap = new JsonObject();
			gap.addProperty("candidateId", "parameterized:ConvertLoopOperationTest"); //$NON-NLS-1$ //$NON-NLS-2$
			if (includeReason) {
				gap.addProperty("reasonCode", "PARAMETERIZED_FIELD_INJECTION"); //$NON-NLS-1$ //$NON-NLS-2$
			}
			gap.addProperty("remediation", "Use explicit Jupiter method arguments"); //$NON-NLS-1$ //$NON-NLS-2$
			gaps.add(gap);
			diagnostics.add("gaps", gaps); //$NON-NLS-1$
			report.add("planningDiagnostics", diagnostics); //$NON-NLS-1$
		} else {
			report.add("planningDiagnostics", new JsonArray()); //$NON-NLS-1$
		}
		report.addProperty("errorCount", 0); //$NON-NLS-1$
		report.add("errors", new JsonArray()); //$NON-NLS-1$
		Files.createDirectories(path.getParent());
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		Files.writeString(path, gson.toJson(report) + System.lineSeparator(), StandardCharsets.UTF_8);
	}

	private static void assertProfile(Map<String, String> profile, String bestEffort) {
		assertEquals("true", profile.get("cleanup.junitcleanup")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("false", profile.get("cleanup.junit3cleanup")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(bestEffort, profile.get("cleanup.junitcleanup_best_effort")); //$NON-NLS-1$
		assertEquals("true", profile.get("cleanup.junitcleanup_4_parameterized")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static Map<String, String> readProperties(Path path) throws IOException {
		Map<String, String> result = new LinkedHashMap<>();
		for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
			String line = raw.trim();
			if (line.isEmpty() || line.startsWith("#")) { //$NON-NLS-1$
				continue;
			}
			int separator = line.indexOf('=');
			assertTrue(separator > 0, () -> "Expected key=value in " + path); //$NON-NLS-1$
			String previous = result.put(line.substring(0, separator), line.substring(separator + 1));
			assertNull(previous, () -> "Duplicate property in " + path); //$NON-NLS-1$
		}
		return result;
	}

	private static Path contractPath(Path root) {
		return root.resolve("qa/upstream-jdt/jdt-ui-junit4-corpus.json"); //$NON-NLS-1$
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

	private record EvidenceFixture(JdtUiCorpusEvidenceVerifier.Request request, Path changedFiles,
			Path checkReport, Path applyReport, Path output, List<String> changed) {
	}
}
